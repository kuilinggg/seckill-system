package com.seckill.order.message;

import lombok.Data;

@Data
public class OrderCreatedEvent {

    private String orderNo;

    private Long userId;

    private Long productId;

    private Integer count;
}
