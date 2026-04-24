package com.seckill.order.message;

import lombok.Data;

@Data
public class InventoryReleaseEvent {

    private String orderNo;

    private String reason;
}
