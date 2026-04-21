package com.seckill.order.message;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.order.entity.Order;
import com.seckill.order.mapper.OrderMapper;
import com.seckill.order.mapper.ProductStockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageListener {

    private final ObjectMapper objectMapper;
    private final OrderMapper orderMapper;
    private final ProductStockMapper productStockMapper;

    @KafkaListener(topics = "seckill-orders", groupId = "order-service-group")
    public void onMessage(String messageJson) {
        try {
            SeckillOrderMessage message = objectMapper.readValue(messageJson, SeckillOrderMessage.class);

            // 防止消息重复消费导致重复订单。
            Long existing = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                    .eq(Order::getUserId, message.getUserId())
                    .eq(Order::getProductId, message.getProductId()));
            if (existing != null && existing > 0) {
                log.info("重复消息已忽略 userId={}, productId={}", message.getUserId(), message.getProductId());
                return;
            }

            // 【作业要求-落库阶段】这里执行真实库存扣减（数据库层）。
            int stockRows = productStockMapper.reduceStock(message.getProductId(), 1);
            if (stockRows <= 0) {
                log.warn("数据库库存扣减失败，可能已售罄 productId={}", message.getProductId());
                return;
            }

            // 【作业要求-落库阶段】插入订单记录，状态 1 表示已创建。
            Order order = new Order();
            order.setUserId(message.getUserId());
            order.setProductId(message.getProductId());
            order.setStatus(1);
            orderMapper.insert(order);

            log.info("秒杀订单落库成功，orderId={}, userId={}, productId={}", order.getId(), order.getUserId(), order.getProductId());
        } catch (Exception ex) {
            log.error("消费秒杀订单消息失败，message={}", messageJson, ex);
        }
    }
}
