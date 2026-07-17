package com.glimmer.service;

import com.glimmer.common.response.PageResult;
import com.glimmer.service.dto.AiConversationVO;
import com.glimmer.service.dto.ConversationDetailVO;
import com.glimmer.service.dto.SendMessageResponse;

/**
 * AI 对话服务接口
 * 见开发文档 §2.6 / §4.9
 */
public interface AiConversationService {

    /**
     * 开启新会话（消耗1代币）
     */
    AiConversationVO startConversation(Long userId);

    /**
     * 我的会话列表（分页）
     */
    PageResult<AiConversationVO> getConversationList(Long userId, int page, int size);

    /**
     * 会话详情（含所有消息）
     */
    ConversationDetailVO getConversationDetail(Long userId, Long conversationId);

    /**
     * 发送消息（同步返回 AI 回复）
     */
    SendMessageResponse sendMessage(Long userId, Long conversationId, String content);

    /**
     * 主动关闭会话
     */
    void closeConversation(Long userId, Long conversationId);
}
