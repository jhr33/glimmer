package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 消息视图
 */
@Data
public class AiMessageVO {

    private Long id;
    private Long conversationId;
    /** 角色: user/ai */
    private String role;
    private String content;
    private LocalDateTime createdAt;
}
