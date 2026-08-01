package com.glimmer.service;

import com.glimmer.common.response.PageResult;
import com.glimmer.service.dto.AiConversationVO;
import com.glimmer.service.dto.ConversationDetailVO;
import com.glimmer.service.dto.SendMessageResponse;
import com.glimmer.service.dto.UnlockQuotaResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 对话服务接口
 * 见开发文档 §2.6 / §4.9
 */
public interface AiConversationService {

    /**
     * 开启新会话（消耗1代币）
     * <p>
     * 重构后语义：始终创建付费会话（conversationType=paid）。
     * 免费会话由 {@link #getOrCreateFreeConversation} 在访问列表时自动创建。
     */
    AiConversationVO startConversation(Long userId);

    /**
     * 获取或创建用户的免费会话（每日额度 10 轮，00:00 懒重置）。
     * <p>
     * 每个用户至多存在一个 active 的 free 会话；访问 AI 页面时自动确保存在。
     * 不消耗代币。
     */
    AiConversationVO getOrCreateFreeConversation(Long userId);

    /**
     * 解锁对话额度（消耗 1 枚代币，额度上限 +10）。
     * <p>
     * 仅在当前额度已耗尽时可调用。best-effort 生成会话摘要并更新 user.ai_context。
     */
    UnlockQuotaResponse unlockQuota(Long userId, Long conversationId);

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
     * 发送消息（流式返回 AI 回复）
     */
    void sendMessageStream(Long userId, Long conversationId, String content, SseEmitter emitter, ObjectMapper objectMapper);

    /**
     * 主动关闭会话
     */
    void closeConversation(Long userId, Long conversationId);
}
