package com.glimmer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.response.PageResult;
import com.glimmer.entity.CampfireMessage;
import com.glimmer.entity.DriftBottle;
import com.glimmer.entity.DriftBottleReply;
import com.glimmer.entity.Letter;
import com.glimmer.entity.Report;
import com.glimmer.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.glimmer.mapper.CampfireMessageMapper;
import com.glimmer.mapper.DriftBottleMapper;
import com.glimmer.mapper.DriftBottleReplyMapper;
import com.glimmer.mapper.LetterMapper;
import com.glimmer.mapper.ReportMapper;
import com.glimmer.mapper.UserMapper;
import com.glimmer.service.NotificationService;
import com.glimmer.service.ReportService;
import com.glimmer.service.dto.ReportVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 举报服务实现
 * 见开发文档 §2.8、§4.11
 */
@Slf4j
@Service
public class ReportServiceImpl implements ReportService {

    /** 触发自动封禁的待处理举报数阈值 */
    private static final int BAN_THRESHOLD = 7;

    private final ReportMapper reportMapper;
    private final UserMapper userMapper;
    private final DriftBottleMapper driftBottleMapper;
    private final DriftBottleReplyMapper driftBottleReplyMapper;
    private final LetterMapper letterMapper;
    private final CampfireMessageMapper campfireMessageMapper;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public ReportServiceImpl(ReportMapper reportMapper, UserMapper userMapper,
                             DriftBottleMapper driftBottleMapper, DriftBottleReplyMapper driftBottleReplyMapper,
                             LetterMapper letterMapper, CampfireMessageMapper campfireMessageMapper,
                             NotificationService notificationService, ObjectMapper objectMapper) {
        this.reportMapper = reportMapper;
        this.userMapper = userMapper;
        this.driftBottleMapper = driftBottleMapper;
        this.driftBottleReplyMapper = driftBottleReplyMapper;
        this.letterMapper = letterMapper;
        this.campfireMessageMapper = campfireMessageMapper;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createReport(Long reporterId, String targetType, Long targetId, String content) {
        // 1. 校验举报人状态（被封禁用户不可举报）
        User reporter = userMapper.selectById(reporterId);
        if (reporter == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if ("banned".equals(reporter.getStatus())) {
            throw new BusinessException(ErrorCode.USER_BANNED);
        }

        // 2. 校验目标类型并查询被举报人ID
        Long targetUserId = getTargetUserId(targetType, targetId);

        // 3. 不能举报自己
        if (targetUserId.equals(reporterId)) {
            throw new BusinessException(ErrorCode.CANNOT_REPORT_SELF);
        }

        // 4. 插入举报记录（uk_reporter_target 唯一约束兜底重复举报）
        Report report = new Report();
        report.setReporterId(reporterId);
        report.setTargetUserId(targetUserId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setContent(content);
        report.setStatus("pending");
        try {
            reportMapper.insert(report);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }

        // 5. 查询当天该用户被举报次数
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(23, 59, 59);
        Long todayReportCountLong = reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getTargetUserId, targetUserId)
                .ge(Report::getCreatedAt, startOfDay)
                .le(Report::getCreatedAt, endOfDay));
        int todayReportCount = todayReportCountLong != null ? todayReportCountLong.intValue() : 0;

        // 6. 更新被举报人 pending_report_count（乐观锁 @Version）
        User targetUser = userMapper.selectById(targetUserId);
        if (targetUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "被举报用户不存在");
        }
        int newCount = (targetUser.getPendingReportCount() == null ? 0 : targetUser.getPendingReportCount()) + 1;
        targetUser.setPendingReportCount(newCount);

        // 7. 若一天内被举报超过3次，则自动永久封禁
        boolean shouldBan = todayReportCount > 3 && !"banned".equals(targetUser.getStatus());
        if (shouldBan) {
            targetUser.setStatus("banned");
            targetUser.setMuteType("ban");
            targetUser.setMuteEndTime(null);
        }

        boolean updated = userMapper.updateById(targetUser) > 0;
        if (!updated) {
            // 乐观锁冲突（并发举报场景），uk_reporter_target 已保证不会重复举报
            throw new BusinessException(ErrorCode.CONFLICT, "举报处理冲突，请重试");
        }

        // 7. 若触发封禁，向被举报人发送 system 通知
        if (shouldBan) {
            notificationService.sendNotification(
                    targetUserId,
                    "system",
                    "账号已被封禁",
                    "您因累计被举报达到阈值，账号已被暂时封禁，请联系管理员申诉。",
                    null,
                    null);
            log.info("用户因累计举报被自动封禁: userId={}, pendingReportCount={}", targetUserId, newCount);
        }

        log.info("举报提交成功: reporterId={}, targetType={}, targetId={}, targetUserId={}",
                reporterId, targetType, targetId, targetUserId);
    }

