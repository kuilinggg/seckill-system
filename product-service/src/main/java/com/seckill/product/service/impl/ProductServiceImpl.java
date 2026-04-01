package com.seckill.product.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.seckill.product.entity.Product;
import com.seckill.product.mapper.ProductMapper;
import com.seckill.product.search.ProductDoc;
import com.seckill.product.search.ProductRepository;
import com.seckill.product.service.ProductService;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final String CACHE_KEY_PREFIX = "product:detail:";
    private static final String LOCK_KEY_PREFIX = "lock:product:detail:";
    private static final String NULL_MARKER = "__NULL__";

    private static final int BASE_CACHE_MINUTES = 30;
    private static final int AVALANCHE_RANDOM_MINUTES_MIN = 1;
    private static final int AVALANCHE_RANDOM_MINUTES_MAX = 5;

    private static final int NULL_CACHE_MINUTES_MIN = 2;
    private static final int NULL_CACHE_MINUTES_MAX = 5;

    private static final int LOCK_EXPIRE_SECONDS = 10;
    private static final int RETRY_TIMES = 6;
    private static final long RETRY_SLEEP_BASE_MILLIS = 60L;

    private final ProductMapper productMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ProductRepository productRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    @DS("slave_1")
    public Product getProductById(Long id) {
        if (id == null || id <= 0) {
            return null;
        }

        String cacheKey = CACHE_KEY_PREFIX + id;
        String lockKey = LOCK_KEY_PREFIX + id;

        // 第一步：先查缓存，绝大多数请求在这里直接返回，避免打到数据库。
        String cachedValue = stringRedisTemplate.opsForValue().get(cacheKey);
        if (NULL_MARKER.equals(cachedValue)) {
            // 【防穿透】命中空值标记，说明数据库里本来就没有这个商品，直接返回 null。
            return null;
        }
        if (StringUtils.hasText(cachedValue)) {
            return deserializeProduct(cachedValue);
        }

        // 第二步：缓存未命中，尝试获取分布式锁，控制只有一个线程去重建缓存。
        String lockToken = String.valueOf(ThreadLocalRandom.current().nextLong());
        boolean locked = tryLock(lockKey, lockToken);
        if (locked) {
            try {
                // 双检缓存：避免拿到锁之前，已有其它线程完成缓存回填。
                String secondCheck = stringRedisTemplate.opsForValue().get(cacheKey);
                if (NULL_MARKER.equals(secondCheck)) {
                    return null;
                }
                if (StringUtils.hasText(secondCheck)) {
                    return deserializeProduct(secondCheck);
                }

                Product product = productMapper.selectById(id);
                if (product == null) {
                    // 【防穿透】数据库不存在的数据写入空值标记，且 TTL 用短随机时间（2-5 分钟）。
                    int nullTtlMinutes = randomBetween(NULL_CACHE_MINUTES_MIN, NULL_CACHE_MINUTES_MAX);
                    stringRedisTemplate.opsForValue().set(cacheKey, NULL_MARKER, Duration.ofMinutes(nullTtlMinutes));
                    return null;
                }

                // 【防雪崩】正常商品缓存使用“基础 TTL + 随机波动 TTL”，避免同一时刻集体过期。
                int randomTtlMinutes = randomBetween(AVALANCHE_RANDOM_MINUTES_MIN, AVALANCHE_RANDOM_MINUTES_MAX);
                int finalTtlMinutes = BASE_CACHE_MINUTES + randomTtlMinutes;
                stringRedisTemplate.opsForValue().set(cacheKey, serializeProduct(product), Duration.ofMinutes(finalTtlMinutes));
                return product;
            } finally {
                releaseLock(lockKey, lockToken);
            }
        }

        // 第三步：未拿到锁时，不直接查库，短暂等待后重试查缓存。
        // 【防击穿】高并发场景下仅允许一个线程查库，其他线程等待缓存重建结果。
        for (int i = 0; i < RETRY_TIMES; i++) {
            sleepQuietly(RETRY_SLEEP_BASE_MILLIS + ThreadLocalRandom.current().nextLong(40L));
            String retryValue = stringRedisTemplate.opsForValue().get(cacheKey);
            if (NULL_MARKER.equals(retryValue)) {
                return null;
            }
            if (StringUtils.hasText(retryValue)) {
                return deserializeProduct(retryValue);
            }
        }

        // 兜底降级：缓存重建未完成时返回 null，由上层决定提示信息。
        log.warn("缓存重建等待超时，productId={}", id);
        return null;
    }

    @Override
    @DS("master")
    public boolean reduceStock(Long id, Integer count) {
        if (id == null || id <= 0 || count == null || count <= 0) {
            return false;
        }

        int rows = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .setSql("stock = stock - " + count)
                .eq(Product::getId, id)
                .ge(Product::getStock, count));

        if (rows > 0) {
            // 扣减成功后主动删除缓存，确保后续读请求重新加载最新库存。
            stringRedisTemplate.delete(CACHE_KEY_PREFIX + id);
            return true;
        }
        return false;
    }

    @Override
    @DS("slave_1")
    public boolean syncProductToEs(Long id) {
        if (id == null || id <= 0) {
            return false;
        }

        Product product = productMapper.selectById(id);
        if (product == null) {
            return false;
        }

        ProductDoc doc = new ProductDoc();
        doc.setId(product.getId());
        doc.setTitle(product.getTitle());
        doc.setPrice(product.getPrice());
        doc.setStock(product.getStock());
        productRepository.save(doc);
        return true;
    }

    @Override
    public List<ProductDoc> searchProducts(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.match(m -> m.field("title").query(keyword)))
                .build();

        return elasticsearchOperations.search(query, ProductDoc.class)
                .stream()
                .map(SearchHit::getContent)
                .toList();
    }

    private boolean tryLock(String lockKey, String lockToken) {
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockToken, Duration.ofSeconds(LOCK_EXPIRE_SECONDS));
        return Boolean.TRUE.equals(locked);
    }

    private void releaseLock(String lockKey, String lockToken) {
        String lua = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(lua, Long.class);
        stringRedisTemplate.execute(script, java.util.Collections.singletonList(lockKey), lockToken);
    }

    private int randomBetween(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private String serializeProduct(Product product) {
        try {
            return objectMapper.writeValueAsString(product);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("商品缓存序列化失败", e);
        }
    }

    private Product deserializeProduct(String json) {
        try {
            return objectMapper.readValue(json, Product.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("商品缓存反序列化失败", e);
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
