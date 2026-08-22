package com.glimmer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.response.PageResult;
import com.glimmer.common.util.BannedWordFilterService;
import com.glimmer.common.util.TokenBalanceHelper;
import com.glimmer.entity.DriftBottle;
import com.glimmer.entity.DriftBottlePickRecord;
import com.glimmer.entity.DriftBottleReply;
import com.glimmer.entity.TokenTransaction;
import com.glimmer.entity.User;
import com.glimmer.mapper.DriftBottleMapper;
import com.glimmer.mapper.DriftBottlePickRecordMapper;
import com.glimmer.mapper.DriftBottleReplyMapper;
import com.glimmer.mapper.ReportMapper;
import com.glimmer.mapper.TokenTransactionMapper;
import com.glimmer.mapper.UserMapper;
import com.glimmer.service.DriftBottleService;
import com.glimmer.service.NotificationService;
import com.glimmer.service.UserService;
import com.glimmer.service.dto.BottlePickVO;
import com.glimmer.service.dto.BottleReplyVO;
import com.glimmer.service.dto.BottleSummaryVO;
import com.glimmer.service.dto.BottleVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 漂流瓶服务实现
 * 见开发文档 §2.3
 */
@Slf4j
@Service
public class DriftBottleServiceImpl implements DriftBottleService {

    private final DriftBottleMapper driftBottleMapper;
    private final DriftBottleReplyMapper driftBottleReplyMapper;
    private final DriftBottlePickRecordMapper driftBottlePickRecordMapper;
    private final UserMapper userMapper;
    private final TokenTransactionMapper tokenTransactionMapper;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final UserService userService;
    private final ReportMapper reportMapper;
    private final TokenBalanceHelper tokenBalanceHelper;
    private final BannedWordFilterService bannedWordFilterService;

