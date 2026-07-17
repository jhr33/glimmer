package com.glimmer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知表 Mapper
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
