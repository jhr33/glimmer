package com.glimmer.service.dto;

import lombok.Data;

import java.util.List;

/**
 * AI 会话详情（含全部消息）
 */
@Data
public class ConversationDetailVO {

    private AiConversationVO conversation;
    private List<AiMessageVO> messages;
}
