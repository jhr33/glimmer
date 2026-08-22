package com.glimmer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.response.PageResult;
import com.glimmer.common.util.BannedWordFilterService;
import com.glimmer.common.util.RedisUtils;
import com.glimmer.common.util.TokenBalanceHelper;
import com.glimmer.entity.Campfire;
import com.glimmer.entity.CampfireMember;
import com.glimmer.entity.CampfireMessage;
import com.glimmer.entity.TokenTransaction;
import com.glimmer.entity.User;
import com.glimmer.mapper.CampfireMapper;
import com.glimmer.mapper.CampfireMemberMapper;
import com.glimmer.mapper.CampfireMessageMapper;
import com.glimmer.mapper.ReportMapper;
import com.glimmer.mapper.TokenTransactionMapper;
import com.glimmer.mapper.UserMapper;
import com.glimmer.common.util.AnonymousNameGenerator;
import com.glimmer.service.CampfireService;
import com.glimmer.service.UserService;
import com.glimmer.service.dto.CampfireMessageVO;
import com.glimmer.service.dto.CampfireMemberVO;
import com.glimmer.service.dto.CampfireVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 篝火服务实现
 * 见开发文档 §2.5 / §4.8
 */
@Slf4j
@Service
public class CampfireServiceImpl implements CampfireService {

    /** 篝火人数上限与代币消耗映射（10→1, 20→2, 30→3） */
    private static final Map<Integer, Integer> MAX_MEMBERS_TOKEN_COST = Map.of(10, 1, 20, 2, 30, 3);

    /** 缓存 key：篝火成员身份名 campfire:member:{campfireId}:{userId} → anonymousName，TTL 30 分钟 */
    private static final String CACHE_KEY_MEMBER = "campfire:member:%d:%d";
    private static final Duration MEMBER_CACHE_TTL = Duration.ofMinutes(30);

    private final CampfireMapper campfireMapper;
    private final CampfireMemberMapper campfireMemberMapper;
    private final CampfireMessageMapper campfireMessageMapper;
    private final UserMapper userMapper;
    private final TokenTransactionMapper tokenTransactionMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserService userService;
    private final ReportMapper reportMapper;
    private final RedisUtils redis;
    private final TokenBalanceHelper tokenBalanceHelper;
    private final BannedWordFilterService bannedWordFilterService;

    public CampfireServiceImpl(CampfireMapper campfireMapper,
                               CampfireMemberMapper campfireMemberMapper,
                               CampfireMessageMapper campfireMessageMapper,
                               UserMapper userMapper,
                               TokenTransactionMapper tokenTransactionMapper,
                               SimpMessagingTemplate messagingTemplate,
                               UserService userService,
                               ReportMapper reportMapper,
                               RedisUtils redis,
                               TokenBalanceHelper tokenBalanceHelper,
                               BannedWordFilterService bannedWordFilterService) {
        this.campfireMapper = campfireMapper;
        this.campfireMemberMapper = campfireMemberMapper;
        this.campfireMessageMapper = campfireMessageMapper;
        this.userMapper = userMapper;
        this.tokenTransactionMapper = tokenTransactionMapper;
        this.messagingTemplate = messagingTemplate;
        this.userService = userService;
        this.reportMapper = reportMapper;
        this.redis = redis;
        this.tokenBalanceHelper = tokenBalanceHelper;
        this.bannedWordFilterService = bannedWordFilterService;
    }

    /**
     * 缓存成员身份名
     */
    private void cacheMemberName(Long campfireId, Long userId, String anonymousName) {
        if (anonymousName != null && !anonymousName.isEmpty()) {
            redis.set(String.format(CACHE_KEY_MEMBER, campfireId, userId), anonymousName, MEMBER_CACHE_TTL);
        }
    }

    /**
     * 读取缓存的成员身份名（未命中返回 null）
     */
    private String getCachedMemberName(Long campfireId, Long userId) {
        return redis.get(String.format(CACHE_KEY_MEMBER, campfireId, userId));
    }

