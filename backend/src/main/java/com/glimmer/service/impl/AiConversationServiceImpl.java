package com.glimmer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.response.PageResult;
import com.glimmer.common.util.RedisUtils;
import com.glimmer.config.ai.DeepSeekProperties;
import com.glimmer.entity.AiConversation;
import com.glimmer.entity.AiMessage;
import com.glimmer.entity.TokenTransaction;
import com.glimmer.entity.User;
import com.glimmer.mapper.AiConversationMapper;
import com.glimmer.mapper.AiMessageMapper;
import com.glimmer.mapper.TokenTransactionMapper;
import com.glimmer.common.util.TokenBalanceHelper;
import com.glimmer.service.AiConversationService;
import com.glimmer.service.UserService;
import com.glimmer.service.ai.DeepSeekClient;
import com.glimmer.service.ai.DeepSeekMessage;
import com.glimmer.service.dto.AiConversationVO;
import com.glimmer.service.dto.AiMessageVO;
import com.glimmer.service.dto.ConversationDetailVO;
import com.glimmer.service.dto.SendMessageResponse;
import com.glimmer.service.dto.StreamMessageDTO;
import com.glimmer.service.dto.UnlockQuotaResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    /** 单个会话安全硬上限消息数（防异常暴涨，不再作为业务关闭线） */
    private static final int SAFETY_MAX_MESSAGES = 500;
    /** 开启会话消耗代币 */
    private static final int START_CONVERSATION_TOKEN_COST = 1;
    /** 免费会话每日轮次上限 */
    private static final int FREE_DAILY_QUOTA = 10;
    /** 每次解锁增加的轮次 */
    private static final int UNLOCK_QUOTA_STEP = 10;
    /** 摘要提取时取最近的消息条数（10 轮 = 20 条） */
    private static final int SUMMARY_MESSAGE_LIMIT = 20;
    /** 上海时区，用于每日额度重置判断 */
    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final AiConversationMapper aiConversationMapper;
    private final AiMessageMapper aiMessageMapper;
    private final TokenTransactionMapper tokenTransactionMapper;
    private final DeepSeekClient deepSeekClient;
    private final DeepSeekProperties deepSeekProperties;
    private final UserService userService;
    private final StringRedisTemplate stringRedisTemplate;
    private final TokenBalanceHelper tokenBalanceHelper;
    private final ObjectMapper objectMapper;

    /** 缓存 key：AI 会话上下文 ai:ctx:{conversationId}，List 存 role:content，TTL 1 小时 */
    private static final String CACHE_KEY_AI_CTX = "ai:ctx:%d";
    private static final long AI_CTX_TTL_HOURS = 1;

    public AiConversationServiceImpl(AiConversationMapper aiConversationMapper,
                                     AiMessageMapper aiMessageMapper,
                                     TokenTransactionMapper tokenTransactionMapper,
                                     DeepSeekClient deepSeekClient,
                                     DeepSeekProperties deepSeekProperties,
                                     UserService userService,
                                     StringRedisTemplate stringRedisTemplate,
                                     TokenBalanceHelper tokenBalanceHelper,
                                     ObjectMapper objectMapper) {
        this.aiConversationMapper = aiConversationMapper;
        this.aiMessageMapper = aiMessageMapper;
        this.tokenTransactionMapper = tokenTransactionMapper;
        this.deepSeekClient = deepSeekClient;
        this.deepSeekProperties = deepSeekProperties;
        this.userService = userService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.tokenBalanceHelper = tokenBalanceHelper;
        this.objectMapper = objectMapper;
    }

    // ==================== Redis 上下文缓存 ====================

    /**
     * 追加一条消息到 Redis 上下文 List（左侧插入，保留最近 maxContext 条）
     * 格式："role|content"
     */
    private void appendToContextCache(Long conversationId, String role, String content, int maxContext) {
        try {
            String key = String.format(CACHE_KEY_AI_CTX, conversationId);
            String value = role + "|" + content;
            stringRedisTemplate.opsForList().leftPush(key, value);
            // 保留最近 maxContext 条
            stringRedisTemplate.opsForList().trim(key, 0, Math.max(0, maxContext - 1));
            // 幂等设置 TTL
            stringRedisTemplate.expire(key, java.time.Duration.ofHours(AI_CTX_TTL_HOURS));
        } catch (Exception e) {
            log.warn("[Redis] appendToContextCache 失败 conversationId={}, err={}", conversationId, e.getMessage());
        }
    }

    /**
     * 从 Redis 读取上下文（正序：从最早到最近）
     * Redis List 是 LPUSH 后倒序的（最新在左），所以需要反转
     */
    private List<String[]> readContextFromCache(Long conversationId) {
        try {
            String key = String.format(CACHE_KEY_AI_CTX, conversationId);
            List<String> raw = stringRedisTemplate.opsForList().range(key, 0, -1);
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            // raw 是从最新到最早（LPUSH 顺序），反转为从最早到最新
            java.util.Collections.reverse(raw);
            List<String[]> result = new ArrayList<>(raw.size());
            for (String s : raw) {
                int idx = s.indexOf('|');
                if (idx > 0) {
                    result.add(new String[]{s.substring(0, idx), s.substring(idx + 1)});
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("[Redis] readContextFromCache 失败 conversationId={}, err={}", conversationId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 清除上下文缓存（会话关闭时调用）
     */
    private void evictContextCache(Long conversationId) {
        try {
            stringRedisTemplate.delete(String.format(CACHE_KEY_AI_CTX, conversationId));
        } catch (Exception e) {
            log.warn("[Redis] evictContextCache 失败 conversationId={}, err={}", conversationId, e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiConversationVO startConversation(Long userId) {
        // 1. 校验用户非 banned/禁言
        userService.checkUserNotMuted(userId);

        // 2. 扣代币（分布式锁 + 余额校验 + 乐观锁兜底，余额不足/冲突由 helper 抛出）
        tokenBalanceHelper.deduct(userId, START_CONVERSATION_TOKEN_COST);

        // 3. 写流水：source='ai_chat'
        TokenTransaction tx = new TokenTransaction();
        tx.setUserId(userId);
        tx.setType("spend");
        tx.setAmount(START_CONVERSATION_TOKEN_COST);
        tx.setSource("ai_chat");
        tokenTransactionMapper.insert(tx);

        // 4. 插入 ai_conversation（付费会话：初始额度 10 轮）
        LocalDateTime now = LocalDateTime.now();
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setStatus("active");
        conversation.setMessageCount(0);
        conversation.setMaxMessages(SAFETY_MAX_MESSAGES);
        conversation.setStartedAt(now);
        conversation.setLastActiveAt(now);
        conversation.setConversationType("paid");
        conversation.setQuotaUsed(0);
        conversation.setQuotaLimit(UNLOCK_QUOTA_STEP);
        conversation.setTitle("✨ 新对话");
        aiConversationMapper.insert(conversation);

        log.info("AI 付费会话开启成功: userId={}, conversationId={}", userId, conversation.getId());
        return toConversationVO(conversation);
    }

    @Override
    public AiConversationVO getOrCreateFreeConversation(Long userId) {
        // 查找用户当前 active 的 free 会话
        AiConversation free = aiConversationMapper.selectOne(
                new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getUserId, userId)
                        .eq(AiConversation::getConversationType, "free")
                        .eq(AiConversation::getStatus, "active")
                        .last("LIMIT 1"));
        if (free != null) {
            resetQuotaIfStale(free);
            return toConversationVO(free);
        }

        // 无免费会话，创建一个（不消耗代币）
        LocalDateTime now = LocalDateTime.now();
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setStatus("active");
        conversation.setMessageCount(0);
        conversation.setMaxMessages(SAFETY_MAX_MESSAGES);
        conversation.setStartedAt(now);
        conversation.setLastActiveAt(now);
        conversation.setConversationType("free");
        conversation.setQuotaUsed(0);
        conversation.setQuotaLimit(FREE_DAILY_QUOTA);
        conversation.setQuotaResetDate(LocalDate.now(ZONE_SHANGHAI));
        conversation.setTitle("🌙 每日闲聊");
        aiConversationMapper.insert(conversation);

        log.info("AI 免费会话创建成功: userId={}, conversationId={}", userId, conversation.getId());
        return toConversationVO(conversation);
    }

    /**
     * 免费会话额度懒重置：若 quotaResetDate < today，重置为每日免费额度。
     * 仅 free 类型生效，paid 类型跳过。
     */
    private void resetQuotaIfStale(AiConversation conversation) {
        if (!"free".equals(conversation.getConversationType())) {
            return;
        }
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        if (conversation.getQuotaResetDate() == null || conversation.getQuotaResetDate().isBefore(today)) {
            aiConversationMapper.update(null, new LambdaUpdateWrapper<AiConversation>()
                    .eq(AiConversation::getId, conversation.getId())
                    .set(AiConversation::getQuotaUsed, 0)
                    .set(AiConversation::getQuotaLimit, FREE_DAILY_QUOTA)
                    .set(AiConversation::getQuotaResetDate, today));
            conversation.setQuotaUsed(0);
            conversation.setQuotaLimit(FREE_DAILY_QUOTA);
            conversation.setQuotaResetDate(today);
            log.info("免费会话额度已重置: conversationId={}, today={}", conversation.getId(), today);
        }
    }

    /**
     * 从用户首条消息生成会话标题（截取前 20 字）。
     */
    private String generateTitle(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "新对话";
        }
        String title = content.trim().replaceAll("\\s+", " ");
        if (title.length() > 20) {
            return title.substring(0, 20) + "...";
        }
        return title;
    }

    @Override
    public PageResult<AiConversationVO> getConversationList(Long userId, int page, int size) {
        // 确保免费会话存在（首次访问 AI 页面时自动创建）
        getOrCreateFreeConversation(userId);
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
        // 进入详情时懒重置免费会话额度
        resetQuotaIfStale(conversation);
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
    public SendMessageResponse sendMessage(Long userId, Long conversationId, String content) {
        // 注意：不加 @Transactional —— 否则 deepSeekClient.chatCompletion（最长 60s）
        // 会持有 DB 连接，可能导致 HikariCP 连接池耗尽（池仅 10 个连接）。
        // 各 DB 操作各自 auto-commit；若 DeepSeek 失败抛异常，用户消息已存但无 AI 回复，
        // 前端可重试或刷新。

        // 1. 校验会话属于当前用户
        AiConversation conversation = checkConversationOwner(userId, conversationId);

        // 2. 校验 status='active'
        if (!"active".equals(conversation.getStatus())) {
            throw new BusinessException(ErrorCode.AI_CONVERSATION_CLOSED);
        }

        // 3. 懒重置免费会话额度
        resetQuotaIfStale(conversation);

        // 4. 配额校验：1轮 = 1用户消息 + 1AI回复，额度用完不可发送
        int quotaUsed = conversation.getQuotaUsed() != null ? conversation.getQuotaUsed() : 0;
        int quotaLimit = conversation.getQuotaLimit() != null ? conversation.getQuotaLimit() : FREE_DAILY_QUOTA;
        if (quotaUsed >= quotaLimit) {
            throw new BusinessException(ErrorCode.AI_QUOTA_EXHAUSTED);
        }

        // 5. 安全硬上限校验（防异常暴涨）
        int maxMessages = conversation.getMaxMessages() != null ? conversation.getMaxMessages() : SAFETY_MAX_MESSAGES;
        if (conversation.getMessageCount() != null && conversation.getMessageCount() >= maxMessages) {
            closeConversationInternal(conversation);
            throw new BusinessException(ErrorCode.AI_CONVERSATION_CLOSED);
        }

        LocalDateTime now = LocalDateTime.now();

        // 6. 插入 ai_message（role='user'）
        AiMessage userMessage = new AiMessage();
        userMessage.setConversationId(conversationId);
        userMessage.setRole("user");
        userMessage.setContent(content);
        userMessage.setCreatedAt(now);
        aiMessageMapper.insert(userMessage);

        // 7. 更新 message_count += 1, last_active_at；首条消息时同步设置标题
        int newCountAfterUser = (conversation.getMessageCount() == null ? 0 : conversation.getMessageCount()) + 1;
        boolean isFirstMessage = (conversation.getMessageCount() == null || conversation.getMessageCount() == 0);
        if (isFirstMessage) {
            String title = generateTitle(content);
            aiConversationMapper.update(null, new LambdaUpdateWrapper<AiConversation>()
                    .eq(AiConversation::getId, conversationId)
                    .set(AiConversation::getMessageCount, newCountAfterUser)
                    .set(AiConversation::getLastActiveAt, now)
                    .set(AiConversation::getTitle, title));
            conversation.setTitle(title);
        } else {
            updateConversationStats(conversationId, newCountAfterUser, now);
        }

        // 8. 构建上下文（含记忆注入 + 摘要优化，优先 Redis 缓存）
        List<DeepSeekMessage> deepSeekMessages = buildContextWithSummary(userId, conversation, content, userMessage.getId());

        // 9. 调用 DeepSeek（失败抛 BusinessException，事务回滚 user 消息插入）
        String aiContent = deepSeekClient.chatCompletion(deepSeekMessages);

        // 10. 插入 ai_message（role='ai'）
        LocalDateTime aiTime = LocalDateTime.now();
        AiMessage aiMessage = new AiMessage();
        aiMessage.setConversationId(conversationId);
        aiMessage.setRole("ai");
        aiMessage.setContent(aiContent);
        aiMessage.setCreatedAt(aiTime);
        aiMessageMapper.insert(aiMessage);

        // 11. 将新消息写入缓存（user + ai）
        int maxContext = deepSeekProperties.getMaxContextMessages();
        appendToContextCache(conversationId, "user", content, maxContext);
        appendToContextCache(conversationId, "ai", aiContent, maxContext);

        // 12. 更新 message_count += 1, quota_used += 1, last_active_at
        int newCountAfterAi = newCountAfterUser + 1;
        int newQuotaUsed = quotaUsed + 1;
        boolean quotaExhausted = newQuotaUsed >= quotaLimit;
        // 安全硬上限触发时关闭会话
        boolean shouldClose = newCountAfterAi >= maxMessages;
        if (shouldClose) {
            aiConversationMapper.update(null, new LambdaUpdateWrapper<AiConversation>()
                    .eq(AiConversation::getId, conversationId)
                    .set(AiConversation::getMessageCount, newCountAfterAi)
                    .set(AiConversation::getQuotaUsed, newQuotaUsed)
                    .set(AiConversation::getLastActiveAt, aiTime)
                    .set(AiConversation::getStatus, "closed"));
            evictContextCache(conversationId);
        } else {
            aiConversationMapper.update(null, new LambdaUpdateWrapper<AiConversation>()
                    .eq(AiConversation::getId, conversationId)
                    .set(AiConversation::getMessageCount, newCountAfterAi)
                    .set(AiConversation::getQuotaUsed, newQuotaUsed)
                    .set(AiConversation::getLastActiveAt, aiTime));
        }

        log.info("AI 消息发送成功: userId={}, conversationId={}, messageCount={}, quotaUsed={}/{}",
                userId, conversationId, newCountAfterAi, newQuotaUsed, quotaLimit);

        // 13. 返回 SendMessageResponse（含配额信息）
        SendMessageResponse response = new SendMessageResponse();
        response.setUserMessage(toMessageVO(userMessage));
        response.setAiMessage(toMessageVO(aiMessage));
        response.setConversationStatus(shouldClose ? "closed" : "active");
        response.setMessageCount(newCountAfterAi);
        response.setMaxMessages(maxMessages);
        response.setQuotaUsed(newQuotaUsed);
        response.setQuotaLimit(quotaLimit);
        response.setQuotaExhausted(quotaExhausted);
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
    public UnlockQuotaResponse unlockQuota(Long userId, Long conversationId) {
        // 1. 校验会话归属
        AiConversation conversation = checkConversationOwner(userId, conversationId);

        // 2. 校验会话 active
        if (!"active".equals(conversation.getStatus())) {
            throw new BusinessException(ErrorCode.AI_CONVERSATION_CLOSED);
        }

        // 3. 懒重置免费会话额度（若跨天则无需解锁）
        resetQuotaIfStale(conversation);

        // 4. 校验额度已耗尽（仅耗尽时才需解锁）
        int quotaUsed = conversation.getQuotaUsed() != null ? conversation.getQuotaUsed() : 0;
        int quotaLimit = conversation.getQuotaLimit() != null ? conversation.getQuotaLimit() : FREE_DAILY_QUOTA;
        if (quotaUsed < quotaLimit) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "当前额度未耗尽，无需解锁");
        }

        // 5. 扣代币（分布式锁 + 余额校验 + 乐观锁兜底，自带独立事务）
        User user = tokenBalanceHelper.deduct(userId, 1);

        // 6. 写流水
        TokenTransaction tx = new TokenTransaction();
        tx.setUserId(userId);
        tx.setType("spend");
        tx.setAmount(1);
        tx.setSource("ai_chat_unlock");
        tokenTransactionMapper.insert(tx);

        // 7. 额度上限 += UNLOCK_QUOTA_STEP（先更新额度，让用户能立即继续聊天）
        int newQuotaLimit = quotaLimit + UNLOCK_QUOTA_STEP;
        aiConversationMapper.update(null, new LambdaUpdateWrapper<AiConversation>()
                .eq(AiConversation::getId, conversationId)
                .set(AiConversation::getQuotaLimit, newQuotaLimit));
        conversation.setQuotaLimit(newQuotaLimit);

        // 8. best-effort 生成摘要（在所有 DB 操作之后，无事务包裹，避免 DeepSeek 同步调用占用 DB 连接）
        boolean summaryGenerated = generateSummary(conversation, userId);

        log.info("AI 对话额度解锁成功: userId={}, conversationId={}, newQuotaLimit={}, summaryGenerated={}",
                userId, conversationId, newQuotaLimit, summaryGenerated);

        // 9. 返回响应
        UnlockQuotaResponse response = new UnlockQuotaResponse();
        response.setConversationId(conversationId);
        response.setQuotaUsed(quotaUsed);
        response.setQuotaLimit(newQuotaLimit);
        response.setTokenBalance(user.getTokenBalance());
        response.setSummaryGenerated(summaryGenerated);
        return response;
    }

    /**
     * 生成会话摘要并更新用户记忆（best-effort，失败仅记录日志）。
     * <p>
     * 1. 取该会话最近 20 条消息（10 轮）
     * 2. 调用 DeepSeek 生成 2-3 句摘要 + 3-5 个关键信息词条
     * 3. 摘要写入 ai_conversation.summary
     * 4. 关键信息覆盖写入 user.ai_context（JSON）
     *
     * @return 是否成功生成摘要
     */
    private boolean generateSummary(AiConversation conversation, Long userId) {
        try {
            // 1. 取最近 SUMMARY_MESSAGE_LIMIT 条消息
            Page<AiMessage> msgPage = new Page<>(1, SUMMARY_MESSAGE_LIMIT);
            LambdaQueryWrapper<AiMessage> wrapper = new LambdaQueryWrapper<AiMessage>()
                    .eq(AiMessage::getConversationId, conversation.getId())
                    .orderByDesc(AiMessage::getCreatedAt);
            IPage<AiMessage> result = aiMessageMapper.selectPage(msgPage, wrapper);
            List<AiMessage> recentMessages = result.getRecords();
            if (recentMessages.isEmpty()) {
                return false;
            }
            // 反转为正序（最早→最新）便于阅读
            java.util.Collections.reverse(recentMessages);

            // 2. 构建对话内容
            StringBuilder dialogContent = new StringBuilder();
            for (AiMessage m : recentMessages) {
                String role = "user".equals(m.getRole()) ? "用户" : "AI";
                dialogContent.append(role).append(": ").append(m.getContent()).append("\n");
            }

            // 3. 调用 DeepSeek 生成摘要
            String prompt = String.format(deepSeekProperties.getSummaryPrompt(), dialogContent.toString());
            List<DeepSeekMessage> summaryMessages = new ArrayList<>();
            summaryMessages.add(new DeepSeekMessage("user", prompt));
            String response = deepSeekClient.chatCompletion(summaryMessages);

            // 4. 解析 JSON（容错：去除 ```json 包裹）
            String json = response.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(json)?\\s*", "").replaceAll("\\s*```$", "");
            }
            JsonNode root = objectMapper.readTree(json);

            // 5. summary 写入 conversation.summary
            if (root.has("summary") && !root.get("summary").isNull()) {
                String summary = root.get("summary").asText();
                if (StringUtils.hasText(summary)) {
                    conversation.setSummary(summary);
                    aiConversationMapper.update(null, new LambdaUpdateWrapper<AiConversation>()
                            .eq(AiConversation::getId, conversation.getId())
                            .set(AiConversation::getSummary, summary));
                }
            }

            // 6. keyInfo 覆盖写入 user.ai_context（JSON 字符串）
            if (root.has("keyInfo") && !root.get("keyInfo").isNull()) {
                String keyInfoJson = objectMapper.writeValueAsString(root.get("keyInfo"));
                tokenBalanceHelper.modifyWithLock(userId, u -> u.setAiContext(keyInfoJson));
            }

            log.info("会话摘要生成成功: conversationId={}, userId={}", conversation.getId(), userId);
            return true;
        } catch (Exception e) {
            log.warn("会话摘要生成失败（best-effort，不影响解锁）: conversationId={}, err={}",
                    conversation.getId(), e.getMessage());
            return false;
        }
    }

    @Override
    public void sendMessageStream(Long userId, Long conversationId, String content, SseEmitter emitter, ObjectMapper objectMapper) {
        AiConversation conversation = null;
        AiMessage userMessage = null;
        int maxMessages = SAFETY_MAX_MESSAGES;
        int newCountAfterUser = 0;
        int quotaUsed = 0;
        int quotaLimit = FREE_DAILY_QUOTA;

        try {
            // 1. 校验会话属于当前用户
            conversation = checkConversationOwner(userId, conversationId);

            // 2. 校验 status='active'
            if (!"active".equals(conversation.getStatus())) {
                sendError(emitter, objectMapper, "会话已关闭");
                return;
            }

            // 3. 懒重置免费会话额度
            resetQuotaIfStale(conversation);

            // 4. 配额校验：额度用完不可发送
            quotaUsed = conversation.getQuotaUsed() != null ? conversation.getQuotaUsed() : 0;
            quotaLimit = conversation.getQuotaLimit() != null ? conversation.getQuotaLimit() : FREE_DAILY_QUOTA;
            if (quotaUsed >= quotaLimit) {
                sendError(emitter, objectMapper, "本轮对话额度已用完");
                return;
            }

            // 5. 安全硬上限校验
            maxMessages = conversation.getMaxMessages() != null ? conversation.getMaxMessages() : SAFETY_MAX_MESSAGES;
            if (conversation.getMessageCount() != null && conversation.getMessageCount() >= maxMessages) {
                closeConversationInternal(conversation);
                sendError(emitter, objectMapper, "会话已关闭");
                return;
            }

            LocalDateTime now = LocalDateTime.now();

            // 6. 插入 ai_message（role='user'）
            userMessage = new AiMessage();
            userMessage.setConversationId(conversationId);
            userMessage.setRole("user");
            userMessage.setContent(content);
            userMessage.setCreatedAt(now);
            aiMessageMapper.insert(userMessage);

            // 7. 更新 message_count += 1, last_active_at；首条消息时同步设置标题
            newCountAfterUser = (conversation.getMessageCount() == null ? 0 : conversation.getMessageCount()) + 1;
            boolean isFirstMessage = (conversation.getMessageCount() == null || conversation.getMessageCount() == 0);
            if (isFirstMessage) {
                String title = generateTitle(content);
                aiConversationMapper.update(null, new LambdaUpdateWrapper<AiConversation>()
                        .eq(AiConversation::getId, conversationId)
                        .set(AiConversation::getMessageCount, newCountAfterUser)
                        .set(AiConversation::getLastActiveAt, now)
                        .set(AiConversation::getTitle, title));
                conversation.setTitle(title);
            } else {
                updateConversationStats(conversationId, newCountAfterUser, now);
            }

            // 8. 构建上下文（含记忆注入 + 摘要优化，优先 Redis 缓存）
            List<DeepSeekMessage> deepSeekMessages = buildContextWithSummary(userId, conversation, content, userMessage.getId());

            // 9. 流式调用 DeepSeek（使用回调方式）
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

                // 将本轮 user + ai 消息写入 Redis 上下文缓存
                int maxContext = deepSeekProperties.getMaxContextMessages();
                appendToContextCache(conversationId, "user", content, maxContext);
                appendToContextCache(conversationId, "ai", finalContent, maxContext);

                // 更新会话状态：message_count += 1, quota_used += 1
                int count = newCountAfterUser + 1;
                int newQuotaUsed = quotaUsed + 1;
                boolean quotaExhausted = newQuotaUsed >= quotaLimit;
                boolean shouldClose = count >= maxMessages;
                if (shouldClose) {
                    aiConversationMapper.update(null, new LambdaUpdateWrapper<AiConversation>()
                            .eq(AiConversation::getId, conversationId)
                            .set(AiConversation::getMessageCount, count)
                            .set(AiConversation::getQuotaUsed, newQuotaUsed)
                            .set(AiConversation::getLastActiveAt, aiTime)
                            .set(AiConversation::getStatus, "closed"));
                    evictContextCache(conversationId);
                } else {
                    aiConversationMapper.update(null, new LambdaUpdateWrapper<AiConversation>()
                            .eq(AiConversation::getId, conversationId)
                            .set(AiConversation::getMessageCount, count)
                            .set(AiConversation::getQuotaUsed, newQuotaUsed)
                            .set(AiConversation::getLastActiveAt, aiTime));
                }

                log.info("AI 流式消息发送成功: userId={}, conversationId={}, messageCount={}, quotaUsed={}/{}",
                        userId, conversationId, count, newQuotaUsed, quotaLimit);

                sendFinal(emitter, objectMapper, toMessageVO(aiMessage), toMessageVO(userMessage),
                        shouldClose ? "closed" : "active", count, maxMessages,
                        newQuotaUsed, quotaLimit, quotaExhausted);
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

    /**
     * 发送最终消息（含配额信息，前端据此更新额度显示与触发解锁弹窗）
     */
    private void sendFinal(SseEmitter emitter, ObjectMapper objectMapper, AiMessageVO aiMessage, AiMessageVO userMessage,
                           String conversationStatus, Integer messageCount, Integer maxMessages,
                           Integer quotaUsed, Integer quotaLimit, Boolean quotaExhausted) {
        try {
            StreamMessageDTO dto = StreamMessageDTO.finalMessage(aiMessage, userMessage, conversationStatus,
                    messageCount, maxMessages, quotaUsed, quotaLimit, quotaExhausted);
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
     * 构建上下文消息（含记忆注入 + 长上下文摘要优化）
     * <p>
     * 记忆注入：在系统提示词后追加【关于这位用户的记忆】块，包含上次摘要和关键信息，
     * 让 AI 具备跨会话记忆能力。AI 应自然使用，不直接说"根据记忆"。
     *
     * @param conversation      当前会话（用于读取 summary 和 id）
     * @param excludeMessageId  刚插入的用户消息 ID，DB 查询时排除（避免与 currentContent 重复）
     */
    private List<DeepSeekMessage> buildContextWithSummary(Long userId, AiConversation conversation, String currentContent, Long excludeMessageId) {
        List<DeepSeekMessage> messages = new ArrayList<>();
        Long conversationId = conversation.getId();

        // 添加系统提示词
        String systemPrompt = deepSeekProperties.getSystemPrompt();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(new DeepSeekMessage("system", systemPrompt));
        }

        // === 记忆注入：在系统提示词后追加用户记忆块 ===
        String summary = conversation.getSummary();
        String aiContext = null;
        try {
            aiContext = userService.getAiContext(userId);
        } catch (Exception e) {
            log.warn("[AI记忆] 读取用户 ai_context 失败, userId={}, err={}", userId, e.getMessage());
        }
        if (StringUtils.hasText(summary) || StringUtils.hasText(aiContext)) {
            StringBuilder memory = new StringBuilder("【关于这位用户的记忆】\n");
            if (StringUtils.hasText(summary)) {
                memory.append("上次摘要：").append(summary).append("\n");
            }
            if (StringUtils.hasText(aiContext)) {
                memory.append("关键信息：").append(aiContext).append("\n");
            }
            memory.append("（自然地使用，不要说\"根据记忆\"）");
            messages.add(new DeepSeekMessage("system", memory.toString()));
        }

        int maxContext = deepSeekProperties.getMaxContextMessages();

        // 优先 Redis 缓存读取历史（缓存中不包含刚插入的用户消息，天然排除重复）
        List<String[]> cachedAsc = readContextFromCache(conversationId); // ASC: 最早→最新

        // 统一转为 DESC 顺序（最新在前），与原 DB 查询 orderByDesc 一致
        List<String[]> historyDesc;
        if (!cachedAsc.isEmpty()) {
            historyDesc = new ArrayList<>(cachedAsc);
            java.util.Collections.reverse(historyDesc); // ASC → DESC
        } else {
            // 缓存未命中，查 DB（排除刚插入的用户消息）并回填缓存
            Page<AiMessage> contextPage = new Page<>(1, maxContext);
            LambdaQueryWrapper<AiMessage> contextWrapper = new LambdaQueryWrapper<AiMessage>()
                    .eq(AiMessage::getConversationId, conversationId)
                    .ne(excludeMessageId != null, AiMessage::getId, excludeMessageId)
                    .orderByDesc(AiMessage::getCreatedAt);
            IPage<AiMessage> contextResult = aiMessageMapper.selectPage(contextPage, contextWrapper);
            List<AiMessage> dbHistory = contextResult.getRecords(); // DESC: 最新在前

            historyDesc = new ArrayList<>();
            for (AiMessage m : dbHistory) {
                historyDesc.add(new String[]{m.getRole(), m.getContent()});
                appendToContextCache(conversationId, m.getRole(), m.getContent(), maxContext);
            }
        }

        // 摘要优化（historyDesc 是 DESC 顺序：最新在前）
        if (historyDesc.size() > 4) {
            // 保留最近的 2 轮对话作为详细上下文，更早的合并为摘要
            List<String[]> recentHistory = new ArrayList<>();
            List<String[]> oldHistory = new ArrayList<>();

            for (int i = 0; i < historyDesc.size(); i++) {
                if (i < 4) {
                    recentHistory.add(historyDesc.get(i));
                } else {
                    oldHistory.add(historyDesc.get(i));
                }
            }

            // 构建旧对话摘要
            if (!oldHistory.isEmpty()) {
                StringBuilder oldSummary = new StringBuilder("【历史对话摘要】\n");
                for (String[] m : oldHistory) {
                    String roleName = "user".equals(m[0]) ? "用户" : "AI";
                    String msgContent = m[1];
                    if (msgContent.length() > 100) {
                        msgContent = msgContent.substring(0, 100) + "...";
                    }
                    oldSummary.append(roleName).append(": ").append(msgContent).append("\n");
                }
                oldSummary.append("【摘要结束】");
                messages.add(new DeepSeekMessage("system", oldSummary.toString()));
            }

            // 添加最近的详细对话（反转回正序）
            java.util.Collections.reverse(recentHistory);
            for (String[] m : recentHistory) {
                String role = "ai".equals(m[0]) ? "assistant" : m[0];
                messages.add(new DeepSeekMessage(role, m[1]));
            }
        } else {
            // 历史消息较少，直接使用
            java.util.Collections.reverse(historyDesc);
            for (String[] m : historyDesc) {
                String role = "ai".equals(m[0]) ? "assistant" : m[0];
                messages.add(new DeepSeekMessage(role, m[1]));
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
        // 清除上下文缓存
        evictContextCache(conversation.getId());
    }

    private AiConversationVO toConversationVO(AiConversation conversation) {
        AiConversationVO vo = new AiConversationVO();
        vo.setId(conversation.getId());
        vo.setStatus(conversation.getStatus());
        vo.setMessageCount(conversation.getMessageCount());
        vo.setMaxMessages(conversation.getMaxMessages());
        vo.setStartedAt(conversation.getStartedAt());
        vo.setLastActiveAt(conversation.getLastActiveAt());
        vo.setConversationType(conversation.getConversationType());
        vo.setQuotaUsed(conversation.getQuotaUsed());
        vo.setQuotaLimit(conversation.getQuotaLimit());
        vo.setSummary(conversation.getSummary());
        vo.setTitle(conversation.getTitle());
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
