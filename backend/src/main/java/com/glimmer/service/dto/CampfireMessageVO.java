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

    /** 发送者头像快照（null = 系统默认头像，前端按 userId 取默认头像） */
    private String avatarUrl;

    private String content;

    /** 消息类型: text文本/image图片 */
    private String msgType;

    /** 图片消息URL（msgType=image 时使用） */
    private String imageUrl;

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
