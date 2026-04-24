package com.seckill.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_inventory")
public class Inventory {

    @TableId
    private Long productId;

    private Integer availableStock;

    private Integer frozenStock;

    private LocalDateTime updateTime;
}
