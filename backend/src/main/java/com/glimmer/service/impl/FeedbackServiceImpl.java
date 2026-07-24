package com.glimmer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.response.PageResult;
import com.glimmer.entity.CampfireMessage;
import com.glimmer.entity.DriftBottle;
import com.glimmer.entity.DriftBottleReply;
import com.glimmer.entity.Feedback;
import com.glimmer.entity.Letter;
import com.glimmer.entity.Report;
import com.glimmer.entity.User;
import com.glimmer.mapper.CampfireMessageMapper;
import com.glimmer.mapper.DriftBottleMapper;
import com.glimmer.mapper.DriftBottleReplyMapper;
import com.glimmer.mapper.FeedbackMapper;
import com.glimmer.mapper.LetterMapper;
import com.glimmer.mapper.ReportMapper;
import com.glimmer.mapper.UserMapper;
import com.glimmer.service.FeedbackService;
import com.glimmer.service.NotificationService;
import com.glimmer.service.dto.FeedbackVO;
import com.glimmer.service.dto.ReportVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 意见信服务实现
 * 见开发文档 §2.9、§4.12
 */
@Slf4j
@Service
public class FeedbackServiceImpl implements FeedbackService {

    private static final int MAX_APPEALS_PER_DAY = 15;
    private static final int MAX_APPEALS_PER_REPORT = 3;

    private final FeedbackMapper feedbackMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;
    private final ReportMapper reportMapper;
    private final DriftBottleMapper driftBottleMapper;
    private final DriftBottleReplyMapper driftBottleReplyMapper;
    private final LetterMapper letterMapper;
    private final CampfireMessageMapper campfireMessageMapper;

    public FeedbackServiceImpl(FeedbackMapper feedbackMapper, UserMapper userMapper,
                               NotificationService notificationService, ReportMapper reportMapper,
                               DriftBottleMapper driftBottleMapper,
                               DriftBottleReplyMapper driftBottleReplyMapper,
                               LetterMapper letterMapper,
                               CampfireMessageMapper campfireMessageMapper) {
        this.feedbackMapper = feedbackMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
        this.reportMapper = reportMapper;
        this.driftBottleMapper = driftBottleMapper;
        this.driftBottleReplyMapper = driftBottleReplyMapper;
        this.letterMapper = letterMapper;
        this.campfireMessageMapper = campfireMessageMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createFeedback(Long userId, String content) {
        createFeedback(userId, content, null, "feedback");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createAppeal(Long userId, Long reportId, String content) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        if (reportId != null) {
            Report report = reportMapper.selectById(reportId);
            if (report == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "举报不存在");
            }
            if (!userId.equals(report.getTargetUserId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "只能申诉自己被举报的内容");
            }
            if (!"approved".equals(report.getResult())) {
                throw new BusinessException(ErrorCode.CONFLICT, "只有举报成立的内容才能申诉");
            }

            int currentAppealCount = report.getAppealCount() == null ? 0 : report.getAppealCount();
            if (currentAppealCount >= MAX_APPEALS_PER_REPORT) {
                throw new BusinessException(ErrorCode.CONFLICT, "该举报已达到最大申诉次数");
            }

            report.setAppealCount(currentAppealCount + 1);
            reportMapper.updateById(report);
        }

        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        long todayAppealCount = feedbackMapper.selectCount(new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getUserId, userId)
                .eq(Feedback::getType, "appeal")
                .ge(Feedback::getCreatedAt, todayStart));
        if (todayAppealCount >= MAX_APPEALS_PER_DAY) {
            throw new BusinessException(ErrorCode.CONFLICT, "今日申诉次数已达上限（7次）");
        }

