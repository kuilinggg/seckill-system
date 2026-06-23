package com.seckill.product.service.impl;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.json.JsonData;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.product.entity.Product;
import com.seckill.product.mapper.ProductMapper;
import com.seckill.product.search.ProductDoc;
import com.seckill.product.search.ProductRepository;
import com.seckill.product.search.ProductSearchResult;
import com.seckill.product.service.ProductService;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
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
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

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
        String cachedValue = stringRedisTemplate.opsForValue().get(cacheKey);
        if (NULL_MARKER.equals(cachedValue)) {
            return null;
        }
        if (StringUtils.hasText(cachedValue)) {
            return deserializeProduct(cachedValue);
        }

        String lockToken = String.valueOf(ThreadLocalRandom.current().nextLong());
        boolean locked = tryLock(lockKey, lockToken);
        if (locked) {
            try {
                String secondCheck = stringRedisTemplate.opsForValue().get(cacheKey);
                if (NULL_MARKER.equals(secondCheck)) {
                    return null;
                }
                if (StringUtils.hasText(secondCheck)) {
                    return deserializeProduct(secondCheck);
                }

                Product product = productMapper.selectById(id);
                if (product == null) {
                    int nullTtlMinutes = randomBetween(NULL_CACHE_MINUTES_MIN, NULL_CACHE_MINUTES_MAX);
                    stringRedisTemplate.opsForValue().set(cacheKey, NULL_MARKER, Duration.ofMinutes(nullTtlMinutes));
                    return null;
                }

                int randomTtlMinutes = randomBetween(AVALANCHE_RANDOM_MINUTES_MIN, AVALANCHE_RANDOM_MINUTES_MAX);
                int finalTtlMinutes = BASE_CACHE_MINUTES + randomTtlMinutes;
                stringRedisTemplate.opsForValue().set(cacheKey, serializeProduct(product), Duration.ofMinutes(finalTtlMinutes));
                return product;
            } finally {
                releaseLock(lockKey, lockToken);
            }
        }

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

        log.warn("cache rebuild timeout, productId={}", id);
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
            stringRedisTemplate.delete(CACHE_KEY_PREFIX + id);
            try {
                syncProductToEs(id);
            } catch (Exception ex) {
                log.warn("sync product to Elasticsearch failed after stock reduce, productId={}", id, ex);
            }
            return true;
        }
        return false;
    }

    @Override
    @DS("master")
    public boolean syncProductToEs(Long id) {
        if (id == null || id <= 0) {
            return false;
        }

        Product product = productMapper.selectById(id);
        if (product == null) {
            return false;
        }

        productRepository.save(toDoc(product));
        return true;
    }

    @Override
    public ProductSearchResult searchProducts(String keyword, Integer page, Integer size, Boolean inStockOnly, String sort) {
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        if (!StringUtils.hasText(keyword)) {
            return new ProductSearchResult(List.of(), 0, currentPage, pageSize);
        }

        NativeQueryBuilder queryBuilder = NativeQuery.builder()
                .withQuery(q -> q.match(m -> m.field("title").query(keyword)))
                .withPageable(org.springframework.data.domain.PageRequest.of(currentPage - 1, pageSize))
                .withTrackTotalHits(true)
                .withHighlightQuery(new HighlightQuery(new Highlight(
                        HighlightParameters.builder()
                                .withPreTags("<em>")
                                .withPostTags("</em>")
                                .build(),
                        List.of(new HighlightField("title"))), ProductDoc.class));

        if (Boolean.TRUE.equals(inStockOnly)) {
            queryBuilder.withFilter(q -> q.range(r -> r.field("stock").gt(JsonData.of(0))));
        }

        applySort(queryBuilder, sort);
        SearchHits<ProductDoc> searchHits = elasticsearchOperations.search(queryBuilder.build(), ProductDoc.class);
        List<ProductDoc> records = searchHits.stream()
                .map(this::withHighlightTitle)
                .toList();
        return new ProductSearchResult(records, searchHits.getTotalHits(), currentPage, pageSize);
    }

    private void applySort(NativeQueryBuilder queryBuilder, String sort) {
        if (!StringUtils.hasText(sort) || "relevance".equalsIgnoreCase(sort)) {
            return;
        }
        switch (sort.toLowerCase()) {
            case "price_asc" -> queryBuilder.withSort(s -> s.field(f -> f.field("price").order(SortOrder.Asc)));
            case "price_desc" -> queryBuilder.withSort(s -> s.field(f -> f.field("price").order(SortOrder.Desc)));
            case "stock_desc" -> queryBuilder.withSort(s -> s.field(f -> f.field("stock").order(SortOrder.Desc)));
            default -> log.warn("unsupported product search sort: {}", sort);
        }
    }

    private ProductDoc withHighlightTitle(SearchHit<ProductDoc> hit) {
        ProductDoc doc = hit.getContent();
        List<String> highlights = hit.getHighlightField("title");
        if (highlights != null && !highlights.isEmpty()) {
            doc.setHighlightTitle(highlights.get(0));
        } else {
            doc.setHighlightTitle(doc.getTitle());
        }
        return doc;
    }

    private ProductDoc toDoc(Product product) {
        ProductDoc doc = new ProductDoc();
        doc.setId(product.getId());
        doc.setTitle(product.getTitle());
        doc.setPrice(product.getPrice());
        doc.setStock(product.getStock());
        return doc;
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
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
