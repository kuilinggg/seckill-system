package com.seckill.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_inventory_reservation")
public class InventoryReservation {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String orderNo;

    private Long productId;

    private Integer reserveCount;

    private Integer status;

    private String reason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