    @Override
    public PageResult<ReportVO> getMyReports(Long reporterId, int page, int size) {
        Page<Report> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<Report>()
                .eq(Report::getReporterId, reporterId)
                .orderByDesc(Report::getCreatedAt);

        IPage<Report> result = reportMapper.selectPage(pageParam, wrapper);
        List<ReportVO> list = toVOList(result.getRecords());
        return new PageResult<>(list, result.getTotal(), page, size);
    }

    @Override
    public PageResult<ReportVO> getReportList(String status, int page, int size) {
        Page<Report> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<Report>()
                .eq(StringUtils.hasText(status), Report::getStatus, status)
                .orderByDesc(Report::getCreatedAt);

        IPage<Report> result = reportMapper.selectPage(pageParam, wrapper);
        List<ReportVO> list = toVOList(result.getRecords());
        return new PageResult<>(list, result.getTotal(), page, size);
    }

    @Override
    public ReportVO getReportDetail(Long reportId) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "举报不存在");
        }
        List<ReportVO> list = toVOList(Collections.singletonList(report));
        return list.get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewReport(Long reviewerId, Long reportId, String result, String reviewComment, String penaltyType) {
        User reviewer = userMapper.selectById(reviewerId);
        if (reviewer == null || !"admin".equals(reviewer.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无管理员权限");
        }

        if (!"approved".equals(result) && !"rejected".equals(result)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "审核结果只能为 approved 或 rejected");
        }

        if ("approved".equals(result) && StringUtils.hasText(penaltyType)) {
            if (!"warning".equals(penaltyType) && !"mute_24h".equals(penaltyType) && 
                !"mute_7d".equals(penaltyType) && !"ban".equals(penaltyType)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "处罚类型只能为 warning/mute_24h/mute_7d/ban");
            }
        }

        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "举报不存在");
        }
        if (!"pending".equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该举报已审核");
        }

        report.setStatus("reviewed");
        report.setResult(result);
        report.setReviewerId(reviewerId);
        report.setReviewComment(reviewComment);
        report.setReviewedAt(LocalDateTime.now());
        report.setPenaltyType(penaltyType);
        report.setAppealCount(0);
        reportMapper.updateById(report);

        Long targetUserId = report.getTargetUserId();
        User targetUser = userMapper.selectById(targetUserId);
        if (targetUser != null) {
            int currentCount = targetUser.getPendingReportCount() == null ? 0 : targetUser.getPendingReportCount();
            int newCount = Math.max(0, currentCount - 1);
            targetUser.setPendingReportCount(newCount);

            if ("approved".equals(result) && StringUtils.hasText(penaltyType)) {
                applyPenalty(targetUser, penaltyType);
            }

            boolean updated = userMapper.updateById(targetUser) > 0;
            if (!updated) {
                throw new BusinessException(ErrorCode.CONFLICT, "审核处理冲突，请重试");
            }
        }

        String resultLabel = "approved".equals(result) ? "举报成立" : "举报驳回";
        String targetLabel = describeTargetType(report.getTargetType());
        
        String reportedContent = getReportedContent(report.getTargetType(), report.getTargetId());
        String locationLabel = describeLocation(report.getTargetType(), report.getTargetId());

        String reporterContent = String.format(
                "您举报的%s内容（场所：%s，内容：%s），审核结果：%s。%s",
                targetLabel, locationLabel, reportedContent, resultLabel, 
                StringUtils.hasText(reviewComment) ? "审核备注：" + reviewComment : "");

        notificationService.sendNotification(
                report.getReporterId(),
                "report_result",
                "举报审核结果",
                reporterContent,
                "report",
                reportId);

        if (targetUserId != null) {
            StringBuilder targetContent = new StringBuilder();
            targetContent.append(String.format("您在%s发布的内容（内容：%s，发言者ID：%d）被举报，审核结果：%s。", 
                    locationLabel, reportedContent, targetUserId, resultLabel));
            if (StringUtils.hasText(reviewComment)) {
                targetContent.append("审核备注：").append(reviewComment).append("。");
            }
            if ("approved".equals(result) && StringUtils.hasText(penaltyType)) {
                String penaltyLabel = describePenaltyType(penaltyType);
                targetContent.append("处罚结果：").append(penaltyLabel).append("。");
                targetContent.append("您可以在意见与申诉页面提交申诉，最多可申诉3次。");
            }

            String extraJson = null;
            if ("approved".equals(result)) {
                try {
                    Map<String, Object> extraMap = new HashMap<>();
                    extraMap.put("reportId", reportId);
                    extraMap.put("result", result);
                    extraMap.put("penaltyType", penaltyType);
                    extraJson = objectMapper.writeValueAsString(extraMap);
                } catch (Exception e) {
                    log.warn("序列化通知额外信息失败", e);
                }
            }
            notificationService.sendNotification(
                    targetUserId,
                    "report_result",
                    "您的内容被举报",
                    targetContent.toString(),
                    "report",
                    reportId,
                    extraJson);
        }

        log.info("举报审核完成: reportId={}, reviewerId={}, result={}, penaltyType={}", reportId, reviewerId, result, penaltyType);
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

    /**
     * 根据 targetType 查询对应资源，返回被举报人ID；资源不存在抛 NOT_FOUND
     */
    private Long getTargetUserId(String targetType, Long targetId) {
        switch (targetType) {
            case "drift_bottle": {
                DriftBottle bottle = driftBottleMapper.selectById(targetId);
                if (bottle == null) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "举报目标不存在");
                }
                return bottle.getUserId();
            }
            case "bottle_reply": {
                DriftBottleReply reply = driftBottleReplyMapper.selectById(targetId);
                if (reply == null) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "举报目标不存在");
                }
                return reply.getUserId();
            }
            case "letter": {
                Letter letter = letterMapper.selectById(targetId);
                if (letter == null) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "举报目标不存在");
                }
                return letter.getSenderId();
            }
            case "campfire_message": {
                CampfireMessage message = campfireMessageMapper.selectById(targetId);
                if (message == null) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "举报目标不存在");
                }
                return message.getUserId();
            }
            default:
                throw new BusinessException(ErrorCode.PARAM_ERROR, "目标类型非法");
        }
    }

    private String describeTargetType(String targetType) {
        switch (targetType) {
            case "drift_bottle":
                return "漂流瓶";
            case "bottle_reply":
                return "漂流瓶回复";
            case "letter":
                return "信件";
            case "campfire_message":
                return "篝火消息";
            default:
                return targetType;
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
            return describeTargetType(targetType);
        }
    }

    private String truncateContent(String content) {
        if (content == null) {
            return "空内容";
        }
        if (content.length() <= 50) {
            return content;
        }
        return content.substring(0, 50) + "...";
    }

    /**
     * 批量关联用户名并组装 VO 列表
     */
    private List<ReportVO> toVOList(List<Report> reports) {
        if (reports.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> userIds = reports.stream()
                .flatMap(r -> java.util.Arrays.asList(
                        r.getReporterId(),
                        r.getTargetUserId(),
                        r.getReviewerId()).stream())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        return reports.stream().map(r -> toVO(r, userMap)).collect(Collectors.toList());
    }

    private ReportVO toVO(Report report, Map<Long, User> userMap) {
        ReportVO vo = new ReportVO();
        vo.setId(report.getId());
        vo.setReporterId(report.getReporterId());
        User reporter = userMap.get(report.getReporterId());
        vo.setReporterUsername(reporter == null ? null : reporter.getUsername());
        vo.setTargetUserId(report.getTargetUserId());
        User target = userMap.get(report.getTargetUserId());
        vo.setTargetUsername(target == null ? null : target.getUsername());
        vo.setTargetType(report.getTargetType());
        vo.setTargetId(report.getTargetId());
        vo.setContent(report.getContent());
        vo.setStatus(report.getStatus());
        vo.setResult(report.getResult());
        vo.setReviewerId(report.getReviewerId());
        vo.setReviewComment(report.getReviewComment());
        vo.setReviewedAt(report.getReviewedAt());
        vo.setCreatedAt(report.getCreatedAt());
        vo.setPenaltyType(report.getPenaltyType());
        vo.setAppealCount(report.getAppealCount());
        vo.setReportedContent(getReportedContent(report.getTargetType(), report.getTargetId()));
        vo.setLocation(describeLocation(report.getTargetType(), report.getTargetId()));
        return vo;
    }
}
