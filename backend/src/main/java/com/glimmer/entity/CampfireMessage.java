package com.glimmer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 篝火消息表（campfire_message）
 */
@Data
@TableName("campfire_message")
public class CampfireMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long campfireId;

    private Long userId;

    private String anonymousName;

    private String content;

    private LocalDateTime createdAt;
}
