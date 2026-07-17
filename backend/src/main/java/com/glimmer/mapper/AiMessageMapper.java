package com.glimmer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.entity.AiMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI消息表 Mapper
 */
@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessage> {
}