    /**
     * 清除成员身份名缓存
     */
    private void evictMemberNameCache(Long campfireId, Long userId) {
        redis.delete(String.format(CACHE_KEY_MEMBER, campfireId, userId));
    }

    @Override
    public List<CampfireVO> getCampfireList(Long userId) {
        LambdaQueryWrapper<Campfire> wrapper = new LambdaQueryWrapper<Campfire>()
                .eq(Campfire::getStatus, "active")
                .orderByDesc(Campfire::getCreatedAt);
        List<Campfire> campfires = campfireMapper.selectList(wrapper);

        Map<Long, Long> memberCountMap = countMembers(campfires);

        return campfires.stream().map(c -> toVO(c, memberCountMap.getOrDefault(c.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampfireVO createCampfire(Long userId, String name, int maxMembers) {
        // 1. 校验用户非 banned
        userService.checkUserNotMuted(userId);
        // 2. 违禁词检测
        bannedWordFilterService.check(name, "createCampfire");

        // 2. 校验 maxMembers ∈ {10, 20, 30}
        Integer tokenCost = MAX_MEMBERS_TOKEN_COST.get(maxMembers);
        if (tokenCost == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "人数上限仅支持 10/20/30");
        }

        // 3. 扣代币（分布式锁 + 余额校验 + 乐观锁兜底，返回更新后的用户用于后续身份解析）
        User user = tokenBalanceHelper.deduct(userId, tokenCost);

        // 4. 插入 campfire
        LocalDateTime now = LocalDateTime.now();
        Campfire campfire = new Campfire();
        campfire.setName(name);
        campfire.setType("custom");
        campfire.setMaxMembers(maxMembers);
        campfire.setCreatorId(userId);
        campfire.setStatus("active");
        campfire.setCreatedAt(now);
        campfire.setLastActiveAt(now);
        campfireMapper.insert(campfire);

        // 5. 创建者自动加入（默认使用昵称）
        CampfireMember member = new CampfireMember();
        member.setCampfireId(campfire.getId());
        member.setUserId(userId);
        member.setAnonymousName(resolveIdentityName(user, campfire.getId(), "nickname"));
        member.setJoinedAt(now);
        campfireMemberMapper.insert(member);

        // 6. 写流水：source='create_campfire'
        TokenTransaction tx = new TokenTransaction();
        tx.setUserId(userId);
        tx.setType("spend");
        tx.setAmount(tokenCost);
        tx.setSource("create_campfire");
        tx.setRefId(campfire.getId());
        tokenTransactionMapper.insert(tx);

        log.info("创建篝火成功: userId={}, campfireId={}, maxMembers={}, cost={}",
                userId, campfire.getId(), maxMembers, tokenCost);

        CampfireVO vo = toVO(campfire, 1L);
        return vo;
    }

    @Override
    public CampfireVO getCampfireDetail(Long userId, Long campfireId) {
        Campfire campfire = campfireMapper.selectById(campfireId);
        if (campfire == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "篝火不存在");
        }
        Long memberCount = countMembers(campfireId);
        return toVO(campfire, memberCount);
    }

    @Override
    public PageResult<CampfireMessageVO> getHistoryMessages(Long userId, Long campfireId, int page, int size) {
        Campfire campfire = campfireMapper.selectById(campfireId);
        if (campfire == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "篝火不存在");
        }
        // 游客模式（userId=null）跳过成员校验，仅围观
        if (userId != null) {
            checkCampfireMember(userId, campfireId);
        }

        List<Long> bannedMessageIds = reportMapper.selectApprovedTargetIds("campfire_message");
        LocalDateTime threeDaysAgo = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusDays(3);

        // 查询3天内的消息总数
        Long totalCount = campfireMessageMapper.selectCount(new LambdaQueryWrapper<CampfireMessage>()
                .eq(CampfireMessage::getCampfireId, campfireId)
                .ge(CampfireMessage::getCreatedAt, threeDaysAgo)
                .notIn(bannedMessageIds != null && !bannedMessageIds.isEmpty(), CampfireMessage::getId, bannedMessageIds));

        int pageSize = Math.min(size, 100);
        // 最多10页，每页100条 = 1000条
        int maxPages = 10;
        int requestedPage = page;
        
        // 如果请求的页码超过范围，返回空
        if (requestedPage > maxPages) {
            return new PageResult<>(java.util.Collections.emptyList(), Math.min(totalCount, 1000), pageSize, requestedPage);
        }

        // 按时间倒序查询（最新在前），page=1是最新的100条
        LambdaQueryWrapper<CampfireMessage> wrapper = new LambdaQueryWrapper<CampfireMessage>()
                .eq(CampfireMessage::getCampfireId, campfireId)
                .ge(CampfireMessage::getCreatedAt, threeDaysAgo)
                .notIn(bannedMessageIds != null && !bannedMessageIds.isEmpty(), CampfireMessage::getId, bannedMessageIds)
                .orderByDesc(CampfireMessage::getCreatedAt);

        // 用OFFSET限制，最多1000条
        long offset = (long)(requestedPage - 1) * pageSize;
        if (totalCount > 1000) {
            offset = totalCount - 1000 + (long)(requestedPage - 1) * pageSize;
            if (offset < 0) offset = 0;
        }
        wrapper.last("LIMIT " + pageSize + " OFFSET " + offset);

        List<CampfireMessage> records = campfireMessageMapper.selectList(wrapper);
        // 转为正序（旧→新，符合聊天显示习惯）
        java.util.Collections.reverse(records);
        List<CampfireMessageVO> list = records.stream()
                .map(this::toMessageVO).collect(Collectors.toList());
        fillIsFromBotForMessages(list, records);
        fillQuotedInfo(list, records);
        
        long effectiveTotal = Math.min(totalCount, 1000);
        return new PageResult<>(list, effectiveTotal, pageSize, requestedPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampfireMemberVO joinCampfire(Long userId, Long campfireId, String displayMode) {
        Campfire campfire = campfireMapper.selectById(campfireId);
        if (campfire == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "篝火不存在");
        }
        // 校验状态 active
        if (!"active".equals(campfire.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "篝火不可加入");
        }

        // 获取用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        // 根据 displayMode 生成身份名称
        String identityName = resolveIdentityName(user, campfireId, displayMode);
        CampfireMember member;

        LocalDateTime now = LocalDateTime.now();
        
        if (isMember(userId, campfireId)) {
            // 已加入则更新身份名称和最后活跃时间
            campfireMemberMapper.update(null, new LambdaUpdateWrapper<CampfireMember>()
                    .eq(CampfireMember::getCampfireId, campfireId)
                    .eq(CampfireMember::getUserId, userId)
                    .set(CampfireMember::getAnonymousName, identityName)
                    .set(CampfireMember::getLastActiveAt, now));
            // 读取更新后的成员
            member = campfireMemberMapper.selectOne(new LambdaQueryWrapper<CampfireMember>()
                    .eq(CampfireMember::getCampfireId, campfireId)
                    .eq(CampfireMember::getUserId, userId));
            log.info("用户已加入篝火，更新身份: userId={}, campfireId={}, anonymousName={}", userId, campfireId, identityName);
        } else {
            // 校验人数未满
            Long memberCount = countMembers(campfireId);
            if (campfire.getMaxMembers() != null && memberCount >= campfire.getMaxMembers()) {
                throw new BusinessException(ErrorCode.CAMPFIRE_FULL);
            }
            // 插入成员
            member = new CampfireMember();
            member.setCampfireId(campfireId);
            member.setUserId(userId);
            member.setAnonymousName(identityName);
            member.setJoinedAt(now);
            member.setLastActiveAt(now);
            campfireMemberMapper.insert(member);
            log.info("加入篝火成功: userId={}, campfireId={}, anonymousName={}", userId, campfireId, identityName);
        }

        // 更新最后活跃时间
        campfire.setLastActiveAt(LocalDateTime.now());
        campfireMapper.updateById(campfire);

        // 缓存成员身份名（sendMessage 时直接读缓存，避免查 DB）
        cacheMemberName(campfireId, userId, identityName);

        return toMemberVO(member);
    }

    private CampfireMemberVO toMemberVO(CampfireMember member) {
        CampfireMemberVO vo = new CampfireMemberVO();
        vo.setId(member.getId());
        vo.setCampfireId(member.getCampfireId());
        vo.setUserId(member.getUserId());
        vo.setAnonymousName(member.getAnonymousName());
        vo.setJoinedAt(member.getJoinedAt());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void leaveCampfire(Long userId, Long campfireId) {
        Campfire campfire = campfireMapper.selectById(campfireId);
        if (campfire == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "篝火不存在");
        }
        // 删除成员
        int deleted = campfireMemberMapper.delete(new LambdaQueryWrapper<CampfireMember>()
                .eq(CampfireMember::getCampfireId, campfireId)
                .eq(CampfireMember::getUserId, userId));
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未加入该篝火");
        }
        
        // 更新最后活跃时间
        campfire.setLastActiveAt(LocalDateTime.now());
        campfireMapper.updateById(campfire);

        // 清除成员身份名缓存
        evictMemberNameCache(campfireId, userId);

        log.info("退出篝火成功: userId={}, campfireId={}", userId, campfireId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void extinguishCampfire(Long userId, Long campfireId) {
        Campfire campfire = campfireMapper.selectById(campfireId);
        if (campfire == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "篝火不存在");
        }
        if (!userId.equals(campfire.getCreatorId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅创建者可熄灭篝火");
        }
        if ("default".equals(campfire.getType())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "系统默认篝火不可熄灭");
        }
        if (!"active".equals(campfire.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "篝火状态异常");
        }
        campfire.setStatus("extinguished");
        campfireMapper.updateById(campfire);
        campfireMemberMapper.delete(new LambdaQueryWrapper<CampfireMember>()
                .eq(CampfireMember::getCampfireId, campfireId));
        log.info("篝火已熄灭: userId={}, campfireId={}", userId, campfireId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampfireMessageVO sendMessage(Long userId, Long campfireId, String content, Long quotedMessageId) {
        // 1. 校验用户非 banned
        userService.checkUserNotMuted(userId);
        // 2. 违禁词检测
        bannedWordFilterService.check(content, "campfireMessage");
        // 3. 校验用户是该篝火成员
        CampfireMember member = checkCampfireMember(userId, campfireId);
        // 4. 获取用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        // 5. 确定身份名称
        String identityName = getCachedMemberName(campfireId, userId);
        if (identityName == null || identityName.isEmpty()) {
            identityName = member.getAnonymousName();
        }
        if (identityName == null || identityName.isEmpty()) {
            identityName = (user.getNickname() != null && !user.getNickname().isEmpty())
                    ? user.getNickname()
                    : AnonymousNameGenerator.generateStable(userId, campfireId);
            campfireMemberMapper.update(null, new LambdaUpdateWrapper<CampfireMember>()
                    .eq(CampfireMember::getId, member.getId())
                    .set(CampfireMember::getAnonymousName, identityName));
            cacheMemberName(campfireId, userId, identityName);
        }

        // 6. 如果有引用消息，获取引用内容
        String quotedContent = null;
        if (quotedMessageId != null) {
            CampfireMessage quotedMsg = campfireMessageMapper.selectById(quotedMessageId);
            if (quotedMsg != null) {
                // 校验引用消息属于同一篝火
                if (!quotedMsg.getCampfireId().equals(campfireId)) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR, "引用的消息不属于当前篝火");
                }
                quotedContent = quotedMsg.getContent();
                // 限制引用内容长度
                if (quotedContent != null && quotedContent.length() > 100) {
                    quotedContent = quotedContent.substring(0, 100) + "...";
                }
            }
        }

        // 7. 插入消息
        LocalDateTime now = LocalDateTime.now();
        CampfireMessage message = new CampfireMessage();
        message.setCampfireId(campfireId);
        message.setUserId(userId);
        message.setAnonymousName(identityName);
        message.setContent(content);
        message.setQuotedMessageId(quotedMessageId);
        message.setQuotedContent(quotedContent);
        message.setCreatedAt(now);
        campfireMessageMapper.insert(message);

        // 更新成员最后活跃时间
        updateMemberLastActive(userId, campfireId, now);

        CampfireMessageVO vo = toMessageVO(message);
        // 设置引用消息的发送者昵称
        if (quotedMessageId != null) {
            CampfireMessage quotedMsg = campfireMessageMapper.selectById(quotedMessageId);
            if (quotedMsg != null) {
                vo.setQuotedAnonymousName(quotedMsg.getAnonymousName());
            }
        }
        vo.setIsFromBot(Boolean.TRUE.equals(selectBotUserIdSet(Set.of(userId)).contains(userId)));

        // 8. 通过 WebSocket 推送
        messagingTemplate.convertAndSend("/topic/campfire/" + campfireId, vo);

        log.info("篝火消息发送成功: userId={}, campfireId={}, messageId={}, quotedMessageId={}", userId, campfireId, message.getId(), quotedMessageId);
        return vo;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 校验当前用户是该篝火成员，返回成员记录
     */
    private CampfireMember checkCampfireMember(Long userId, Long campfireId) {
        CampfireMember member = campfireMemberMapper.selectOne(
                new LambdaQueryWrapper<CampfireMember>()
                        .eq(CampfireMember::getCampfireId, campfireId)
                        .eq(CampfireMember::getUserId, userId));
        if (member == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "未加入该篝火");
        }
        return member;
    }

    /**
     * 更新成员最后活跃时间
     */
    private void updateMemberLastActive(Long userId, Long campfireId, LocalDateTime time) {
        campfireMemberMapper.update(null, new LambdaUpdateWrapper<CampfireMember>()
                .eq(CampfireMember::getCampfireId, campfireId)
                .eq(CampfireMember::getUserId, userId)
                .set(CampfireMember::getLastActiveAt, time));
    }

    /**
     * 根据 displayMode 解析身份名称
     * - "nickname"：按 userId 查询用户 nickname
     * - "anonymous"：同一用户+同一篝火+同一天 = 同一稳定名称
     * - 默认：按 userId 查询用户 nickname
     */
    private String resolveIdentityName(User user, Long campfireId, String displayMode) {
        if ("anonymous".equals(displayMode)) {
            return AnonymousNameGenerator.generateStable(user.getId(), campfireId);
        }
        // nickname 模式或默认
        if (user.getNickname() != null && !user.getNickname().isEmpty()) {
            return user.getNickname();
        }
        // 未设置昵称，fallback 到稳定匿名
        return AnonymousNameGenerator.generateStable(user.getId(), campfireId);
    }

    /**
     * 判断当前用户是否是该篝火成员
     */
    private boolean isMember(Long userId, Long campfireId) {
        Long count = campfireMemberMapper.selectCount(
                new LambdaQueryWrapper<CampfireMember>()
                        .eq(CampfireMember::getCampfireId, campfireId)
                        .eq(CampfireMember::getUserId, userId));
        return count != null && count > 0;
    }

    /**
     * 统计单个篝火成员数
     */
    private Long countMembers(Long campfireId) {
        return campfireMemberMapper.selectCount(new LambdaQueryWrapper<CampfireMember>()
                .eq(CampfireMember::getCampfireId, campfireId));
    }

    /**
     * 批量统计多个篝火成员数
     */
    private Map<Long, Long> countMembers(List<Campfire> campfires) {
        Map<Long, Long> result = new HashMap<>();
        if (campfires == null || campfires.isEmpty()) {
            return result;
        }
        List<Long> ids = campfires.stream().map(Campfire::getId).collect(Collectors.toList());
        List<CampfireMember> members = campfireMemberMapper.selectList(
                new LambdaQueryWrapper<CampfireMember>()
                        .in(CampfireMember::getCampfireId, ids));
        for (CampfireMember m : members) {
            result.merge(m.getCampfireId(), 1L, Long::sum);
        }
        return result;
    }

    private CampfireVO toVO(Campfire campfire, Long memberCount) {
        CampfireVO vo = new CampfireVO();
        vo.setId(campfire.getId());
        vo.setName(campfire.getName());
        vo.setType(campfire.getType());
        vo.setMaxMembers(campfire.getMaxMembers());
        vo.setCreatorId(campfire.getCreatorId());
        vo.setStatus(campfire.getStatus());
        vo.setCreatedAt(campfire.getCreatedAt());
        vo.setMemberCount(memberCount);
        return vo;
    }

    private CampfireMessageVO toMessageVO(CampfireMessage message) {
        CampfireMessageVO vo = new CampfireMessageVO();
        vo.setId(message.getId());
        vo.setCampfireId(message.getCampfireId());
        vo.setUserId(message.getUserId());
        vo.setAnonymousName(message.getAnonymousName());
        vo.setContent(message.getContent());
        vo.setCreatedAt(message.getCreatedAt());
        vo.setQuotedMessageId(message.getQuotedMessageId());
        vo.setQuotedContent(message.getQuotedContent());
        return vo;
    }

    /**
     * 批量给消息 VO 填充 isFromBot（避免 N+1 查用户）
     *
     * @param list     要填充的 VO 列表
     * @param messages 原始消息实体列表（来源与 list 索引一一对应）
     */
    private void fillIsFromBotForMessages(List<CampfireMessageVO> list, List<CampfireMessage> messages) {
        if (list == null || list.isEmpty()) return;
        Set<Long> userIds = messages.stream()
                .map(CampfireMessage::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Set<Long> botIds = selectBotUserIdSet(userIds);
        for (int i = 0; i < list.size() && i < messages.size(); i++) {
            Long uid = messages.get(i).getUserId();
            list.get(i).setIsFromBot(uid != null && botIds.contains(uid));
        }
    }

    /**
     * 批量填充引用消息的发送者昵称
     */
    private void fillQuotedInfo(List<CampfireMessageVO> list, List<CampfireMessage> messages) {
        if (list == null || list.isEmpty()) return;

        // 收集所有被引用的消息ID
        Set<Long> quotedIds = new HashSet<>();
        for (CampfireMessage msg : messages) {
            if (msg.getQuotedMessageId() != null) {
                quotedIds.add(msg.getQuotedMessageId());
            }
        }

        // 批量查询被引用消息的发送者昵称
        Map<Long, String> quotedNames = new HashMap<>();
        if (!quotedIds.isEmpty()) {
            List<CampfireMessage> quotedMsgs = campfireMessageMapper.selectBatchIds(quotedIds);
            for (CampfireMessage qm : quotedMsgs) {
                quotedNames.put(qm.getId(), qm.getAnonymousName());
            }
        }

        // 填充引用昵称
        for (int i = 0; i < list.size() && i < messages.size(); i++) {
            CampfireMessageVO vo = list.get(i);
            CampfireMessage msg = messages.get(i);

            // 填充引用消息昵称
            if (msg.getQuotedMessageId() != null) {
                vo.setQuotedAnonymousName(quotedNames.get(msg.getQuotedMessageId()));
            }
        }
    }

    /**
     * 从给定用户ID集合里过滤出「AI 机器人（回音）」的用户ID集合。
     * 判断条件：username == bot_echo 或 role == bot。
     */
    private Set<Long> selectBotUserIdSet(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        return userMapper.selectBatchIds(new HashSet<>(userIds)).stream()
                .filter(u -> "bot_echo".equals(u.getUsername()) || "bot".equals(u.getRole()))
                .map(User::getId)
                .collect(Collectors.toSet());
    }
}