        createFeedback(userId, content, reportId, "appeal");
        log.info("申诉提交成功: userId={}, reportId={}", userId, reportId);
    }

    private void createFeedback(Long userId, String content, Long reportId, String type) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setContent(content);
        feedback.setStatus("pending");
        feedback.setType(type);
        feedback.setReportId(reportId);
        feedbackMapper.insert(feedback);
        log.info("反馈提交成功: userId={}, feedbackId={}, type={}", userId, feedback.getId(), type);
    }

    @Override
    public PageResult<FeedbackVO> getMyFeedbacks(Long userId, int page, int size) {
        Page<Feedback> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getUserId, userId)
                .orderByDesc(Feedback::getCreatedAt);

        IPage<Feedback> result = feedbackMapper.selectPage(pageParam, wrapper);
        List<FeedbackVO> list = toVOList(result.getRecords());
        return new PageResult<>(list, result.getTotal(), page, size);
    }

    @Override
    public FeedbackVO getFeedbackDetail(Long userId, Long feedbackId) {
        Feedback feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "意见信不存在");
        }
        if (!userId.equals(feedback.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该意见信");
        }
        List<FeedbackVO> list = toVOList(Collections.singletonList(feedback));
        return list.get(0);
    }

    @Override
    public PageResult<FeedbackVO> getFeedbackList(String status, int page, int size) {
        Page<Feedback> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getType, "feedback")
                .eq(StringUtils.hasText(status), Feedback::getStatus, status)
                .orderByDesc(Feedback::getCreatedAt);

        IPage<Feedback> result = feedbackMapper.selectPage(pageParam, wrapper);
        List<FeedbackVO> list = toVOList(result.getRecords());
        return new PageResult<>(list, result.getTotal(), page, size);
    }

    @Override
    public FeedbackVO getFeedbackDetail(Long feedbackId) {
        Feedback feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "意见信不存在");
        }
        List<FeedbackVO> list = toVOList(Collections.singletonList(feedback));
        return list.get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replyFeedback(Long adminId, Long feedbackId, String reply) {
        // 1. 校验 admin 角色
        User admin = userMapper.selectById(adminId);
        if (admin == null || !"admin".equals(admin.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无管理员权限");
        }

        // 2. 校验意见信存在且为待回复
        Feedback feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "意见信不存在");
        }
        if (!"pending".equals(feedback.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该意见信已回复");
        }

        // 3. 更新意见信
        feedback.setReply(reply);
        feedback.setReplyAdminId(adminId);
        feedback.setRepliedAt(LocalDateTime.now());
        feedback.setStatus("replied");
        feedbackMapper.updateById(feedback);

        // 4. 向提交者发送 feedback_reply 通知
        notificationService.sendNotification(
                feedback.getUserId(),
                "feedback_reply",
                "您的意见已收到回复",
                reply,
                "feedback",
                feedbackId);

        log.info("意见信回复成功: feedbackId={}, adminId={}", feedbackId, adminId);
    }

    /**
     * 批量关联用户名并组装 VO 列表
     */
    private List<FeedbackVO> toVOList(List<Feedback> feedbacks) {
        if (feedbacks.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> userIds = feedbacks.stream()
                .map(Feedback::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        return feedbacks.stream().map(f -> toVO(f, userMap)).collect(Collectors.toList());
    }

    private FeedbackVO toVO(Feedback feedback, Map<Long, User> userMap) {
        FeedbackVO vo = new FeedbackVO();
        vo.setId(feedback.getId());
        vo.setUserId(feedback.getUserId());
        User user = userMap.get(feedback.getUserId());
        vo.setUsername(user == null ? null : user.getUsername());
        vo.setContent(feedback.getContent());
        vo.setReply(feedback.getReply());
        vo.setStatus(feedback.getStatus());
        vo.setReplyAdminId(feedback.getReplyAdminId());
        vo.setRepliedAt(feedback.getRepliedAt());
        vo.setCreatedAt(feedback.getCreatedAt());
        vo.setType(feedback.getType());
        vo.setReportId(feedback.getReportId());
        return vo;
    }

    @Override
    public PageResult<FeedbackVO> getAppealList(String status, int page, int size) {
        LambdaQueryWrapper<Feedback> query = new LambdaQueryWrapper<>();
        query.eq(Feedback::getType, "appeal");
        if (StringUtils.hasText(status)) {
            query.eq(Feedback::getStatus, status);
        }
        query.orderByDesc(Feedback::getCreatedAt);

        Page<Feedback> pageResult = feedbackMapper.selectPage(new Page<>(page, size), query);
        List<FeedbackVO> voList = toVOList(pageResult.getRecords());
        return new PageResult<>(voList, pageResult.getTotal(), page, size);
    }

    @Override
    public FeedbackVO getAppealDetail(Long feedbackId) {
        Feedback feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "申诉不存在");
        }
        Map<Long, User> userMap = Collections.emptyMap();
        if (feedback.getUserId() != null) {
            User user = userMapper.selectById(feedback.getUserId());
            if (user != null) {
                userMap = Collections.singletonMap(user.getId(), user);
            }
        }
        FeedbackVO vo = toVO(feedback, userMap);
        if (feedback.getReportId() != null) {
            Report report = reportMapper.selectById(feedback.getReportId());
            if (report != null) {
                vo.setReportId(report.getId());
                Map<Long, User> reportUserMap = new HashMap<>();
                if (report.getReporterId() != null) {
                    User reporter = userMapper.selectById(report.getReporterId());
                    if (reporter != null) reportUserMap.put(reporter.getId(), reporter);
                }
                if (report.getTargetUserId() != null) {
                    User target = userMapper.selectById(report.getTargetUserId());
                    if (target != null) reportUserMap.put(target.getId(), target);
                }
                ReportVO reportVO = new ReportVO();
                reportVO.setId(report.getId());
                reportVO.setReporterId(report.getReporterId());
                reportVO.setReporterUsername(reportUserMap.get(report.getReporterId()) == null ? null : reportUserMap.get(report.getReporterId()).getUsername());
                reportVO.setTargetUserId(report.getTargetUserId());
                reportVO.setTargetUsername(reportUserMap.get(report.getTargetUserId()) == null ? null : reportUserMap.get(report.getTargetUserId()).getUsername());
                reportVO.setTargetType(report.getTargetType());
                reportVO.setTargetId(report.getTargetId());
                reportVO.setContent(report.getContent());
                reportVO.setStatus(report.getStatus());
                reportVO.setResult(report.getResult());
                reportVO.setPenaltyType(report.getPenaltyType());
                reportVO.setAppealCount(report.getAppealCount());
                reportVO.setReportedContent(getReportedContent(report.getTargetType(), report.getTargetId()));
                reportVO.setLocation(describeLocation(report.getTargetType(), report.getTargetId()));
                vo.setReport(reportVO);
            }
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewAppeal(Long adminId, Long feedbackId, String result, String reply, String newPenaltyType) {
        User admin = userMapper.selectById(adminId);
        if (admin == null || !"admin".equals(admin.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无管理员权限");
        }

        Feedback feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "申诉不存在");
        }
        if (!"appeal".equals(feedback.getType())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该反馈不是申诉");
        }
        if (!"pending".equals(feedback.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该申诉已处理");
        }

        feedback.setReply(reply);
        feedback.setReplyAdminId(adminId);
        feedback.setRepliedAt(LocalDateTime.now());
        feedback.setStatus("replied");
        feedbackMapper.updateById(feedback);

        if ("approved".equals(result)) {
            // 申诉通过，解除或变更处罚
            // 优先使用举报关联的用户ID，如果没有则使用申诉人自己的ID
            Long targetUserId = feedback.getUserId(); // 默认使用申诉人ID
            log.info("申诉审核通过: feedbackId={}, feedbackUserId={}, reportId={}", 
                    feedbackId, feedback.getUserId(), feedback.getReportId());
            
            if (feedback.getReportId() != null) {
                Report report = reportMapper.selectById(feedback.getReportId());
                if (report != null && report.getTargetUserId() != null) {
                    targetUserId = report.getTargetUserId();
                    log.info("使用举报关联用户ID: reportId={}, targetUserId={}", feedback.getReportId(), targetUserId);
                } else {
                    log.info("举报记录不存在或无目标用户，使用申诉人ID: feedbackUserId={}", feedback.getUserId());
                }
            }
            
            // 根据目标用户ID更新处罚状态（乐观锁 @Version）
            User targetUser = userMapper.selectById(targetUserId);
            if (targetUser != null) {
                log.info("找到目标用户: userId={}, username={}, status={}, muteType={}, muteEndTime={}, version={}", 
                        targetUserId, targetUser.getUsername(), targetUser.getStatus(), 
                        targetUser.getMuteType(), targetUser.getMuteEndTime(), targetUser.getVersion());
                if (!StringUtils.hasText(newPenaltyType)) {
                    // 解除处罚：清除所有处罚状态
                    log.info("解除处罚: userId={}, 原status={}, 原muteType={}", targetUserId, targetUser.getStatus(), targetUser.getMuteType());
                    targetUser.setMuteType(null);
                    targetUser.setMuteEndTime(null);
                    if ("banned".equals(targetUser.getStatus())) {
                        targetUser.setStatus("active");
                        log.info("用户状态从 banned 变更为 active");
                    }
                } else {
                    // 变更处罚类型
                    log.info("变更处罚类型: userId={}, newPenaltyType={}", targetUserId, newPenaltyType);
                    applyPenalty(targetUser, newPenaltyType);
                }
                log.info("准备更新用户: userId={}, 更新后status={}, 更新后muteType={}, 更新后muteEndTime={}, version={}", 
                        targetUserId, targetUser.getStatus(), targetUser.getMuteType(), 
                        targetUser.getMuteEndTime(), targetUser.getVersion());
                int updateCount = userMapper.updateById(targetUser);
                log.info("用户更新结果: userId={}, updateCount={}", targetUserId, updateCount);
                boolean updated = updateCount > 0;
                if (!updated) {
                    throw new BusinessException(ErrorCode.CONFLICT, "处罚状态更新冲突，请重试");
                }
                log.info("申诉处罚状态已更新成功: userId={}, status={}, muteType={}, muteEndTime={}", 
                        targetUserId, targetUser.getStatus(), targetUser.getMuteType(), targetUser.getMuteEndTime());
            }

            // 发送通知（独立 try-catch，避免通知失败影响用户状态更新）
            try {
                notificationService.sendNotification(
                        feedback.getUserId(),
                        "appeal_result",
                        "申诉审核结果",
                        "您的申诉已通过，处罚已" + (!StringUtils.hasText(newPenaltyType) ? "解除" : "变更为" + describePenaltyType(newPenaltyType)) + "。" +
                                (StringUtils.hasText(reply) ? "审核备注：" + reply : ""),
                        "feedback",
                        feedbackId);
            } catch (Exception e) {
                log.error("发送申诉通知失败: userId={}, feedbackId={}", feedback.getUserId(), feedbackId, e);
            }
        } else {
            // 发送通知（独立 try-catch，避免通知失败影响申诉状态更新）
            try {
                notificationService.sendNotification(
                        feedback.getUserId(),
                        "appeal_result",
                        "申诉审核结果",
                        "您的申诉未通过。" + (StringUtils.hasText(reply) ? "审核备注：" + reply : ""),
                        "feedback",
                        feedbackId);
            } catch (Exception e) {
                log.error("发送申诉通知失败: userId={}, feedbackId={}", feedback.getUserId(), feedbackId, e);
            }
        }

        log.info("申诉审核完成: feedbackId={}, result={}, adminId={}", feedbackId, result, adminId);
    }

    private void applyPenalty(User user, String penaltyType) {
        user.setMuteType(penaltyType);
        if ("warning".equals(penaltyType)) {
            // 警告不限制发言，状态保持正常
            user.setStatus("active");
            user.setMuteEndTime(null);
        } else if ("ban".equals(penaltyType)) {
            // 永久封禁
            user.setStatus("banned");
            user.setMuteEndTime(null);
        } else {
            // 禁言（mute_24h/mute_7d）：状态设为封禁
            user.setStatus("banned");
            LocalDateTime now = LocalDateTime.now();
            if ("mute_24h".equals(penaltyType)) {
                user.setMuteEndTime(now.plusHours(24));
            } else if ("mute_7d".equals(penaltyType)) {
                user.setMuteEndTime(now.plusDays(7));
            }
        }
    }

    private String describePenaltyType(String penaltyType) {
        switch (penaltyType) {
            case "warning": return "警告";
            case "mute_24h": return "禁言24小时";
            case "mute_7d": return "禁言7天";
            case "ban": return "永久封禁";
            default: return penaltyType;
        }
    }

    private String getReportedContent(String targetType, Long targetId) {
        try {
            switch (targetType) {
                case "drift_bottle": {
                    DriftBottle bottle = driftBottleMapper.selectById(targetId);
                    return bottle != null ? truncateContent(bottle.getContent()) : "未知内容";
                }
                case "bottle_reply": {
                    DriftBottleReply reply = driftBottleReplyMapper.selectById(targetId);
                    return reply != null ? truncateContent(reply.getContent()) : "未知内容";
                }
                case "letter": {
                    Letter letter = letterMapper.selectById(targetId);
                    return letter != null ? truncateContent(letter.getContent()) : "未知内容";
                }
                case "campfire_message": {
                    CampfireMessage message = campfireMessageMapper.selectById(targetId);
                    return message != null ? truncateContent(message.getContent()) : "未知内容";
                }
                default:
                    return "未知内容";
            }
        } catch (Exception e) {
            log.warn("获取被举报内容失败: targetType={}, targetId={}", targetType, targetId, e);
            return "未知内容";
        }
    }

    private String describeLocation(String targetType, Long targetId) {
        try {
            switch (targetType) {
                case "drift_bottle":
                    return "漂流瓶广场";
                case "bottle_reply": {
                    DriftBottleReply reply = driftBottleReplyMapper.selectById(targetId);
                    if (reply != null && reply.getBottleId() != null) {
                        return "漂流瓶#" + reply.getBottleId();
                    }
                    return "漂流瓶回复";
                }
                case "letter": {
                    Letter letter = letterMapper.selectById(targetId);
                    if (letter != null && letter.getReceiverId() != null) {
                        return "私信#" + letter.getReceiverId();
                    }
                    return "信件";
                }
                case "campfire_message": {
                    CampfireMessage message = campfireMessageMapper.selectById(targetId);
                    if (message != null && message.getCampfireId() != null) {
                        return "篝火#" + message.getCampfireId();
                    }
                    return "篝火";
                }
                default:
                    return targetType;
            }
        } catch (Exception e) {
            log.warn("获取发言场所失败: targetType={}, targetId={}", targetType, targetId, e);
            return targetType;
        }
    }

    private String truncateContent(String content) {
        if (content == null) {
            return "";
        }
        content = content.replaceAll("\\s+", " ").trim();
        if (content.length() <= 50) {
            return content;
        }
        return content.substring(0, 50) + "...";
    }
}
