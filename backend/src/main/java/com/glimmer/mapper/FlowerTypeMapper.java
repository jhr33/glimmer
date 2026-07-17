package com.glimmer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.entity.FlowerType;
import org.apache.ibatis.annotations.Mapper;

/**
 * 花种配置表 Mapper
 */
@Mapper
public interface FlowerTypeMapper extends BaseMapper<FlowerType> {
}
