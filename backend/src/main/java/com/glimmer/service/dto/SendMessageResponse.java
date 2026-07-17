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
}
