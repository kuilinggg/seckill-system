package com.seckill.order.service;

import com.seckill.order.entity.Order;

public interface OrderService {

    String seckill(Long userId, Long productId);

    Order getById(Long id);
}
