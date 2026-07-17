package com.glimmer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.entity.CampfireMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 篝火消息表 Mapper
 */
@Mapper
public interface CampfireMessageMapper extends BaseMapper<CampfireMessage> {
}
