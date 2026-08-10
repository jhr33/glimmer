package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 漂流瓶回复视图
 */
@Data
public class BottleReplyVO {

    private Long id;
    private Long bottleId;
    private Long userId;

    /**
     * 回复者的匿名名称（user.anonymousName）
     */
    private String anonymousName;

    private String content;
    private LocalDateTime createdAt;

    /**
     * 是否为 AI 机器人（回音）回复
     * true 时前端不显示举报按钮
     */
    private Boolean isFromBot;
}
