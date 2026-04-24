package com.seckill.order.message;

import lombok.Data;

@Data
public class PaymentResultEvent {

    private String orderNo;

    private Boolean success;
}
