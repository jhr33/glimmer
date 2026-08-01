package com.glimmer.service.dto;

import lombok.Data;

/**
 * AI 发送消息响应（同步返回用户消息 + AI 回复）
 */
@Data
public class SendMessageResponse {

    private AiMessageVO userMessage;
    private AiMessageVO aiMessage;
    private String conversationStatus;
    private Integer messageCount;
    private Integer maxMessages;

    /** 当前已用轮次 */
    private Integer quotaUsed;

    /** 当前轮次上限 */
    private Integer quotaLimit;

    /** 本轮后额度是否已耗尽（前端据此弹解锁窗） */
    private Boolean quotaExhausted;
}
