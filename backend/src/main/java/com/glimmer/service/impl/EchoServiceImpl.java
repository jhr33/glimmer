package com.glimmer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.util.RedisUtils;
import com.glimmer.config.ai.DeepSeekProperties;
import com.glimmer.entity.*;
import com.glimmer.mapper.*;
import com.glimmer.service.EchoService;
import com.glimmer.service.NotificationService;
import com.glimmer.service.ai.DeepSeekClient;
import com.glimmer.service.ai.DeepSeekMessage;
import com.glimmer.service.dto.CampfireMessageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 回音机器人服务实现
 * 见开发文档 §9 AI 机器人系统
 */
@Slf4j
@Service
public class EchoServiceImpl implements EchoService {

    /** Redis key: 托管开关 */
    private static final String KEY_AUTO_MODE = "echo:auto_mode";
    /** Redis key prefix: 回音对篝火的救场标记（防止重复救场），值为最后一次发言时间戳 */
    private static final String KEY_CAMPFIRE_SPOKE_PREFIX = "echo:campfire_spoke:";
    /** Redis key prefix: 回音上次发言类型（reply=回应 / topic=新话题），防止连续提新话题 */
    private static final String KEY_CAMPFIRE_LAST_TYPE_PREFIX = "echo:campfire_last_type:";
    /** Redis key prefix: 篝火静音状态（用户让回音闭嘴时设为1，呼唤回音时清除） */
    private static final String KEY_CAMPFIRE_MUTED_PREFIX = "echo:campfire_muted:";

    /** 每个漂流瓶任务最多处理数 */
    private static final int MAX_BOTTLES_PER_RUN = 20;
    /** 每次篝火任务只处理 1 个（轮询） */
    private static final int MAX_CAMPFIRES_PER_RUN = 1;
    /** 回音最近几条消息作为上下文 */
    private static final int CONTEXT_MESSAGE_COUNT = 6;

    /** 回音机器人账号：懒加载缓存，避免每次都查库 */
    private final AtomicReference<User> botUserRef = new AtomicReference<>();

    private final UserMapper userMapper;
    private final DriftBottleMapper driftBottleMapper;
    private final DriftBottleReplyMapper driftBottleReplyMapper;
    private final CampfireMapper campfireMapper;
    private final CampfireMessageMapper campfireMessageMapper;
    private final CampfireMemberMapper campfireMemberMapper;
    private final RedisUtils redis;
    private final DeepSeekClient deepSeekClient;
    private final DeepSeekProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;

    public EchoServiceImpl(UserMapper userMapper,
                           DriftBottleMapper driftBottleMapper,
                           DriftBottleReplyMapper driftBottleReplyMapper,
                           CampfireMapper campfireMapper,
                           CampfireMessageMapper campfireMessageMapper,
                           CampfireMemberMapper campfireMemberMapper,
                           RedisUtils redis,
                           DeepSeekClient deepSeekClient,
                           DeepSeekProperties properties,
                           PasswordEncoder passwordEncoder,
                           SimpMessagingTemplate messagingTemplate,
                           NotificationService notificationService) {
        this.userMapper = userMapper;
        this.driftBottleMapper = driftBottleMapper;
        this.driftBottleReplyMapper = driftBottleReplyMapper;
        this.campfireMapper = campfireMapper;
        this.campfireMessageMapper = campfireMessageMapper;
        this.campfireMemberMapper = campfireMemberMapper;
        this.redis = redis;
        this.deepSeekClient = deepSeekClient;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.messagingTemplate = messagingTemplate;
        this.notificationService = notificationService;
    }