    public DriftBottleServiceImpl(DriftBottleMapper driftBottleMapper,
                                  DriftBottleReplyMapper driftBottleReplyMapper,
                                  DriftBottlePickRecordMapper driftBottlePickRecordMapper,
                                  UserMapper userMapper,
                                  TokenTransactionMapper tokenTransactionMapper,
                                  ObjectMapper objectMapper,
                                  NotificationService notificationService,
                                  UserService userService,
                                  ReportMapper reportMapper,
                                  TokenBalanceHelper tokenBalanceHelper,
                                  BannedWordFilterService bannedWordFilterService) {
        this.driftBottleMapper = driftBottleMapper;
        this.driftBottleReplyMapper = driftBottleReplyMapper;
        this.driftBottlePickRecordMapper = driftBottlePickRecordMapper;
        this.userMapper = userMapper;
        this.tokenTransactionMapper = tokenTransactionMapper;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.userService = userService;
        this.reportMapper = reportMapper;
        this.tokenBalanceHelper = tokenBalanceHelper;
        this.bannedWordFilterService = bannedWordFilterService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void throwBottle(Long userId, String content) {
        userService.checkUserNotMuted(userId);
        bannedWordFilterService.check(content, "throwBottle");
        DriftBottle bottle = new DriftBottle();
        bottle.setUserId(userId);
        bottle.setContent(content);
        bottle.setStatus("drifting");
        driftBottleMapper.insert(bottle);
        log.info("漂流瓶扔出成功: userId={}, bottleId={}", userId, bottle.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BottlePickVO pickBottle(Long userId, String mode) {
        userService.checkUserNotMuted(userId);

        // 查询用户已捡过的瓶子ID列表
        List<DriftBottlePickRecord> pickedRecords = driftBottlePickRecordMapper.selectList(
                new LambdaQueryWrapper<DriftBottlePickRecord>()
                        .eq(DriftBottlePickRecord::getUserId, userId)
                        .select(DriftBottlePickRecord::getBottleId));
        List<Long> pickedBottleIds = pickedRecords.stream()
                .map(DriftBottlePickRecord::getBottleId)
                .collect(Collectors.toList());

        // 获取被举报成立的瓶子ID列表
        List<Long> bannedBottleIds = reportMapper.selectApprovedTargetIds("drift_bottle");

        // 随机抽取1个未捡过的漂流瓶（排除被举报成立的）
        LambdaQueryWrapper<DriftBottle> wrapper = new LambdaQueryWrapper<DriftBottle>()
                .eq(DriftBottle::getStatus, "drifting")
                .notIn(!pickedBottleIds.isEmpty(), DriftBottle::getId, pickedBottleIds)
                .notIn(bannedBottleIds != null && !bannedBottleIds.isEmpty(), DriftBottle::getId, bannedBottleIds);

        // 公海/私海模式切换：私海仅捞自己的瓶子，公海（默认）排除自己的瓶子
        if ("private".equals(mode)) {
            wrapper.eq(DriftBottle::getUserId, userId);
        } else {
            wrapper.ne(DriftBottle::getUserId, userId);
        }

        wrapper.last("ORDER BY RAND() LIMIT 1");
        List<DriftBottle> bottles = driftBottleMapper.selectList(wrapper);

        if (bottles.isEmpty()) {
            return null;
        }

        DriftBottle bottle = bottles.get(0);

        // 插入捡瓶记录（uk_bottle_user 兜底并发安全）
        DriftBottlePickRecord record = new DriftBottlePickRecord();
        record.setBottleId(bottle.getId());
        record.setUserId(userId);
        record.setOpened(0);
        try {
            driftBottlePickRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 极少数并发场景：同一用户并发捡到同一瓶子
            throw new BusinessException(ErrorCode.CONFLICT, "捡瓶处理冲突，请重试");
        }

        BottlePickVO vo = new BottlePickVO();
        vo.setBottleId(bottle.getId());
        vo.setCreatedAt(bottle.getCreatedAt());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BottleVO getBottleContent(Long userId, Long bottleId) {
        DriftBottle bottle = driftBottleMapper.selectById(bottleId);
        if (bottle == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "漂流瓶不存在");
        }

        // 游客模式（userId=null）跳过捡瓶校验和 pick_record 更新
        if (userId != null) {
            // 校验已捡到
            checkPicked(userId, bottleId);
            // 更新 pick_record.opened=1
            driftBottlePickRecordMapper.update(null, new LambdaUpdateWrapper<DriftBottlePickRecord>()
                    .eq(DriftBottlePickRecord::getBottleId, bottleId)
                    .eq(DriftBottlePickRecord::getUserId, userId)
                    .set(DriftBottlePickRecord::getOpened, 1));
        }

        BottleVO vo = toBottleVO(bottle);
        fillAnonymousNames(java.util.Collections.singletonList(vo));
        return vo;
    }

    @Override
    public void releaseBottle(Long userId, Long bottleId) {
        // 游客模式（userId=null）直接返回，无状态变更
        if (userId == null) return;
        // 校验已捡到
        checkPicked(userId, bottleId);
        // 不做任何状态变更，瓶子继续漂流，pick_record 保留
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replyBottle(Long userId, Long bottleId, String content) {
        userService.checkUserNotMuted(userId);
        bannedWordFilterService.check(content, "replyBottle");
        checkPicked(userId, bottleId);

        DriftBottleReply reply = new DriftBottleReply();
        reply.setBottleId(bottleId);
        reply.setUserId(userId);
        reply.setContent(content);
        try {
            driftBottleReplyMapper.insert(reply);
        } catch (DuplicateKeyException e) {
            // uk_bottle_user 唯一约束兜底：每人只能回复一次
            throw new BusinessException(ErrorCode.ALREADY_REPLIED_BOTTLE);
        }
        log.info("漂流瓶回复成功: userId={}, bottleId={}, replyId={}", userId, bottleId, reply.getId());

        // 通知瓶主收到回复
        DriftBottle bottle = driftBottleMapper.selectById(bottleId);
        if (bottle != null && !userId.equals(bottle.getUserId())) {
            notificationService.sendNotification(
                    bottle.getUserId(),
                    "bottle_reply",
                    "你的漂流瓶收到了一条回复",
                    content.length() > 50 ? content.substring(0, 50) + "…" : content,
                    "drift_bottle",
                    bottleId);
        }
    }

    @Override
    public List<BottleReplyVO> getBottleReplies(Long userId, Long bottleId) {
        DriftBottle bottle = driftBottleMapper.selectById(bottleId);
        if (bottle == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "漂流瓶不存在");
        }
        // 游客模式（userId=null）跳过瓶主校验
        if (userId != null && !userId.equals(bottle.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅瓶主可查看回复");
        }

        List<Long> bannedReplyIds = reportMapper.selectApprovedTargetIds("bottle_reply");
        
        List<DriftBottleReply> replies = driftBottleReplyMapper.selectList(
                new LambdaQueryWrapper<DriftBottleReply>()
                        .eq(DriftBottleReply::getBottleId, bottleId)
                        .notIn(bannedReplyIds != null && !bannedReplyIds.isEmpty(), DriftBottleReply::getId, bannedReplyIds)
                        .orderByAsc(DriftBottleReply::getCreatedAt));
        List<BottleReplyVO> result = replies.stream().map(this::toReplyVO).collect(Collectors.toList());
        fillReplyAnonymousNames(result);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void thankBottle(Long userId, Long bottleId) {
        userService.checkUserNotMuted(userId);
        // 校验已捡到该瓶子
        checkPicked(userId, bottleId);

        DriftBottle bottle = driftBottleMapper.selectById(bottleId);
        if (bottle == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "漂流瓶不存在");
        }

        // 校验未感谢过
        List<Long> thankedBy = parseThankedBy(bottle.getThankedBy());
        if (thankedBy.contains(userId)) {
            throw new BusinessException(ErrorCode.ALREADY_THANKED);
        }

        // 更新 thanked_by
        thankedBy.add(userId);
        driftBottleMapper.update(null, new LambdaUpdateWrapper<DriftBottle>()
                .eq(DriftBottle::getId, bottleId)
                .set(DriftBottle::getThankedBy, serializeThankedBy(thankedBy)));

        // 给瓶子作者 +1代币 +1萤火
        thankReward(bottle.getUserId(), bottleId);
        log.info("漂流瓶感谢成功: userId={}, bottleId={}, targetUserId={}", userId, bottleId, bottle.getUserId());

        // 通知瓶主被感谢
        notificationService.sendNotification(
                bottle.getUserId(),
                "bottle_thank",
                "你的漂流瓶被感谢了",
                "有人感谢了你的漂流瓶，获得 1 代币 + 1 萤火",
                "drift_bottle",
                bottleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void thankBottleReply(Long userId, Long replyId) {
        userService.checkUserNotMuted(userId);

        DriftBottleReply reply = driftBottleReplyMapper.selectById(replyId);
        if (reply == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "回复不存在");
        }

        DriftBottle bottle = driftBottleMapper.selectById(reply.getBottleId());
        if (bottle == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "漂流瓶不存在");
        }

        // 校验当前用户是该瓶子的捡到者或瓶子作者
        boolean isPicker = isPicked(userId, reply.getBottleId());
        boolean isOwner = userId.equals(bottle.getUserId());
        if (!isPicker && !isOwner) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权感谢该回复");
        }

        // 校验未感谢过
        List<Long> thankedBy = parseThankedBy(reply.getThankedBy());
        if (thankedBy.contains(userId)) {
            throw new BusinessException(ErrorCode.ALREADY_THANKED);
        }

        // 更新 thanked_by
        thankedBy.add(userId);
        driftBottleReplyMapper.update(null, new LambdaUpdateWrapper<DriftBottleReply>()
                .eq(DriftBottleReply::getId, replyId)
                .set(DriftBottleReply::getThankedBy, serializeThankedBy(thankedBy)));

        // 给回复者 +1代币 +1萤火
        thankReward(reply.getUserId(), replyId);
        log.info("瓶子回复感谢成功: userId={}, replyId={}, targetUserId={}", userId, replyId, reply.getUserId());

        // 通知回复者被感谢
        notificationService.sendNotification(
                reply.getUserId(),
                "bottle_thank",
                "你的漂流瓶回复被感谢了",
                "有人感谢了你的回复，获得 1 代币 + 1 萤火",
                "bottle_reply",
                replyId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sinkBottle(Long userId, Long bottleId) {
        DriftBottle bottle = driftBottleMapper.selectById(bottleId);
        if (bottle == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "漂流瓶不存在");
        }
        if (!userId.equals(bottle.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅瓶主可沉底瓶子");
        }
        driftBottleMapper.update(null, new LambdaUpdateWrapper<DriftBottle>()
                .eq(DriftBottle::getId, bottleId)
                .set(DriftBottle::getStatus, "sunk")
                .set(DriftBottle::getHideReason, "user")
                .set(DriftBottle::getSunkAt, LocalDateTime.now()));
    }

    @Override
    public PageResult<BottleVO> getMyBottles(Long userId, int page, int size,
                                             String keyword, String timeRange,
                                             String startDate, String endDate) {
        Page<DriftBottle> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<DriftBottle> wrapper = new LambdaQueryWrapper<DriftBottle>()
                .eq(DriftBottle::getUserId, userId);

        // 关键词 LIKE 模糊匹配
        if (StringUtils.hasText(keyword)) {
            wrapper.like(DriftBottle::getContent, keyword.trim());
        }

        // 时间范围过滤
        LocalDateTime[] range = resolveTimeRange(timeRange, startDate, endDate);
        if (range[0] != null) {
            wrapper.ge(DriftBottle::getCreatedAt, range[0]);
        }
        if (range[1] != null) {
            wrapper.lt(DriftBottle::getCreatedAt, range[1]);
        }

        wrapper.orderByDesc(DriftBottle::getCreatedAt);

        IPage<DriftBottle> result = driftBottleMapper.selectPage(pageParam, wrapper);
        List<BottleVO> list = result.getRecords().stream().map(this::toBottleVO).collect(Collectors.toList());
        fillAnonymousNames(list);

        // 批量查询每个瓶子的回复数
        if (!list.isEmpty()) {
            List<Long> bottleIds = list.stream().map(BottleVO::getId).collect(Collectors.toList());
            List<DriftBottleReply> replies = driftBottleReplyMapper.selectList(
                    new LambdaQueryWrapper<DriftBottleReply>()
                            .in(DriftBottleReply::getBottleId, bottleIds));
            Map<Long, Long> countMap = replies.stream()
                    .collect(Collectors.groupingBy(DriftBottleReply::getBottleId, Collectors.counting()));
            list.forEach(vo -> vo.setReplyCount(countMap.getOrDefault(vo.getId(), 0L).intValue()));
        }
        return new PageResult<>(list, result.getTotal(), page, size);
    }

    @Override
    public PageResult<BottleSummaryVO> getBottleList(int page, int size) {
        Page<DriftBottle> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<DriftBottle> wrapper = new LambdaQueryWrapper<DriftBottle>()
                .orderByDesc(DriftBottle::getCreatedAt);
        IPage<DriftBottle> result = driftBottleMapper.selectPage(pageParam, wrapper);
        List<BottleSummaryVO> list = result.getRecords().stream().map(this::toSummaryVO).collect(Collectors.toList());
        return new PageResult<>(list, result.getTotal(), page, size);
    }

    // ==================== 私有辅助方法 ====================

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ZoneId SH = ZoneId.of("Asia/Shanghai");

    /**
     * 解析时间范围为 [start, end) 左闭右开。
     * timeRange: today/week/month/custom；其它值视为不限。
     * 返回长度为 2 的数组，元素可能为 null 表示该端不限制。
     */
    static LocalDateTime[] resolveTimeRange(String timeRange, String startDateStr, String endDateStr) {
        LocalDateTime start = null;
        LocalDateTime end = null;
        LocalDate today = LocalDate.now(SH);
        if ("today".equals(timeRange)) {
            start = today.atStartOfDay();
            end = today.plusDays(1).atStartOfDay();
        } else if ("week".equals(timeRange)) {
            end = today.plusDays(1).atStartOfDay();
            start = today.minusDays(6).atStartOfDay();
        } else if ("month".equals(timeRange)) {
            end = today.plusDays(1).atStartOfDay();
            start = today.minusDays(29).atStartOfDay();
        } else if ("custom".equals(timeRange)) {
            if (StringUtils.hasText(startDateStr)) {
                try {
                    start = LocalDate.parse(startDateStr, DATE_FMT).atStartOfDay();
                } catch (Exception ignore) { /* 格式非法忽略 */ }
            }
            if (StringUtils.hasText(endDateStr)) {
                try {
                    end = LocalDate.parse(endDateStr, DATE_FMT).plusDays(1).atStartOfDay();
                } catch (Exception ignore) { /* 格式非法忽略 */ }
            }
        }
        return new LocalDateTime[]{start, end};
    }

    /**
     * 感谢奖励：给目标用户 +1代币 +1萤火（事务内）
     */
    private void thankReward(Long targetUserId, Long refId) {
        // 感谢奖励：+1代币 +1累计萤火 +1萤火余额（分布式锁 + 乐观锁兜底）
        tokenBalanceHelper.modifyWithLock(targetUserId, u -> {
            u.setTokenBalance(u.getTokenBalance() + 1);
            u.setTotalFirefly(u.getTotalFirefly() + 1);
            u.setFireflyBalance(u.getFireflyBalance() + 1);
        });
        TokenTransaction tx = new TokenTransaction();
        tx.setUserId(targetUserId);
        tx.setType("earn");
        tx.setAmount(1);
        tx.setSource("receive_thanks");
        tx.setRefId(refId);
        tokenTransactionMapper.insert(tx);
    }

    /**
     * 校验当前用户已捡到该瓶子
     */
    private void checkPicked(Long userId, Long bottleId) {
        if (!isPicked(userId, bottleId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "未捡到该漂流瓶");
        }
    }

    /**
     * 判断当前用户是否已捡到该瓶子
     */
    private boolean isPicked(Long userId, Long bottleId) {
        Long count = driftBottlePickRecordMapper.selectCount(
                new LambdaQueryWrapper<DriftBottlePickRecord>()
                        .eq(DriftBottlePickRecord::getBottleId, bottleId)
                        .eq(DriftBottlePickRecord::getUserId, userId));
        return count != null && count > 0;
    }

    /**
     * 解析 thanked_by JSON 数组
     */
    private List<Long> parseThankedBy(String thankedBy) {
        if (!StringUtils.hasText(thankedBy)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(thankedBy, new TypeReference<List<Long>>() {});
        } catch (JsonProcessingException e) {
            log.warn("解析 thanked_by 失败: {}", thankedBy, e);
            return new ArrayList<>();
        }
    }

    /**
     * 序列化 thanked_by 为 JSON 数组字符串
     */
    private String serializeThankedBy(List<Long> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "JSON序列化失败");
        }
    }

    private BottleVO toBottleVO(DriftBottle bottle) {
        BottleVO vo = new BottleVO();
        vo.setId(bottle.getId());
        vo.setContent(bottle.getContent());
        vo.setUserId(bottle.getUserId());
        vo.setStatus(bottle.getStatus());
        vo.setCreatedAt(bottle.getCreatedAt());
        return vo;
    }

    private BottleReplyVO toReplyVO(DriftBottleReply reply) {
        BottleReplyVO vo = new BottleReplyVO();
        vo.setId(reply.getId());
        vo.setBottleId(reply.getBottleId());
        vo.setUserId(reply.getUserId());
        vo.setContent(reply.getContent());
        vo.setCreatedAt(reply.getCreatedAt());
        return vo;
    }

    private void fillAnonymousNames(List<BottleVO> bottles) {
        if (bottles == null || bottles.isEmpty()) return;
        List<Long> userIds = bottles.stream()
                .map(BottleVO::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (userIds.isEmpty()) return;
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, String> nameMap = users.stream()
                .collect(Collectors.toMap(User::getId, User::getAnonymousName, (a, b) -> a));
        // bot 身份集合：username == bot_echo 或 role == bot
        Set<Long> botIds = users.stream()
                .filter(u -> "bot_echo".equals(u.getUsername()) || "bot".equals(u.getRole()))
                .map(User::getId)
                .collect(Collectors.toSet());
        bottles.forEach(b -> {
            b.setAnonymousName(nameMap.getOrDefault(b.getUserId(), "匿名旅人"));
            b.setIsFromBot(botIds.contains(b.getUserId()));
        });
    }

    private void fillReplyAnonymousNames(List<BottleReplyVO> replies) {
        if (replies == null || replies.isEmpty()) return;
        List<Long> userIds = replies.stream()
                .map(BottleReplyVO::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (userIds.isEmpty()) return;
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, String> nameMap = users.stream()
                .collect(Collectors.toMap(User::getId, User::getAnonymousName, (a, b) -> a));
        Set<Long> botIds = users.stream()
                .filter(u -> "bot_echo".equals(u.getUsername()) || "bot".equals(u.getRole()))
                .map(User::getId)
                .collect(Collectors.toSet());
        replies.forEach(r -> {
            r.setAnonymousName(nameMap.getOrDefault(r.getUserId(), "匿名旅人"));
            r.setIsFromBot(botIds.contains(r.getUserId()));
        });
    }

    private BottleSummaryVO toSummaryVO(DriftBottle bottle) {
        BottleSummaryVO vo = new BottleSummaryVO();
        vo.setId(bottle.getId());
        vo.setCreatedAt(bottle.getCreatedAt());
        return vo;
    }
}
