package com.seckill.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductStockMapper {

    @Update("UPDATE product SET stock = stock - #{count} WHERE id = #{productId} AND stock >= #{count}")
    int reduceStock(@Param("productId") Long productId, @Param("count") Integer count);
}
