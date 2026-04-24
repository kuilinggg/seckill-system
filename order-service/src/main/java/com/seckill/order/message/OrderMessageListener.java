package com.seckill.order.message;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.order.entity.Order;
import com.seckill.order.mapper.OrderMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageListener {

    private static final String TOPIC_INVENTORY_CONFIRM_SELL = "inventory-confirm-sell";
    private static final String TOPIC_ORDER_TIMEOUT_CANCEL = "order-timeout-cancel";
    private static final String ORDER_IDEMPOTENT_KEY_PREFIX = "seckill:order:";
    private static final String PRODUCT_STOCK_KEY_PREFIX = "seckill:stock:";

    private final ObjectMapper objectMapper;
    private final OrderMapper orderMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    @KafkaListener(topics = "inventory-events", groupId = "order-service-group")
    public void onInventoryResult(String messageJson) {
        try {
            InventoryResultEvent event = objectMapper.readValue(messageJson, InventoryResultEvent.class);
            if (Boolean.TRUE.equals(event.getSuccess())) {
                orderMapper.update(
                        null,
                        new LambdaUpdateWrapper<Order>()
                                .set(Order::getStatus, 1)
                                .set(Order::getUpdateTime, LocalDateTime.now())
                                .eq(Order::getOrderNo, event.getOrderNo())
                                .eq(Order::getStatus, 0));
                log.info("库存冻结成功，订单进入待支付状态 orderNo={}", event.getOrderNo());
                return;
            }

            Order order = orderMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                    .eq(Order::getOrderNo, event.getOrderNo()));
            if (order != null) {
                orderMapper.update(
                        null,
                        new LambdaUpdateWrapper<Order>()
                                .set(Order::getStatus, 2)
                                .set(Order::getPaymentStatus, "FAILED")
                                .set(Order::getUpdateTime, LocalDateTime.now())
                                .eq(Order::getOrderNo, event.getOrderNo())
                                .in(Order::getStatus, 0, 1));
                rollbackRedisMarks(order.getUserId(), order.getProductId(), order.getCount());
            }
            log.warn("库存冻结失败，订单关闭 orderNo={}, reason={}", event.getOrderNo(), event.getReason());
        } catch (Exception ex) {
            log.error("消费库存结果消息失败，message={}", messageJson, ex);
        }
    }

    @KafkaListener(topics = "payment-success", groupId = "order-service-group")
    public void onPaymentSuccess(String messageJson) {
        try {
            PaymentResultEvent event = objectMapper.readValue(messageJson, PaymentResultEvent.class);
            Order order = orderMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                    .eq(Order::getOrderNo, event.getOrderNo()));
            if (order == null) {
                log.warn("支付成功消息对应订单不存在 orderNo={}", event.getOrderNo());
                return;
            }
            if ("PAID".equals(order.getPaymentStatus())) {
                return;
            }

            orderMapper.update(
                    null,
                    new LambdaUpdateWrapper<Order>()
                            .set(Order::getStatus, 3)
                            .set(Order::getPaymentStatus, "PAID")
                            .set(Order::getUpdateTime, LocalDateTime.now())
                            .eq(Order::getOrderNo, event.getOrderNo())
                            .in(Order::getStatus, 1, 0));

            InventoryConfirmEvent confirmEvent = new InventoryConfirmEvent();
            confirmEvent.setOrderNo(event.getOrderNo());
            kafkaTemplate.send(TOPIC_INVENTORY_CONFIRM_SELL, event.getOrderNo(), objectMapper.writeValueAsString(confirmEvent));
            log.info("支付成功，已发送库存确认事件 orderNo={}", event.getOrderNo());
        } catch (Exception ex) {
            log.error("消费支付成功消息失败，message={}", messageJson, ex);
        }
    }

    @KafkaListener(topics = "payment-failed", groupId = "order-service-group")
    public void onPaymentFailed(String messageJson) {
        try {
            PaymentResultEvent event = objectMapper.readValue(messageJson, PaymentResultEvent.class);
            Order order = orderMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                    .eq(Order::getOrderNo, event.getOrderNo()));
            if (order == null) {
                log.warn("支付失败消息对应订单不存在 orderNo={}", event.getOrderNo());
                return;
            }

            orderMapper.update(
                    null,
                    new LambdaUpdateWrapper<Order>()
                            .set(Order::getStatus, 4)
                            .set(Order::getPaymentStatus, "FAILED")
                            .set(Order::getUpdateTime, LocalDateTime.now())
                            .eq(Order::getOrderNo, event.getOrderNo())
                            .in(Order::getStatus, 0, 1));

            InventoryReleaseEvent releaseEvent = new InventoryReleaseEvent();
            releaseEvent.setOrderNo(event.getOrderNo());
            releaseEvent.setReason("payment_failed");
            kafkaTemplate.send(TOPIC_ORDER_TIMEOUT_CANCEL, event.getOrderNo(), objectMapper.writeValueAsString(releaseEvent));

            rollbackRedisMarks(order.getUserId(), order.getProductId(), order.getCount());
            log.info("支付失败，已关闭订单并发送库存释放事件 orderNo={}", event.getOrderNo());
        } catch (Exception ex) {
            log.error("消费支付失败消息失败，message={}", messageJson, ex);
        }
    }

    private void rollbackRedisMarks(Long userId, Long productId, Integer count) {
        if (productId != null && count != null && count > 0) {
            stringRedisTemplate.opsForValue().increment(PRODUCT_STOCK_KEY_PREFIX + productId, count);
        }
        if (userId != null && productId != null) {
            stringRedisTemplate.delete(ORDER_IDEMPOTENT_KEY_PREFIX + userId + ":" + productId);
        }
    }
}
