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
import com.glimmer.entity.Letter;
import com.glimmer.entity.Report;
import com.glimmer.entity.User;
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

import java.time.LocalDateTime;
import java.util.Collections;
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

    public ReportServiceImpl(ReportMapper reportMapper, UserMapper userMapper,
                             DriftBottleMapper driftBottleMapper, DriftBottleReplyMapper driftBottleReplyMapper,
                             LetterMapper letterMapper, CampfireMessageMapper campfireMessageMapper,
                             NotificationService notificationService) {
        this.reportMapper = reportMapper;
        this.userMapper = userMapper;
        this.driftBottleMapper = driftBottleMapper;
        this.driftBottleReplyMapper = driftBottleReplyMapper;
        this.letterMapper = letterMapper;
        this.campfireMessageMapper = campfireMessageMapper;
        this.notificationService = notificationService;
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

        // 5. 被举报人 pending_report_count += 1（乐观锁 @Version）
        User targetUser = userMapper.selectById(targetUserId);
        if (targetUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "被举报用户不存在");
        }
        int newCount = (targetUser.getPendingReportCount() == null ? 0 : targetUser.getPendingReportCount()) + 1;
        targetUser.setPendingReportCount(newCount);

        // 6. 若达到阈值则自动封禁
        boolean shouldBan = newCount >= BAN_THRESHOLD && !"banned".equals(targetUser.getStatus());
        if (shouldBan) {
            targetUser.setStatus("banned");
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
    public void reviewReport(Long reviewerId, Long reportId, String result, String reviewComment) {
        // 1. 校验审核人是 admin 角色（Controller 已校验，Service 兜底）
        User reviewer = userMapper.selectById(reviewerId);
        if (reviewer == null || !"admin".equals(reviewer.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无管理员权限");
        }

        // 2. 校验 result 取值
        if (!"approved".equals(result) && !"rejected".equals(result)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "审核结果只能为 approved 或 rejected");
        }

        // 3. 校验举报存在且为待审核
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "举报不存在");
        }
        if (!"pending".equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该举报已审核");
        }

        // 4. 更新举报记录
        report.setStatus("reviewed");
        report.setResult(result);
        report.setReviewerId(reviewerId);
        report.setReviewComment(reviewComment);
        report.setReviewedAt(LocalDateTime.now());
        reportMapper.updateById(report);

        // 5. 被举报人 pending_report_count -= 1（最小为 0，乐观锁）
        Long targetUserId = report.getTargetUserId();
        User targetUser = userMapper.selectById(targetUserId);
        if (targetUser != null) {
            int currentCount = targetUser.getPendingReportCount() == null ? 0 : targetUser.getPendingReportCount();
            int newCount = Math.max(0, currentCount - 1);
            targetUser.setPendingReportCount(newCount);

            // 6. 若被举报人当前被封禁且计数已低于阈值，则解封
            boolean shouldUnban = "banned".equals(targetUser.getStatus()) && newCount < BAN_THRESHOLD;
            if (shouldUnban) {
                targetUser.setStatus("active");
            }

            boolean updated = userMapper.updateById(targetUser) > 0;
            if (!updated) {
                throw new BusinessException(ErrorCode.CONFLICT, "审核处理冲突，请重试");
            }

            if (shouldUnban) {
                notificationService.sendNotification(
                        targetUserId,
                        "system",
                        "账号已解封",
                        "您的待处理举报数已降至阈值以下，账号已恢复使用。",
                        null,
                        null);
                log.info("用户因举报计数降低被自动解封: userId={}, pendingReportCount={}", targetUserId, newCount);
            }
        }

        // 7. 向举报人发送 report_result 通知
        String resultLabel = "approved".equals(result) ? "举报成立" : "举报驳回";
        String targetLabel = describeTargetType(report.getTargetType());
        String reporterContent = String.format(
                "您举报的%s内容，审核结果：%s。%s",
                targetLabel, resultLabel, StringUtils.hasText(reviewComment) ? "审核备注：" + reviewComment : "");

        notificationService.sendNotification(
                report.getReporterId(),
                "report_result",
                "举报审核结果",
                reporterContent,
                "report",
                reportId);

        // 8. 向被举报人发送 report_result 通知
        if (targetUserId != null) {
            String targetContent = String.format(
                    "您的%s内容被举报，审核结果：%s。%s",
                    targetLabel, resultLabel, StringUtils.hasText(reviewComment) ? "审核备注：" + reviewComment : "");
            notificationService.sendNotification(
                    targetUserId,
                    "report_result",
                    "您的内容被举报",
                    targetContent,
                    "report",
                    reportId);
        }

        log.info("举报审核完成: reportId={}, reviewerId={}, result={}", reportId, reviewerId, result);
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
        return vo;
    }
}
