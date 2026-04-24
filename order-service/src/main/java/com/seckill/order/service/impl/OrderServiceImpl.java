package com.seckill.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.order.entity.Order;
import com.seckill.order.message.OrderCreatedEvent;
import com.seckill.order.message.PaymentResultEvent;
import com.seckill.order.mapper.OrderMapper;
import com.seckill.order.service.OrderService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final String ORDER_IDEMPOTENT_KEY_PREFIX = "seckill:order:";
    private static final String PRODUCT_STOCK_KEY_PREFIX = "seckill:stock:";
    private static final String TOPIC_ORDER_CREATED = "order-created";
    private static final String TOPIC_PAYMENT_SUCCESS = "payment-success";
    private static final String TOPIC_PAYMENT_FAILED = "payment-failed";

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

        String orderNo = "ORD" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        try {
            Order order = new Order();
            order.setOrderNo(orderNo);
            order.setUserId(userId);
            order.setProductId(productId);
            order.setCount(1);
            order.setStatus(0);
            order.setPaymentStatus("PENDING");
            order.setCreateTime(LocalDateTime.now());
            order.setUpdateTime(LocalDateTime.now());
            orderMapper.insert(order);

            OrderCreatedEvent event = new OrderCreatedEvent();
            event.setOrderNo(orderNo);
            event.setUserId(userId);
            event.setProductId(productId);
            event.setCount(1);
            String messageJson = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(TOPIC_ORDER_CREATED, orderNo, messageJson);
            return "下单成功，订单号：" + orderNo + "，正在等待库存确认";
        } catch (Exception ex) {
            // 发送失败时回滚预扣库存和幂等键，避免数据错乱。
            stringRedisTemplate.opsForValue().increment(stockKey);
            stringRedisTemplate.delete(idemKey);
            orderMapper.delete(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
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

    @Override
    public String handlePaymentResult(String orderNo, Boolean success) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException("订单号不能为空");
        }
        if (success == null) {
            throw new IllegalArgumentException("支付结果不能为空");
        }

        try {
            PaymentResultEvent event = new PaymentResultEvent();
            event.setOrderNo(orderNo);
            event.setSuccess(success);
            String messageJson = objectMapper.writeValueAsString(event);

            if (success) {
                kafkaTemplate.send(TOPIC_PAYMENT_SUCCESS, orderNo, messageJson);
                return "支付成功事件已发送";
            }

            kafkaTemplate.send(TOPIC_PAYMENT_FAILED, orderNo, messageJson);
            return "支付失败事件已发送";
        } catch (Exception ex) {
            throw new IllegalStateException("支付事件发送失败");
        }
    }
}
