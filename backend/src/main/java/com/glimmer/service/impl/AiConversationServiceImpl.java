package com.glimmer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.response.PageResult;
import com.glimmer.config.ai.DeepSeekProperties;
import com.glimmer.entity.AiConversation;
import com.glimmer.entity.AiMessage;
import com.glimmer.entity.TokenTransaction;
import com.glimmer.entity.User;
import com.glimmer.mapper.AiConversationMapper;
import com.glimmer.mapper.AiMessageMapper;
import com.glimmer.mapper.TokenTransactionMapper;
import com.glimmer.mapper.UserMapper;
import com.glimmer.service.AiConversationService;
import com.glimmer.service.UserService;
import com.glimmer.service.ai.DeepSeekClient;
import com.glimmer.service.ai.DeepSeekMessage;
import com.glimmer.service.dto.AiConversationVO;
import com.glimmer.service.dto.AiMessageVO;
import com.glimmer.service.dto.ConversationDetailVO;
import com.glimmer.service.dto.SendMessageResponse;
import com.glimmer.service.dto.StreamMessageDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 对话服务实现
 * 见开发文档 §2.6 / §4.9 / §3.4.4
 */
@Slf4j
@Service
public class AiConversationServiceImpl implements AiConversationService {

    /** 单个会话最大消息数（见开发文档 §2.6.1） */
    private static final int MAX_MESSAGES = 100;
    /** 开启会话消耗代币 */
    private static final int START_CONVERSATION_TOKEN_COST = 1;

    private final AiConversationMapper aiConversationMapper;
    private final AiMessageMapper aiMessageMapper;
    private final UserMapper userMapper;
    private final TokenTransactionMapper tokenTransactionMapper;
    private final DeepSeekClient deepSeekClient;
    private final DeepSeekProperties deepSeekProperties;
    private final UserService userService;

    public AiConversationServiceImpl(AiConversationMapper aiConversationMapper,
                                     AiMessageMapper aiMessageMapper,
                                     UserMapper userMapper,
                                     TokenTransactionMapper tokenTransactionMapper,
                                     DeepSeekClient deepSeekClient,
                                     DeepSeekProperties deepSeekProperties,
                                     UserService userService) {
        this.aiConversationMapper = aiConversationMapper;
        this.aiMessageMapper = aiMessageMapper;
        this.userMapper = userMapper;
        this.tokenTransactionMapper = tokenTransactionMapper;
        this.deepSeekClient = deepSeekClient;
        this.deepSeekProperties = deepSeekProperties;
        this.userService = userService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiConversationVO startConversation(Long userId) {
        // 1. 校验用户非 banned/禁言
        userService.checkUserNotMuted(userId);

        // 2. 校验代币余额 >= 1
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (user.getTokenBalance() == null || user.getTokenBalance() < START_CONVERSATION_TOKEN_COST) {
            throw new BusinessException(ErrorCode.TOKEN_NOT_ENOUGH);
        }

        // 3. 扣代币（乐观锁 @Version）
        user.setTokenBalance(user.getTokenBalance() - START_CONVERSATION_TOKEN_COST);
        boolean updated = userMapper.updateById(user) > 0;
        if (!updated) {
            throw new BusinessException(ErrorCode.CONFLICT, "代币扣减冲突，请重试");
        }

        // 4. 写流水：source='ai_chat'
        TokenTransaction tx = new TokenTransaction();
        tx.setUserId(userId);
        tx.setType("spend");
        tx.setAmount(START_CONVERSATION_TOKEN_COST);
        tx.setSource("ai_chat");
        tokenTransactionMapper.insert(tx);

        // 5. 插入 ai_conversation
        LocalDateTime now = LocalDateTime.now();
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setStatus("active");
        conversation.setMessageCount(0);
        conversation.setMaxMessages(MAX_MESSAGES);
        conversation.setStartedAt(now);
        conversation.setLastActiveAt(now);
        aiConversationMapper.insert(conversation);

        log.info("AI 会话开启成功: userId={}, conversationId={}", userId, conversation.getId());
        return toConversationVO(conversation);
    }

    @Override
    public PageResult<AiConversationVO> getConversationList(Long userId, int page, int size) {
        Page<AiConversation> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<AiConversation> wrapper = new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getUserId, userId)
                .orderByDesc(AiConversation::getStartedAt);
        IPage<AiConversation> result = aiConversationMapper.selectPage(pageParam, wrapper);
        List<AiConversationVO> list = result.getRecords().stream()
                .map(this::toConversationVO).collect(Collectors.toList());
        return new PageResult<>(list, result.getTotal(), page, size);
    }