    @Override
    public User getOrCreateBotUser() {
        User cached = botUserRef.get();
        if (cached != null) {
            return cached;
        }
        User bot = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, BOT_USERNAME));
        if (bot == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "回音机器人账号未初始化，请先执行 EchoInitializer");
        }
        botUserRef.set(bot);
        return bot;
    }

    @Override
    public boolean isAutoModeEnabled() {
        String val = redis.get(KEY_AUTO_MODE);
        return "1".equals(val);
    }

    @Override
    public void setAutoMode(boolean enabled) {
        redis.set(KEY_AUTO_MODE, enabled ? "1" : "0",
                java.time.Duration.ofDays(30));
        log.info("回音托管开关已设置: enabled={}", enabled);
    }

    @Override
    public boolean getAutoMode() {
        return isAutoModeEnabled();
    }

    @Override
    public int processPendingBottles(int limit) {
        if (!isAutoModeEnabled()) {
            log.debug("[回音漂流瓶] 托管未开启，跳过");
            return 0;
        }
        if (properties.getEchoBottlePrompt() == null || properties.getEchoBottlePrompt().isEmpty()) {
            log.warn("[回音漂流瓶] 提示词 echo-bottle-prompt 未配置，跳过");
            return 0;
        }

        User bot = getOrCreateBotUser();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));

        // 先查出回音已回复过的瓶子ID列表，用于在查询时直接排除
        List<Long> repliedBottleIds = driftBottleReplyMapper.selectList(
                new LambdaQueryWrapper<DriftBottleReply>()
                        .eq(DriftBottleReply::getUserId, bot.getId())
                        .select(DriftBottleReply::getBottleId))
                .stream()
                .map(DriftBottleReply::getBottleId)
                .collect(Collectors.toList());

        // 查询处于漂流状态的瓶子，排除回音自己扔的、回音已回复过的
        List<DriftBottle> driftingBottles = driftBottleMapper.selectList(
                new LambdaQueryWrapper<DriftBottle>()
                        .eq(DriftBottle::getStatus, "drifting")
                        .ne(DriftBottle::getUserId, bot.getId())
                        .notIn(!repliedBottleIds.isEmpty(), DriftBottle::getId, repliedBottleIds)
                        .last("ORDER BY created_at ASC LIMIT " + limit));

        // 调试：统计各状态瓶子数
        Long driftingCount = driftBottleMapper.selectCount(
                new LambdaQueryWrapper<DriftBottle>().eq(DriftBottle::getStatus, "drifting"));
        Long sunkCount = driftBottleMapper.selectCount(
                new LambdaQueryWrapper<DriftBottle>().eq(DriftBottle::getStatus, "sunk"));
        log.info("[回音漂流瓶] 数据库状态统计: drifting={}, sunk={}, botUserId={}, 回音已回复瓶子数={}",
                driftingCount, sunkCount, bot.getId(), repliedBottleIds.size());

        if (driftingBottles.isEmpty()) {
            log.info("[回音漂流瓶] 无漂流中的瓶子");
            return 0;
        }

        log.info("[回音漂流瓶] 发现 {} 个漂流中的瓶子", driftingBottles.size());

        int repliedCount = 0;
        for (DriftBottle bottle : driftingBottles) {
            try {
                log.info("[回音漂流瓶] 检查瓶子: bottleId={}, ownerUserId={}, botUserId={}, status={}",
                        bottle.getId(), bottle.getUserId(), bot.getId(), bottle.getStatus());

                // 检查瓶子总回复数（回音+其他用户）
                Long totalReplyCount = driftBottleReplyMapper.selectCount(
                        new LambdaQueryWrapper<DriftBottleReply>()
                                .eq(DriftBottleReply::getBottleId, bottle.getId()));

                // 总回复数 >= 2 → 已有足够回复，跳过
                // 注：回音已回复过的瓶子已在 SQL 查询时排除，这里无需再检查
                if (totalReplyCount != null && totalReplyCount >= 2) {
                    log.debug("[回音漂流瓶] 瓶子已有{}条回复，跳过: bottleId={}", totalReplyCount, bottle.getId());
                    continue;
                }

                log.info("[回音漂流瓶] 开始回复瓶子: bottleId={}, ownerUserId={}, totalReplyCount={}, contentLen={}",
                        bottle.getId(), bottle.getUserId(), totalReplyCount,
                        bottle.getContent() != null ? bottle.getContent().length() : 0);

                // 调用 AI 生成回复
                String prompt = properties.getEchoBottlePrompt().formatted(bottle.getContent());
                DeepSeekClient.ChatResult result = deepSeekClient.chatCompletion(
                        List.of(new DeepSeekMessage("user", prompt)));

                String aiReply = result.getContent();
                if (aiReply == null || aiReply.isBlank()) {
                    log.warn("回音 AI 回复为空，bottleId={}", bottle.getId());
                    continue;
                }

                // 保存回复
                DriftBottleReply reply = new DriftBottleReply();
                reply.setBottleId(bottle.getId());
                reply.setUserId(bot.getId());
                reply.setContent(aiReply);
                reply.setCreatedAt(now);
                driftBottleReplyMapper.insert(reply);
                repliedCount++;

                log.info("回音自动回复漂流瓶成功: bottleId={}, replyId={}, content={}",
                        bottle.getId(), reply.getId(), aiReply);

                // 通知瓶主收到回复（参考 DriftBottleServiceImpl.replyBottle 逻辑）
                if (!bot.getId().equals(bottle.getUserId())) {
                    try {
                        notificationService.sendNotification(
                                bottle.getUserId(),
                                "bottle_reply",
                                "你的漂流瓶收到了一条回复",
                                aiReply.length() > 50 ? aiReply.substring(0, 50) + "…" : aiReply,
                                "drift_bottle",
                                bottle.getId());
                        log.info("[回音漂流瓶] 通知已发送: bottleId={}, ownerUserId={}", bottle.getId(), bottle.getUserId());
                    } catch (Exception ex) {
                        log.error("[回音漂流瓶] 通知发送失败: bottleId={}, ownerUserId={}", bottle.getId(), bottle.getUserId(), ex);
                    }
                }

                if (repliedCount >= limit) {
                    break;
                }
            } catch (Exception e) {
                log.error("[回音漂流瓶] 回复瓶子异常: bottleId={}", bottle.getId(), e);
            }
        }

        return repliedCount;
    }

    @Override
    public boolean processCampfireColdspot(int coldSeconds) {
        if (!isAutoModeEnabled()) {
            return false;
        }
        boolean hasReplyPrompt = properties.getEchoCampfireReplyPrompt() != null && !properties.getEchoCampfireReplyPrompt().isEmpty();
        boolean hasTopicPrompt = properties.getEchoCampfirePrompt() != null && !properties.getEchoCampfirePrompt().isEmpty();
        if (!hasReplyPrompt && !hasTopicPrompt) {
            log.warn("[回音篝火] 提示词未配置，跳过");
            return false;
        }

        User bot = getOrCreateBotUser();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));

        // 只处理系统默认公共篝火（type=default），用户自定义篝火不接入 AI
        List<Campfire> activeCampfires = campfireMapper.selectList(
                new LambdaQueryWrapper<Campfire>()
                        .eq(Campfire::getStatus, "active")
                        .eq(Campfire::getType, "default")
                        .orderByDesc(Campfire::getLastActiveAt));

        if (activeCampfires.isEmpty()) {
            log.debug("[回音篝火] 无活跃公共篝火");
            return false;
        }

        log.info("[回音篝火] 发现 {} 个活跃公共篝火", activeCampfires.size());

        int processed = 0;
        for (Campfire campfire : activeCampfires) {
            if (processed >= MAX_CAMPFIRES_PER_RUN) {
                break;
            }

            // 统计篝火真人成员数（排除回音自己）
            Long memberCount = campfireMemberMapper.selectCount(
                    new LambdaQueryWrapper<CampfireMember>()
                            .eq(CampfireMember::getCampfireId, campfire.getId())
                            .ne(CampfireMember::getUserId, bot.getId()));
            int humanMembers = memberCount != null ? memberCount.intValue() : 0;

            // 查询最近几条消息作为上下文
            List<CampfireMessage> recentMsgs = campfireMessageMapper.selectList(
                    new LambdaQueryWrapper<CampfireMessage>()
                            .eq(CampfireMessage::getCampfireId, campfire.getId())
                            .orderByDesc(CampfireMessage::getCreatedAt)
                            .last("LIMIT " + CONTEXT_MESSAGE_COUNT));
            if (!recentMsgs.isEmpty()) {
                java.util.Collections.reverse(recentMsgs);
            }

            CampfireMessage lastMsg = recentMsgs.isEmpty() ? null : recentMsgs.get(recentMsgs.size() - 1);

            // 检查静音状态变化：扫描最近消息中是否包含闭嘴/呼唤关键词
            checkMuteStatus(campfire.getId(), recentMsgs, bot);

            // 如果被静音，跳过此篝火
            if (isCampfireMuted(campfire.getId())) {
                log.debug("[回音篝火] {} 已被用户静音，跳过", campfire.getId());
                continue;
            }

            // 决策：该不该发言，发什么类型
            String actionType = decideCampfireAction(campfire.getId(), bot, lastMsg, recentMsgs,
                    coldSeconds, now, hasReplyPrompt, hasTopicPrompt, humanMembers);
            if (actionType == null) {
                continue;
            }

            // 加入篝火（如果不是成员的话）
            CampfireMember member = campfireMemberMapper.selectOne(
                    new LambdaQueryWrapper<CampfireMember>()
                            .eq(CampfireMember::getCampfireId, campfire.getId())
                            .eq(CampfireMember::getUserId, bot.getId()));
            if (member == null) {
                member = new CampfireMember();
                member.setCampfireId(campfire.getId());
                member.setUserId(bot.getId());
                member.setAnonymousName(bot.getNickname() != null ? bot.getNickname() : "回音");
                member.setJoinedAt(now);
                campfireMemberMapper.insert(member);
            }

            // 根据类型选择提示词
            String prompt;
            if ("reply".equals(actionType)) {
                String context = buildContext(recentMsgs, bot);
                prompt = properties.getEchoCampfireReplyPrompt().formatted(context);
            } else {
                prompt = properties.getEchoCampfirePrompt().formatted(campfire.getName());
            }

            // 调用 AI
            DeepSeekClient.ChatResult result;
            try {
                result = deepSeekClient.chatCompletion(
                        List.of(new DeepSeekMessage("user", prompt)));
            } catch (Exception e) {
                log.error("[回音篝火] AI 调用失败: campfireId={}, actionType={}", campfire.getId(), actionType, e);
                continue;
            }

            String aiReply = result.getContent();
            if (aiReply == null || aiReply.isBlank()) {
                log.warn("[回音篝火] AI 回复为空: campfireId={}", campfire.getId());
                continue;
            }

            // 保存消息
            CampfireMessage message = new CampfireMessage();
            message.setCampfireId(campfire.getId());
            message.setUserId(bot.getId());
            message.setAnonymousName(member.getAnonymousName());
            message.setContent(aiReply);
            message.setCreatedAt(now);

            // 写 Redis 标记
            redis.set(KEY_CAMPFIRE_SPOKE_PREFIX + campfire.getId(),
                    now.toString(), java.time.Duration.ofMinutes(30));
            redis.set(KEY_CAMPFIRE_LAST_TYPE_PREFIX + campfire.getId(),
                    actionType, java.time.Duration.ofMinutes(30));

            campfireMessageMapper.insert(message);

            // WebSocket 推送
            CampfireMessageVO vo = new CampfireMessageVO();
            vo.setId(message.getId());
            vo.setCampfireId(message.getCampfireId());
            vo.setUserId(message.getUserId());
            vo.setAnonymousName(message.getAnonymousName());
            vo.setContent(message.getContent());
            vo.setCreatedAt(message.getCreatedAt());
            vo.setIsFromBot(true);
            messagingTemplate.convertAndSend("/topic/campfire/" + campfire.getId(), vo);

            // 更新篝火活跃时间
            campfireMapper.update(null, new LambdaUpdateWrapper<Campfire>()
                    .eq(Campfire::getId, campfire.getId())
                    .set(Campfire::getLastActiveAt, now));

            log.info("回音篝火[{}]: campfireId={}, humanMembers={}, content={}",
                    "reply".equals(actionType) ? "回应" : "话题",
                    campfire.getId(), humanMembers, aiReply);
            processed++;
        }

        return processed > 0;
    }

    /** 检查是否被静音 */
    private boolean isCampfireMuted(Long campfireId) {
        return "1".equals(redis.get(KEY_CAMPFIRE_MUTED_PREFIX + campfireId));
    }

    /** 静音关键词 */
    private static final String[] MUTE_KEYWORDS = {"闭嘴", "安静", "别说了", "别说话", "嘘", "闭麦", "你能不能别说了"};
    /** 呼唤关键词 */
    private static final String[] UNMUTE_KEYWORDS = {"回音", "echo", "Echo", "ECHO"};

    /**
     * 扫描最近消息，检查是否包含静音/呼唤关键词tt
     * 只检查回音上次发言之后的真人消息
     */
    private void checkMuteStatus(Long campfireId, List<CampfireMessage> recentMsgs, User bot) {
        if (recentMsgs == null || recentMsgs.isEmpty()) return;

        // 获取回音上次发言时间
        String lastSpokeTime = redis.get(KEY_CAMPFIRE_SPOKE_PREFIX + campfireId);
        LocalDateTime spokeAt = null;
        if (lastSpokeTime != null) {
            try {
                spokeAt = LocalDateTime.parse(lastSpokeTime);
            } catch (Exception e) {
                // 忽略解析失败
            }
        }

        // 从最新消息往前扫描，找最近一条真人消息
        for (int i = recentMsgs.size() - 1; i >= 0; i--) {
            CampfireMessage msg = recentMsgs.get(i);
            // 跳过回音自己的消息
            if (msg.getUserId().equals(bot.getId())) continue;
            // 如果有回音发言时间，只检查之后的真人消息
            if (spokeAt != null && msg.getCreatedAt().isBefore(spokeAt)) break;

            String content = msg.getContent();
            if (content == null) continue;

            // 检查呼唤关键词（优先级高，解除静音）
            for (String kw : UNMUTE_KEYWORDS) {
                if (content.contains(kw)) {
                    redis.delete(KEY_CAMPFIRE_MUTED_PREFIX + campfireId);
                    log.info("[回音篝火] {} 用户呼唤回音，解除静音", campfireId);
                    return;
                }
            }

            // 检查静音关键词
            for (String kw : MUTE_KEYWORDS) {
                if (content.contains(kw)) {
                    redis.set(KEY_CAMPFIRE_MUTED_PREFIX + campfireId, "1",
                            java.time.Duration.ofHours(1));
                    log.info("[回音篝火] {} 用户让回音闭嘴，进入静音", campfireId);
                    return;
                }
            }
        }
    }

    /**
     * 决策回音是否该发言、发什么类型
     * 新规则：
     * - 无消息 → 提新话题（仅当有人在线时）
     * - 最后一条是真人发言：
     *   - 只有1人在线 → 直接回应（延迟3秒）
     *   - 多人在线但3分钟内只有同一人发言 → 回应
     *   - 多人在线且3分钟内有多人发言 → 不接话
     * - 最后一条是回音发言：
     *   - 无人回应超过15分钟 → 提新话题
     *   - 否则不发言
     */
    private String decideCampfireAction(Long campfireId, User bot, CampfireMessage lastMsg,
                                        List<CampfireMessage> recentMsgs,
                                        int coldSeconds, LocalDateTime now,
                                        boolean hasReplyPrompt, boolean hasTopicPrompt,
                                        int humanMembers) {
        // 无人在线 → 不发言
        if (humanMembers == 0) {
            return null;
        }

        // 无消息 → 提新话题
        if (lastMsg == null) {
            log.debug("[回音篝火] {} 无消息但有人在线，提新话题", campfireId);
            return hasTopicPrompt ? "topic" : null;
        }

        boolean lastIsBot = lastMsg.getUserId().equals(bot.getId());

        if (!lastIsBot) {
            // 最后一条是真人发言
            LocalDateTime threeMinAgo = now.minusMinutes(3);

            if (humanMembers == 1) {
                // 只有1人在线 → 直接回应（检查3秒延迟，由定时任务10秒轮询保证）
                if (lastMsg.getCreatedAt().isAfter(now.minusSeconds(3))) {
                    return null; // 3秒内，等一下
                }
                log.debug("[回音篝火] {} 仅1人在线，回应", campfireId);
                return hasReplyPrompt ? "reply" : (hasTopicPrompt ? "topic" : null);
            }

            // 多人在线：检查3分钟内是否只有同一个用户发言
            List<CampfireMessage> recent3Min = campfireMessageMapper.selectList(
                    new LambdaQueryWrapper<CampfireMessage>()
                            .eq(CampfireMessage::getCampfireId, campfireId)
                            .gt(CampfireMessage::getCreatedAt, threeMinAgo)
                            .ne(CampfireMessage::getUserId, bot.getId())
                            .orderByDesc(CampfireMessage::getCreatedAt));

            if (recent3Min.isEmpty()) {
                // 3分钟内无真人发言（可能消息在3分钟前），检查冷场
                if (lastMsg.getCreatedAt().isBefore(now.minusSeconds(coldSeconds))) {
                    log.debug("[回音篝火] {} 冷场超过{}秒，提新话题", campfireId, coldSeconds);
                    return hasTopicPrompt ? "topic" : null;
                }
                return null;
            }

            // 检查3分钟内是否只有同一个用户发言
            long distinctUsers = recent3Min.stream()
                    .map(CampfireMessage::getUserId)
                    .distinct()
                    .count();
            if (distinctUsers <= 1) {
                // 3分钟内只有同一人发言 → 回应
                if (lastMsg.getCreatedAt().isAfter(now.minusSeconds(3))) {
                    return null; // 3秒延迟
                }
                log.debug("[回音篝火] {} 多人在线但3分钟内只有同一人发言，回应", campfireId);
                return hasReplyPrompt ? "reply" : (hasTopicPrompt ? "topic" : null);
            }

            // 多人活跃交流中 → 不接话
            return null;
        }

        // 最后一条是回音发言 → 不发言，避免回音自言自语进入循环
        log.debug("[回音篝火] {} 最后一条是回音消息，不发言避免循环", campfireId);
        return null;
    }

    /**
     * 构建篝火对话上下文（用于回应提示词）
     */
    private String buildContext(List<CampfireMessage> msgs, User bot) {
        if (msgs == null || msgs.isEmpty()) {
            return "（暂无对话）";
        }
        StringBuilder sb = new StringBuilder();
        for (CampfireMessage m : msgs) {
            String speaker = m.getUserId().equals(bot.getId()) ? "回音" : m.getAnonymousName();
            sb.append(speaker).append("：").append(m.getContent()).append("\n");
        }
        return sb.toString().trim();
    }
}
