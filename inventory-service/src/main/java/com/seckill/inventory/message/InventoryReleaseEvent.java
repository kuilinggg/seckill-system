package com.seckill.inventory.message;

import lombok.Data;

@Data
public class InventoryReleaseEvent {

    private String orderNo;

    private String reason;
}
