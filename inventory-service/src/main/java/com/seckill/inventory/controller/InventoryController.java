package com.seckill.inventory.controller;

import com.seckill.inventory.common.Result;
import com.seckill.inventory.service.InventoryService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/reserve")
    public Result<Boolean> reserve(@RequestBody ReserveRequest request) {
        boolean success = inventoryService.reserveForOrder(request.getOrderNo(), request.getProductId(), request.getCount());
        return Result.success(success);
    }

    @PostMapping("/confirm/{orderNo}")
    public Result<Boolean> confirm(@PathVariable("orderNo") String orderNo) {
        return Result.success(inventoryService.confirmSell(orderNo));
    }

    @PostMapping("/release/{orderNo}")
    public Result<Boolean> release(@PathVariable("orderNo") String orderNo, @RequestBody(required = false) ReleaseRequest request) {
        String reason = request == null ? "manual_release" : request.getReason();
        return Result.success(inventoryService.release(orderNo, reason));
    }

    @Data
    public static class ReserveRequest {
        private String orderNo;
        private Long productId;
        private Integer count;
    }

    @Data
    public static class ReleaseRequest {
        private String reason;
    }
}
