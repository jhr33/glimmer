package com.glimmer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.entity.AiConversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI对话表 Mapper
 */
@Mapper
public interface AiConversationMapper extends BaseMapper<AiConversation> {
}
