package com.seckill.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.order.entity.Order;
import com.seckill.order.message.SeckillOrderMessage;
import com.seckill.order.mapper.OrderMapper;
import com.seckill.order.service.OrderService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final String ORDER_IDEMPOTENT_KEY_PREFIX = "seckill:order:";
    private static final String PRODUCT_STOCK_KEY_PREFIX = "seckill:stock:";
    private static final String TOPIC_SECKILL_ORDERS = "seckill-orders";

    private final StringRedisTemplate stringRedisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final OrderMapper orderMapper;

    @Override
    public String seckill(Long userId, Long productId) {
        if (userId == null || userId <= 0 || productId == null || productId <= 0) {
            throw new IllegalArgumentException("用户ID或商品ID不合法");
        }

        String idemKey = ORDER_IDEMPOTENT_KEY_PREFIX + userId + ":" + productId;

        // 【作业要求-幂等性控制】使用 setIfAbsent 防止同一用户重复抢同一商品。
        Boolean firstOrder = stringRedisTemplate.opsForValue().setIfAbsent(idemKey, "1", Duration.ofHours(2));
        if (!Boolean.TRUE.equals(firstOrder)) {
            throw new IllegalStateException("您已下过该商品订单，请勿重复下单");
        }

        String stockKey = PRODUCT_STOCK_KEY_PREFIX + productId;

        // 【作业要求-防超卖控制】使用 Redis 原子递减做库存预扣减。
        Long remainStock = stringRedisTemplate.opsForValue().decrement(stockKey);
        if (remainStock == null) {
            stringRedisTemplate.delete(idemKey);
            throw new IllegalStateException("库存预热未完成，请稍后重试");
        }
        if (remainStock < 0) {
            stringRedisTemplate.opsForValue().increment(stockKey);
            stringRedisTemplate.delete(idemKey);
            throw new IllegalStateException("商品已售罄");
        }

        try {
            SeckillOrderMessage message = new SeckillOrderMessage();
            message.setUserId(userId);
            message.setProductId(productId);
            String messageJson = objectMapper.writeValueAsString(message);

            // 【作业要求-Kafka 异步发送】把下单消息发送到 seckill-orders 主题削峰。
            kafkaTemplate.send(TOPIC_SECKILL_ORDERS, String.valueOf(userId), messageJson);
            return "正在排队处理中";
        } catch (Exception ex) {
            // 发送失败时回滚预扣库存和幂等键，避免数据错乱。
            stringRedisTemplate.opsForValue().increment(stockKey);
            stringRedisTemplate.delete(idemKey);
            throw new IllegalStateException("下单请求入队失败，请稍后重试");
        }
    }

    @Override
    public Order getById(Long id) {
        if (id == null || id <= 0) {
            return null;
        }
        return orderMapper.selectById(id);
    }
}
