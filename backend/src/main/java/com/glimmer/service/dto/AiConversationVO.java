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

    /** 类型: free免费(每日重置)/paid付费 */
    private String conversationType;

    /** 已用轮次（1轮 = 1用户消息 + 1AI回复） */
    private Integer quotaUsed;

    /** 轮次上限 */
    private Integer quotaLimit;

    /** DeepSeek 生成的会话摘要（2-3句），仅详情接口填充 */
    private String summary;

    /** 会话标题（首条消息前 20 字，用于列表展示） */
    private String title;
}
