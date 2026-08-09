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
import java.util.List;
import java.util.Map;
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
        // 校验用户是该篝火成员
        checkCampfireMember(userId, campfireId);

        List<Long> bannedMessageIds = reportMapper.selectApprovedTargetIds("campfire_message");

        // 篝火聊天记录超过 24 小时前端不显示（数据库不删除，保留审计/申诉取证）
        // 管理员通过 selectById 查单条审核申诉时不受此限制
        LocalDateTime threshold = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusHours(24);

        Page<CampfireMessage> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<CampfireMessage> wrapper = new LambdaQueryWrapper<CampfireMessage>()
                .eq(CampfireMessage::getCampfireId, campfireId)
                .ge(CampfireMessage::getCreatedAt, threshold)
                .notIn(bannedMessageIds != null && !bannedMessageIds.isEmpty(), CampfireMessage::getId, bannedMessageIds)
                .orderByAsc(CampfireMessage::getCreatedAt);
        IPage<CampfireMessage> result = campfireMessageMapper.selectPage(pageParam, wrapper);
        List<CampfireMessageVO> list = result.getRecords().stream()
                .map(this::toMessageVO).collect(Collectors.toList());
        return new PageResult<>(list, result.getTotal(), page, size);
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

        if (isMember(userId, campfireId)) {
            // 已加入则更新身份名称（允许用户在每次进入时更换）
            campfireMemberMapper.update(null, new LambdaUpdateWrapper<CampfireMember>()
                    .eq(CampfireMember::getCampfireId, campfireId)
                    .eq(CampfireMember::getUserId, userId)
                    .set(CampfireMember::getAnonymousName, identityName));
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
            member.setJoinedAt(LocalDateTime.now());
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
    public CampfireMessageVO sendMessage(Long userId, Long campfireId, String content) {
        // 1. 校验用户非 banned
        userService.checkUserNotMuted(userId);
        // 2. 违禁词检测
        bannedWordFilterService.check(content, "campfireMessage");
        // 2. 校验用户是该篝火成员
        CampfireMember member = checkCampfireMember(userId, campfireId);
        // 3. 获取用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        // 4. 确定身份名称：优先用 Redis 缓存 → 成员的 anonymousName → fallback 生成
        String identityName = getCachedMemberName(campfireId, userId);
        if (identityName == null || identityName.isEmpty()) {
            identityName = member.getAnonymousName();
        }
        if (identityName == null || identityName.isEmpty()) {
            identityName = (user.getNickname() != null && !user.getNickname().isEmpty())
                    ? user.getNickname()
                    : AnonymousNameGenerator.generateStable(userId, campfireId);
            // 回填到成员记录并缓存
            campfireMemberMapper.update(null, new LambdaUpdateWrapper<CampfireMember>()
                    .eq(CampfireMember::getId, member.getId())
                    .set(CampfireMember::getAnonymousName, identityName));
            cacheMemberName(campfireId, userId, identityName);
        }
        // 5. 插入消息
        LocalDateTime now = LocalDateTime.now();
        CampfireMessage message = new CampfireMessage();
        message.setCampfireId(campfireId);
        message.setUserId(userId);
        message.setAnonymousName(identityName);
        message.setContent(content);
        message.setCreatedAt(now);
        campfireMessageMapper.insert(message);

        CampfireMessageVO vo = toMessageVO(message);

        // 6. 通过 WebSocket 推送到 /topic/campfire/{campfireId}
        messagingTemplate.convertAndSend("/topic/campfire/" + campfireId, vo);

        log.info("篝火消息发送成功: userId={}, campfireId={}, messageId={}, anonymousName={}", userId, campfireId, message.getId(), identityName);
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
        return vo;
    }
}
