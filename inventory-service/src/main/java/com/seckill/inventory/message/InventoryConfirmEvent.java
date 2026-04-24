package com.seckill.inventory.message;

import lombok.Data;

@Data
public class InventoryConfirmEvent {

    private String orderNo;
}
