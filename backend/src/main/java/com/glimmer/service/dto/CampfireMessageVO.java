package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 篝火消息视图
 */
@Data
public class CampfireMessageVO {

    private Long id;
    private Long campfireId;
    private Long userId;
    private String anonymousName;
    private String content;
    private LocalDateTime createdAt;

    /**
     * 是否为 AI 机器人（回音）发送的消息
     * true 时前端不显示举报按钮
     */
    private Boolean isFromBot;

    /** 被引用的消息ID */
    private Long quotedMessageId;

    /** 被引用的消息内容 */
    private String quotedContent;

    /** 被引用消息的发送者昵称 */
    private String quotedAnonymousName;
}
