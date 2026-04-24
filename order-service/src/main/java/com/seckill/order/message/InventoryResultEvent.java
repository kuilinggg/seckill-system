package com.seckill.order.message;

import lombok.Data;

@Data
public class InventoryResultEvent {

    private String orderNo;

    private Long userId;

    private Long productId;

    private Integer count;

    private Boolean success;

    private String reason;
}
