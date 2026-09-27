package com.umbrella.umbrellabackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.umbrella.umbrellabackend.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}