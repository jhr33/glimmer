package com.glimmer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.entity.Feedback;
import org.apache.ibatis.annotations.Mapper;

/**
 * 意见信/反馈表 Mapper
 */
@Mapper
public interface FeedbackMapper extends BaseMapper<Feedback> {
}