    @Override
    public ConversationDetailVO getConversationDetail(Long userId, Long conversationId) {
        AiConversation conversation = checkConversationOwner(userId, conversationId);
        List<AiMessage> messages = aiMessageMapper.selectList(
                new LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getConversationId, conversationId)
                        .orderByAsc(AiMessage::getCreatedAt));

        ConversationDetailVO vo = new ConversationDetailVO();
        vo.setConversation(toConversationVO(conversation));
        vo.setMessages(messages.stream().map(this::toMessageVO).collect(Collectors.toList()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SendMessageResponse sendMessage(Long userId, Long conversationId, String content) {
        // 1. 校验会话属于当前用户
        AiConversation conversation = checkConversationOwner(userId, conversationId);

        // 2. 校验 status='active'
        if (!"active".equals(conversation.getStatus())) {
            throw new BusinessException(ErrorCode.AI_CONVERSATION_CLOSED);
        }

        // 3. 校验 message_count < max_messages（达上限自动关闭并抛异常）
        int maxMessages = conversation.getMaxMessages() != null ? conversation.getMaxMessages() : MAX_MESSAGES;
        if (conversation.getMessageCount() != null && conversation.getMessageCount() >= maxMessages) {
            closeConversationInternal(conversation);
            throw new BusinessException(ErrorCode.AI_CONVERSATION_CLOSED);
        }

        LocalDateTime now = LocalDateTime.now();

        // 4. 插入 ai_message（role='user'）
        AiMessage userMessage = new AiMessage();
        userMessage.setConversationId(conversationId);
        userMessage.setRole("user");
        userMessage.setContent(content);
        userMessage.setCreatedAt(now);
        aiMessageMapper.insert(userMessage);

        // 5. 更新 message_count += 1, last_active_at
        int newCountAfterUser = (conversation.getMessageCount() == null ? 0 : conversation.getMessageCount()) + 1;
        updateConversationStats(conversationId, newCountAfterUser, now);

        // 6. 拉取历史消息（最近 maxContextMessages 条）作为上下文
        int maxContext = deepSeekProperties.getMaxContextMessages();
        Page<AiMessage> contextPage = new Page<>(1, maxContext);
        LambdaQueryWrapper<AiMessage> contextWrapper = new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .orderByDesc(AiMessage::getCreatedAt);
        IPage<AiMessage> contextResult = aiMessageMapper.selectPage(contextPage, contextWrapper);
        List<AiMessage> history = contextResult.getRecords();
        // 倒序查询后反转为正序
        java.util.Collections.reverse(history);

        // 7. 构建 DeepSeek 消息列表：system + 历史消息
        List<DeepSeekMessage> deepSeekMessages = new ArrayList<>();
        String systemPrompt = deepSeekProperties.getSystemPrompt();
        if (StringUtils.hasText(systemPrompt)) {
            deepSeekMessages.add(new DeepSeekMessage("system", systemPrompt));
        }
        for (AiMessage m : history) {
            String role = "ai".equals(m.getRole()) ? "assistant" : m.getRole();
            deepSeekMessages.add(new DeepSeekMessage(role, m.getContent()));
        }

        // 8. 调用 DeepSeek（失败抛 BusinessException，事务回滚 user 消息插入）
        String aiContent = deepSeekClient.chatCompletion(deepSeekMessages);

        // 9. 插入 ai_message（role='ai'）
        LocalDateTime aiTime = LocalDateTime.now();
        AiMessage aiMessage = new AiMessage();
        aiMessage.setConversationId(conversationId);
        aiMessage.setRole("ai");
        aiMessage.setContent(aiContent);
        aiMessage.setCreatedAt(aiTime);
        aiMessageMapper.insert(aiMessage);

        // 10. 更新 message_count += 1, last_active_at
        int newCountAfterAi = newCountAfterUser + 1;
        // 11. 若 message_count >= max_messages：自动关闭
        boolean shouldClose = newCountAfterAi >= maxMessages;
        if (shouldClose) {
            aiConversationMapper.update(null, new LambdaUpdateWrapper<AiConversation>()
                    .eq(AiConversation::getId, conversationId)
                    .set(AiConversation::getMessageCount, newCountAfterAi)
                    .set(AiConversation::getLastActiveAt, aiTime)
                    .set(AiConversation::getStatus, "closed"));
        } else {
            updateConversationStats(conversationId, newCountAfterAi, aiTime);
        }

        log.info("AI 消息发送成功: userId={}, conversationId={}, messageCount={}", userId, conversationId, newCountAfterAi);

        // 12. 返回 SendMessageResponse
        SendMessageResponse response = new SendMessageResponse();
        response.setUserMessage(toMessageVO(userMessage));
        response.setAiMessage(toMessageVO(aiMessage));
        response.setConversationStatus(shouldClose ? "closed" : "active");
        response.setMessageCount(newCountAfterAi);
        response.setMaxMessages(maxMessages);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeConversation(Long userId, Long conversationId) {
        AiConversation conversation = checkConversationOwner(userId, conversationId);
        if ("closed".equals(conversation.getStatus()) || "timeout".equals(conversation.getStatus())) {
            // 已关闭，幂等返回
            return;
        }
        closeConversationInternal(conversation);
        log.info("AI 会话主动关闭: userId={}, conversationId={}", userId, conversationId);
    }

    @Override
    public void sendMessageStream(Long userId, Long conversationId, String content, SseEmitter emitter, ObjectMapper objectMapper) {
        AiConversation conversation = null;
        AiMessage userMessage = null;
        int maxMessages = MAX_MESSAGES;
        int newCountAfterUser = 0;

        try {
            // 1. 校验会话属于当前用户
            conversation = checkConversationOwner(userId, conversationId);

            // 2. 校验 status='active'
            if (!"active".equals(conversation.getStatus())) {
                sendError(emitter, objectMapper, "会话已关闭");
                return;
            }

            // 3. 校验 message_count < max_messages
            maxMessages = conversation.getMaxMessages() != null ? conversation.getMaxMessages() : MAX_MESSAGES;
            if (conversation.getMessageCount() != null && conversation.getMessageCount() >= maxMessages) {
                closeConversationInternal(conversation);
                sendError(emitter, objectMapper, "会话已关闭");
                return;
            }

            LocalDateTime now = LocalDateTime.now();

            // 4. 插入 ai_message（role='user'）
            userMessage = new AiMessage();
            userMessage.setConversationId(conversationId);
            userMessage.setRole("user");
            userMessage.setContent(content);
            userMessage.setCreatedAt(now);
            aiMessageMapper.insert(userMessage);

            // 5. 更新 message_count += 1, last_active_at
            newCountAfterUser = (conversation.getMessageCount() == null ? 0 : conversation.getMessageCount()) + 1;
            updateConversationStats(conversationId, newCountAfterUser, now);

            // 6. 拉取历史消息并构建上下文（含摘要优化）
            List<DeepSeekMessage> deepSeekMessages = buildContextWithSummary(userId, conversationId, content);

            // 7. 流式调用 DeepSeek（使用回调方式）
            StringBuilder fullContent = new StringBuilder();

            deepSeekClient.chatCompletionStream(deepSeekMessages, new DeepSeekClient.StreamCallback() {
                @Override
                public void onDelta(String delta) {
                    fullContent.append(delta);
                    sendDelta(emitter, objectMapper, delta);
                }

                @Override
                public void onError(Exception e) {
                    log.error("AI 流式调用异常", e);
                    sendError(emitter, objectMapper, "AI 服务暂时不可用");
                }
            });

            // 流式调用完成后，保存 AI 消息并返回最终状态
            try {
                String finalContent = fullContent.toString();
                if (finalContent.isEmpty()) {
                    finalContent = "（AI 暂未返回内容）";
                }

                LocalDateTime aiTime = LocalDateTime.now();
                AiMessage aiMessage = new AiMessage();
                aiMessage.setConversationId(conversationId);
                aiMessage.setRole("ai");
                aiMessage.setContent(finalContent);
                aiMessage.setCreatedAt(aiTime);
                aiMessageMapper.insert(aiMessage);

                // 更新会话状态
                int count = newCountAfterUser + 1;
                boolean shouldClose = count >= maxMessages;
                if (shouldClose) {
                    aiConversationMapper.update(null, new LambdaUpdateWrapper<AiConversation>()
                            .eq(AiConversation::getId, conversationId)
                            .set(AiConversation::getMessageCount, count)
                            .set(AiConversation::getLastActiveAt, aiTime)
                            .set(AiConversation::getStatus, "closed"));
                } else {
                    updateConversationStats(conversationId, count, aiTime);
                }

                log.info("AI 流式消息发送成功: userId={}, conversationId={}, messageCount={}", userId, conversationId, count);

                sendFinal(emitter, objectMapper, toMessageVO(aiMessage), toMessageVO(userMessage),
                        shouldClose ? "closed" : "active", count, maxMessages);
            } catch (Exception e) {
                log.error("保存 AI 消息失败", e);
                sendError(emitter, objectMapper, "保存消息失败");
            }

        } catch (BusinessException e) {
            sendError(emitter, objectMapper, e.getMessage());
        } catch (Exception e) {
            log.error("发送消息前置操作失败", e);
            sendError(emitter, objectMapper, "发送失败");
        }
    }

    private void sendDelta(SseEmitter emitter, ObjectMapper objectMapper, String delta) {
        try {
            StreamMessageDTO dto = StreamMessageDTO.delta(delta);
            String json = objectMapper.writeValueAsString(dto);
            emitter.send(SseEmitter.event().data(json));
        } catch (Exception e) {
            log.error("发送增量消息失败", e);
        }
    }

    private void sendFinal(SseEmitter emitter, ObjectMapper objectMapper, AiMessageVO aiMessage, AiMessageVO userMessage,
                           String conversationStatus, Integer messageCount, Integer maxMessages) {
        try {
            StreamMessageDTO dto = StreamMessageDTO.finalMessage(aiMessage, userMessage, conversationStatus, messageCount, maxMessages);
            String json = objectMapper.writeValueAsString(dto);
            emitter.send(SseEmitter.event().data(json));
            emitter.complete();
        } catch (Exception e) {
            log.error("发送最终消息失败", e);
            emitter.completeWithError(e);
        }
    }

    private void sendError(SseEmitter emitter, ObjectMapper objectMapper, String error) {
        try {
            StreamMessageDTO dto = StreamMessageDTO.error(error);
            String json = objectMapper.writeValueAsString(dto);
            emitter.send(SseEmitter.event().data(json));
            emitter.complete();
        } catch (Exception e) {
            log.error("发送错误消息失败", e);
            emitter.completeWithError(e);
        }
    }

    /**
     * 构建上下文消息（含长上下文摘要优化）
     */
    private List<DeepSeekMessage> buildContextWithSummary(Long userId, Long conversationId, String currentContent) {
        List<DeepSeekMessage> messages = new ArrayList<>();

        // 添加系统提示词
        String systemPrompt = deepSeekProperties.getSystemPrompt();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(new DeepSeekMessage("system", systemPrompt));
        }

        // 拉取历史消息（不包含刚刚插入的当前用户消息）
        int maxContext = deepSeekProperties.getMaxContextMessages();
        Page<AiMessage> contextPage = new Page<>(1, maxContext);
        LambdaQueryWrapper<AiMessage> contextWrapper = new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .orderByDesc(AiMessage::getCreatedAt);
        IPage<AiMessage> contextResult = aiMessageMapper.selectPage(contextPage, contextWrapper);
        List<AiMessage> history = contextResult.getRecords();

        // 如果历史消息过多，进行摘要优化
        if (history.size() > 4) {
            // 保留最近的 2 轮对话作为详细上下文
            // 更早的对话合并为摘要
            List<AiMessage> recentHistory = new ArrayList<>();
            List<AiMessage> oldHistory = new ArrayList<>();

            for (int i = 0; i < history.size(); i++) {
                if (i < 4) { // 最近的 2 轮（用户+AI）
                    recentHistory.add(history.get(i));
                } else {
                    oldHistory.add(history.get(i));
                }
            }

            // 构建旧对话摘要
            if (!oldHistory.isEmpty()) {
                StringBuilder summary = new StringBuilder("【历史对话摘要】\n");
                for (AiMessage m : oldHistory) {
                    String roleName = "user".equals(m.getRole()) ? "用户" : "AI";
                    // 截取每条消息的前 100 字
                    String msgContent = m.getContent();
                    if (msgContent.length() > 100) {
                        msgContent = msgContent.substring(0, 100) + "...";
                    }
                    summary.append(roleName).append(": ").append(msgContent).append("\n");
                }
                summary.append("【摘要结束】");
                messages.add(new DeepSeekMessage("system", summary.toString()));
            }

            // 添加最近的详细对话（反转回正序）
            java.util.Collections.reverse(recentHistory);
            for (AiMessage m : recentHistory) {
                String role = "ai".equals(m.getRole()) ? "assistant" : m.getRole();
                messages.add(new DeepSeekMessage(role, m.getContent()));
            }
        } else {
            // 历史消息较少，直接使用
            java.util.Collections.reverse(history);
            for (AiMessage m : history) {
                String role = "ai".equals(m.getRole()) ? "assistant" : m.getRole();
                messages.add(new DeepSeekMessage(role, m.getContent()));
            }
        }

        // 添加当前用户消息到上下文（关键！）
        messages.add(new DeepSeekMessage("user", currentContent));

        return messages;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 校验会话属于当前用户
     */
    private AiConversation checkConversationOwner(Long userId, Long conversationId) {
        AiConversation conversation = aiConversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        if (!userId.equals(conversation.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该会话");
        }
        return conversation;
    }

    /**
     * 更新会话 message_count 和 last_active_at
     */
    private void updateConversationStats(Long conversationId, int messageCount, LocalDateTime lastActiveAt) {
        aiConversationMapper.update(null, new LambdaUpdateWrapper<AiConversation>()
                .eq(AiConversation::getId, conversationId)
                .set(AiConversation::getMessageCount, messageCount)
                .set(AiConversation::getLastActiveAt, lastActiveAt));
    }

    /**
     * 关闭会话（内部方法，不校验归属）
     */
    private void closeConversationInternal(AiConversation conversation) {
        aiConversationMapper.update(null, new LambdaUpdateWrapper<AiConversation>()
                .eq(AiConversation::getId, conversation.getId())
                .set(AiConversation::getStatus, "closed"));
    }

    private AiConversationVO toConversationVO(AiConversation conversation) {
        AiConversationVO vo = new AiConversationVO();
        vo.setId(conversation.getId());
        vo.setStatus(conversation.getStatus());
        vo.setMessageCount(conversation.getMessageCount());
        vo.setMaxMessages(conversation.getMaxMessages());
        vo.setStartedAt(conversation.getStartedAt());
        vo.setLastActiveAt(conversation.getLastActiveAt());
        return vo;
    }

    private AiMessageVO toMessageVO(AiMessage message) {
        AiMessageVO vo = new AiMessageVO();
        vo.setId(message.getId());
        vo.setConversationId(message.getConversationId());
        vo.setRole(message.getRole());
        vo.setContent(message.getContent());
        vo.setCreatedAt(message.getCreatedAt());
        return vo;
    }
}
