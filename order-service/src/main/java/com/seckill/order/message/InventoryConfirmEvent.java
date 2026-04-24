package com.seckill.order.message;

import lombok.Data;

@Data
public class InventoryConfirmEvent {

    private String orderNo;
}
