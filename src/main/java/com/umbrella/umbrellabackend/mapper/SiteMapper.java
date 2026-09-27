package com.umbrella.umbrellabackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.umbrella.umbrellabackend.entity.Site;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SiteMapper extends BaseMapper<Site> {
}