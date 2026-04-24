package com.seckill.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.inventory.entity.InventoryReservation;
import com.seckill.inventory.mapper.InventoryMapper;
import com.seckill.inventory.mapper.InventoryReservationMapper;
import com.seckill.inventory.service.InventoryService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryMapper inventoryMapper;
    private final InventoryReservationMapper reservationMapper;

    @Override
    @Transactional
    public boolean reserveForOrder(String orderNo, Long productId, Integer count) {
        InventoryReservation existing = reservationMapper.selectOne(new LambdaQueryWrapper<InventoryReservation>()
                .eq(InventoryReservation::getOrderNo, orderNo));
        if (existing != null) {
            return Integer.valueOf(1).equals(existing.getStatus()) || Integer.valueOf(3).equals(existing.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        InventoryReservation reservation = new InventoryReservation();
        reservation.setOrderNo(orderNo);
        reservation.setProductId(productId);
        reservation.setReserveCount(count);
        reservation.setStatus(0);
        reservation.setCreateTime(now);
        reservation.setUpdateTime(now);
        reservationMapper.insert(reservation);

        int rows = inventoryMapper.reserve(productId, count);
        reservation.setUpdateTime(LocalDateTime.now());
        if (rows <= 0) {
            reservation.setStatus(2);
            reservation.setReason("not_enough_stock");
            reservationMapper.updateById(reservation);
            return false;
        }

        reservation.setStatus(1);
        reservationMapper.updateById(reservation);
        return true;
    }

    @Override
    @Transactional
    public boolean confirmSell(String orderNo) {
        InventoryReservation reservation = reservationMapper.selectOne(new LambdaQueryWrapper<InventoryReservation>()
                .eq(InventoryReservation::getOrderNo, orderNo));
        if (reservation == null) {
            return false;
        }
        if (Integer.valueOf(3).equals(reservation.getStatus())) {
            return true;
        }
        if (!Integer.valueOf(1).equals(reservation.getStatus())) {
            return false;
        }

        int rows = inventoryMapper.confirmSell(reservation.getProductId(), reservation.getReserveCount());
        if (rows <= 0) {
            return false;
        }

        reservation.setStatus(3);
        reservation.setUpdateTime(LocalDateTime.now());
        reservationMapper.updateById(reservation);
        return true;
    }

    @Override
    @Transactional
    public boolean release(String orderNo, String reason) {
        InventoryReservation reservation = reservationMapper.selectOne(new LambdaQueryWrapper<InventoryReservation>()
                .eq(InventoryReservation::getOrderNo, orderNo));
        if (reservation == null) {
            return false;
        }
        if (Integer.valueOf(4).equals(reservation.getStatus())) {
            return true;
        }
        if (!Integer.valueOf(1).equals(reservation.getStatus())) {
            return false;
        }

        int rows = inventoryMapper.release(reservation.getProductId(), reservation.getReserveCount());
        if (rows <= 0) {
            return false;
        }

        reservation.setStatus(4);
        reservation.setReason(reason);
        reservation.setUpdateTime(LocalDateTime.now());
        reservationMapper.updateById(reservation);
        return true;
    }
}
