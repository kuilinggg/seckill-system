package com.seckill.order.controller;

import com.seckill.order.common.Result;
import com.seckill.order.entity.Order;
import com.seckill.order.service.OrderService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/seckill")
    public Result<String> seckill(@RequestBody SeckillRequest request) {
        String result = orderService.seckill(request.getUserId(), request.getProductId());
        return Result.success(result);
    }

    @PostMapping("/payment/callback")
    public Result<String> paymentCallback(@RequestBody PaymentCallbackRequest request) {
        String result = orderService.handlePaymentResult(request.getOrderNo(), request.getSuccess());
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<Order> getById(@PathVariable("id") Long id) {
        Order order = orderService.getById(id);
        if (order == null) {
            return Result.error("订单不存在");
        }
        return Result.success(order);
    }

    @Data
    public static class SeckillRequest {
        private Long userId;
        private Long productId;
    }

    @Data
    public static class PaymentCallbackRequest {
        private String orderNo;
        private Boolean success;
    }
}
