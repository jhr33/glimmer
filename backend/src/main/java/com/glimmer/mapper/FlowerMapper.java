package com.glimmer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.entity.Flower;
import org.apache.ibatis.annotations.Mapper;

/**
 * 花朵表 Mapper
 */
@Mapper
public interface FlowerMapper extends BaseMapper<Flower> {
}
