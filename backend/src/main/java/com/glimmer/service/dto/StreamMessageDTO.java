package com.glimmer.service.dto;

import lombok.Data;

/**
 * AI 流式消息 DTO
 * 用于 SSE 流式传输 AI 回复内容
 */
@Data
public class StreamMessageDTO {

    /**
     * 消息类型
     * - delta: AI 回复增量内容
     * - final: 回复结束，包含完整消息和会话状态
     * - error: 错误信息
     */
    private String type;

    /**
     * AI 回复增量内容（type=delta 时有效）
     */
    private String delta;

    /**
     * AI 完整消息（type=final 时有效）
     */
    private AiMessageVO aiMessage;

    /**
     * 用户消息（type=final 时有效）
     */
    private AiMessageVO userMessage;

    /**
     * 会话状态（type=final 时有效）
     */
    private String conversationStatus;

    /**
     * 当前消息数（type=final 时有效）
     */
    private Integer messageCount;

    /**
     * 最大消息数（type=final 时有效）
     */
    private Integer maxMessages;

    /** 当前已用轮次（type=final 时有效） */
    private Integer quotaUsed;

    /** 当前轮次上限（type=final 时有效） */
    private Integer quotaLimit;

    /** 本轮后额度是否已耗尽（type=final 时有效，前端据此弹解锁窗） */
    private Boolean quotaExhausted;

    /**
     * 错误信息（type=error 时有效）
     */
    private String error;

    /**
     * 创建增量消息
     */
    public static StreamMessageDTO delta(String delta) {
        StreamMessageDTO dto = new StreamMessageDTO();
        dto.setType("delta");
        dto.setDelta(delta);
        return dto;
    }

    /**
     * 创建结束消息
     */
    public static StreamMessageDTO finalMessage(AiMessageVO aiMessage, AiMessageVO userMessage,
                                                 String conversationStatus, Integer messageCount, Integer maxMessages) {
        StreamMessageDTO dto = new StreamMessageDTO();
        dto.setType("final");
        dto.setAiMessage(aiMessage);
        dto.setUserMessage(userMessage);
        dto.setConversationStatus(conversationStatus);
        dto.setMessageCount(messageCount);
        dto.setMaxMessages(maxMessages);
        return dto;
    }

    /**
     * 创建结束消息（含配额信息，用于前端额度展示与解锁弹窗触发）
     */
    public static StreamMessageDTO finalMessage(AiMessageVO aiMessage, AiMessageVO userMessage,
                                                 String conversationStatus, Integer messageCount, Integer maxMessages,
                                                 Integer quotaUsed, Integer quotaLimit, Boolean quotaExhausted) {
        StreamMessageDTO dto = new StreamMessageDTO();
        dto.setType("final");
        dto.setAiMessage(aiMessage);
        dto.setUserMessage(userMessage);
        dto.setConversationStatus(conversationStatus);
        dto.setMessageCount(messageCount);
        dto.setMaxMessages(maxMessages);
        dto.setQuotaUsed(quotaUsed);
        dto.setQuotaLimit(quotaLimit);
        dto.setQuotaExhausted(quotaExhausted);
        return dto;
    }

    /**
     * 创建错误消息
     */
    public static StreamMessageDTO error(String error) {
        StreamMessageDTO dto = new StreamMessageDTO();
        dto.setType("error");
        dto.setError(error);
        return dto;
    }
}
