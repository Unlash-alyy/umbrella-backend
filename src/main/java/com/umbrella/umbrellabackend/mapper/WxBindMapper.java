package com.umbrella.umbrellabackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.umbrella.umbrellabackend.entity.WxBind;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WxBindMapper extends BaseMapper<WxBind> {

    @Select("SELECT * FROM `微信绑定表` WHERE `微信openid`=#{openid} LIMIT 1")
    WxBind findByOpenid(String openid);
}