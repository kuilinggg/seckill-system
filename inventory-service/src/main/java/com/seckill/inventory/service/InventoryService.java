package com.seckill.inventory.service;

public interface InventoryService {

    boolean reserveForOrder(String orderNo, Long productId, Integer count);

    boolean confirmSell(String orderNo);

    boolean release(String orderNo, String reason);
}
