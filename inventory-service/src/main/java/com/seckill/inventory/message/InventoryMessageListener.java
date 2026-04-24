package com.seckill.inventory.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryMessageListener {

    private static final String TOPIC_INVENTORY_EVENTS = "inventory-events";

    private final ObjectMapper objectMapper;
    private final InventoryService inventoryService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @KafkaListener(topics = "order-created", groupId = "inventory-service-group")
    public void onOrderCreated(String messageJson) {
        try {
            OrderCreatedEvent event = objectMapper.readValue(messageJson, OrderCreatedEvent.class);
            boolean success = inventoryService.reserveForOrder(event.getOrderNo(), event.getProductId(), event.getCount());

            InventoryResultEvent result = new InventoryResultEvent();
            result.setOrderNo(event.getOrderNo());
            result.setUserId(event.getUserId());
            result.setProductId(event.getProductId());
            result.setCount(event.getCount());
            result.setSuccess(success);
            result.setReason(success ? "reserved" : "stock_not_enough");

            kafkaTemplate.send(TOPIC_INVENTORY_EVENTS, event.getOrderNo(), objectMapper.writeValueAsString(result));
        } catch (Exception ex) {
            log.error("handle order-created failed: {}", messageJson, ex);
        }
    }

    @KafkaListener(topics = "inventory-confirm-sell", groupId = "inventory-service-group")
    public void onInventoryConfirm(String messageJson) {
        try {
            InventoryConfirmEvent event = objectMapper.readValue(messageJson, InventoryConfirmEvent.class);
            boolean success = inventoryService.confirmSell(event.getOrderNo());
            if (!success) {
                log.warn("confirm sell failed for orderNo={}", event.getOrderNo());
            }
        } catch (Exception ex) {
            log.error("handle inventory-confirm-sell failed: {}", messageJson, ex);
        }
    }

    @KafkaListener(topics = "order-timeout-cancel", groupId = "inventory-service-group")
    public void onOrderCancel(String messageJson) {
        try {
            InventoryReleaseEvent event = objectMapper.readValue(messageJson, InventoryReleaseEvent.class);
            boolean success = inventoryService.release(event.getOrderNo(), event.getReason());
            if (!success) {
                log.warn("release inventory failed for orderNo={}", event.getOrderNo());
            }
        } catch (Exception ex) {
            log.error("handle order-timeout-cancel failed: {}", messageJson, ex);
        }
    }
}
