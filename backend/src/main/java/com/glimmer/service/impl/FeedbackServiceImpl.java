package com.glimmer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.response.PageResult;
import com.glimmer.entity.Feedback;
import com.glimmer.entity.Report;
import com.glimmer.entity.User;
import com.glimmer.mapper.FeedbackMapper;
import com.glimmer.mapper.ReportMapper;
import com.glimmer.mapper.UserMapper;
import com.glimmer.service.FeedbackService;
import com.glimmer.service.NotificationService;
import com.glimmer.service.dto.FeedbackVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
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

    private static final int MAX_APPEALS_PER_DAY = 7;
    private static final int MAX_APPEALS_PER_REPORT = 3;

    private final FeedbackMapper feedbackMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;
    private final ReportMapper reportMapper;

    public FeedbackServiceImpl(FeedbackMapper feedbackMapper, UserMapper userMapper,
                               NotificationService notificationService, ReportMapper reportMapper) {
        this.feedbackMapper = feedbackMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
        this.reportMapper = reportMapper;
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
            if (feedback.getReportId() != null) {
                Report report = reportMapper.selectById(feedback.getReportId());
                if (report != null && report.getTargetUserId() != null) {
                    User targetUser = userMapper.selectById(report.getTargetUserId());
                    if (targetUser != null) {
                        if (newPenaltyType == null) {
                            targetUser.setMuteType(null);
                            targetUser.setMuteEndTime(null);
                            if ("banned".equals(targetUser.getStatus())) {
                                targetUser.setStatus("active");
                            }
                        } else {
                            applyPenalty(targetUser, newPenaltyType);
                        }
                        userMapper.updateById(targetUser);
                    }
                }
            }

            notificationService.sendNotification(
                    feedback.getUserId(),
                    "appeal_result",
                    "申诉审核结果",
                    "您的申诉已通过，处罚已" + (newPenaltyType == null ? "解除" : "变更为" + describePenaltyType(newPenaltyType)) + "。" +
                            (StringUtils.hasText(reply) ? "审核备注：" + reply : ""),
                    "feedback",
                    feedbackId);
        } else {
            notificationService.sendNotification(
                    feedback.getUserId(),
                    "appeal_result",
                    "申诉审核结果",
                    "您的申诉未通过。" + (StringUtils.hasText(reply) ? "审核备注：" + reply : ""),
                    "feedback",
                    feedbackId);
        }

        log.info("申诉审核完成: feedbackId={}, result={}, adminId={}", feedbackId, result, adminId);
    }

    private void applyPenalty(User user, String penaltyType) {
        user.setMuteType(penaltyType);
        if ("ban".equals(penaltyType)) {
            user.setStatus("banned");
            user.setMuteEndTime(null);
        } else {
            user.setStatus("active");
            LocalDateTime now = LocalDateTime.now();
            if ("mute_24h".equals(penaltyType)) {
                user.setMuteEndTime(now.plusHours(24));
            } else if ("mute_7d".equals(penaltyType)) {
                user.setMuteEndTime(now.plusDays(7));
            } else if ("warning".equals(penaltyType)) {
                user.setMuteEndTime(null);
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
}
