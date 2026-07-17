package com.glimmer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.entity.Campfire;
import org.apache.ibatis.annotations.Mapper;

/**
 * 篝火表 Mapper
 */
@Mapper
public interface CampfireMapper extends BaseMapper<Campfire> {
}
