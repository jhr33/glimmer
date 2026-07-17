package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话视图
 */
@Data
public class AiConversationVO {

    private Long id;
    private String status;
    private Integer messageCount;
    private Integer maxMessages;
    private LocalDateTime startedAt;
    private LocalDateTime lastActiveAt;
}
