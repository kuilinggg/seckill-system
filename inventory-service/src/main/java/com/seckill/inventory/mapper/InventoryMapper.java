package com.seckill.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seckill.inventory.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {

    @Update("UPDATE t_inventory SET available_stock = available_stock - #{count}, frozen_stock = frozen_stock + #{count}, update_time = NOW() WHERE product_id = #{productId} AND available_stock >= #{count}")
    int reserve(@Param("productId") Long productId, @Param("count") Integer count);

    @Update("UPDATE t_inventory SET frozen_stock = frozen_stock - #{count}, update_time = NOW() WHERE product_id = #{productId} AND frozen_stock >= #{count}")
    int confirmSell(@Param("productId") Long productId, @Param("count") Integer count);

    @Update("UPDATE t_inventory SET available_stock = available_stock + #{count}, frozen_stock = frozen_stock - #{count}, update_time = NOW() WHERE product_id = #{productId} AND frozen_stock >= #{count}")
    int release(@Param("productId") Long productId, @Param("count") Integer count);
}
