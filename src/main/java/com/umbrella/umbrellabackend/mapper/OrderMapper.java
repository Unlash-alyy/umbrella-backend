package com.umbrella.umbrellabackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.umbrella.umbrellabackend.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Select("SELECT * FROM `借还记录表` WHERE `校园卡号`=#{cardId} AND `记录状态`=0 LIMIT 1")
    Order findUnreturned(String cardId);
}