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
import com.glimmer.entity.Punishment;
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
import com.glimmer.service.PunishmentService;
import com.glimmer.service.ReportService;
import com.glimmer.service.dto.ReportGroupVO;
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
    
    /** 短时间重复举报阈值（秒）- 同一条信息在此时间内被多次举报自动隐藏 */
    private static final int DUPLICATE_REPORT_TIME_WINDOW_SECONDS = 300;
    
    /** 短时间内不同信息被举报阈值（条）- 同一账户在此时间内被举报多条不同信息自动封禁 */
    private static final int MULTIPLE_TARGET_REPORT_THRESHOLD = 5;
    
    /** 多条信息举报时间窗口（秒） */
    private static final int MULTIPLE_TARGET_TIME_WINDOW_SECONDS = 3600;

    private final ReportMapper reportMapper;
    private final UserMapper userMapper;
    private final DriftBottleMapper driftBottleMapper;
    private final DriftBottleReplyMapper driftBottleReplyMapper;
    private final LetterMapper letterMapper;
    private final CampfireMessageMapper campfireMessageMapper;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final PunishmentService punishmentService;

    public ReportServiceImpl(ReportMapper reportMapper, UserMapper userMapper,
                             DriftBottleMapper driftBottleMapper, DriftBottleReplyMapper driftBottleReplyMapper,
                             LetterMapper letterMapper, CampfireMessageMapper campfireMessageMapper,
                             NotificationService notificationService, ObjectMapper objectMapper,
                             PunishmentService punishmentService) {
        this.reportMapper = reportMapper;
        this.userMapper = userMapper;
        this.driftBottleMapper = driftBottleMapper;
        this.driftBottleReplyMapper = driftBottleReplyMapper;
        this.letterMapper = letterMapper;
        this.campfireMessageMapper = campfireMessageMapper;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.punishmentService = punishmentService;
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
            boolean hasActivePunishment = punishmentService.isUserBanned(reporterId);
            if (hasActivePunishment) {
                throw new BusinessException(ErrorCode.USER_BANNED);
            }
            // 处罚已全部结束，自动恢复为active
            reporter.setStatus("active");
            userMapper.updateById(reporter);
            log.info("用户举报时发现处罚已结束，自动恢复为active: userId={}", reporterId);
        }

        // 2. 校验目标类型并查询被举报人ID
        Long targetUserId = getTargetUserId(targetType, targetId);

        // 3. 不能举报自己
        if (targetUserId.equals(reporterId)) {
            throw new BusinessException(ErrorCode.CANNOT_REPORT_SELF);
        }

        // 4. 检查同一条信息短时间内是否被多次举报（5分钟内超过3次自动隐藏）
        LocalDateTime duplicateTimeWindowStart = LocalDateTime.now().minusSeconds(DUPLICATE_REPORT_TIME_WINDOW_SECONDS);
        Long duplicateReportCount = reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getTargetType, targetType)
                .eq(Report::getTargetId, targetId)
                .ge(Report::getCreatedAt, duplicateTimeWindowStart));
        
        boolean shouldHide = duplicateReportCount != null && duplicateReportCount >= 3;
        
        // 5. 插入举报记录（uk_reporter_target 唯一约束兜底重复举报）
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

        // 6. 如果同一条信息短时间内被多次举报，自动隐藏该内容
        if (shouldHide) {
            hideTargetContent(targetType, targetId);
            log.info("内容因短时间内被多次举报自动隐藏: targetType={}, targetId={}, reportCount={}", 
                    targetType, targetId, duplicateReportCount);
        }

        // 7. 查询短时间内（1小时）该用户被举报的不同信息数量
        LocalDateTime multiTargetTimeWindowStart = LocalDateTime.now().minusSeconds(MULTIPLE_TARGET_TIME_WINDOW_SECONDS);
        Long multiTargetCount = reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getTargetUserId, targetUserId)
                .ne(Report::getTargetId, targetId) // 排除当前这条
                .ge(Report::getCreatedAt, multiTargetTimeWindowStart));
        
        // 8. 查询当天该用户被举报次数
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(23, 59, 59);
        Long todayReportCountLong = reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getTargetUserId, targetUserId)
                .ge(Report::getCreatedAt, startOfDay)
                .le(Report::getCreatedAt, endOfDay));
        int todayReportCount = todayReportCountLong != null ? todayReportCountLong.intValue() : 0;

        // 9. 更新被举报人 pending_report_count（乐观锁 @Version）
        User targetUser = userMapper.selectById(targetUserId);
        if (targetUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "被举报用户不存在");
        }
        int newCount = (targetUser.getPendingReportCount() == null ? 0 : targetUser.getPendingReportCount()) + 1;
        targetUser.setPendingReportCount(newCount);

        // 10. 判断是否需要自动封禁
        // 条件1：一天内被举报超过3次
        // 条件2：短时间内（1小时）被举报多条不同信息（超过5条）
        boolean alreadyBanned = punishmentService.isUserBanned(targetUserId);
        boolean shouldBan = !alreadyBanned && 
                (todayReportCount > 3 || (multiTargetCount != null && multiTargetCount >= MULTIPLE_TARGET_REPORT_THRESHOLD));
        
        boolean updated = userMapper.updateById(targetUser) > 0;
        if (!updated) {
            // 乐观锁冲突（并发举报场景），uk_reporter_target 已保证不会重复举报
            throw new BusinessException(ErrorCode.CONFLICT, "举报处理冲突，请重试");
        }

        // 11. 若触发封禁，创建处罚单并发送通知给用户和管理员
        if (shouldBan) {
            String banReason = todayReportCount > 3 
                    ? "一天内累计被举报达到阈值，系统自动封禁"
                    : "短时间内被举报多条不同信息，系统自动封禁";
            
            Punishment punishment = punishmentService.createPunishment(
                    targetUserId,
                    Punishment.TYPE_BAN,
                    banReason,
                    Punishment.SOURCE_AUTO,
                    report.getId()
            );
            
            // 构建用户通知的extra信息，包含punishmentId供申诉使用
            String userExtraJson = null;
            try {
                Map<String, Object> userExtraMap = new HashMap<>();
                userExtraMap.put("punishmentId", punishment.getId());
                userExtraMap.put("sourceType", "auto_ban");
                userExtraJson = objectMapper.writeValueAsString(userExtraMap);
            } catch (Exception e) {
                log.warn("序列化用户通知额外信息失败", e);
            }
            
            // 发送通知给用户，末尾显示申诉按钮
            notificationService.sendNotification(
                    targetUserId,
                    "system",
                    "账号已被封禁",
                    "您因" + banReason + "，账号已被暂时封禁。如对结果有异议可点击此处继续申诉。",
                    "punishment",
                    punishment.getId(),
                    userExtraJson);
            
            // 获取未处理的举报内容，辅助管理员判断
            List<Report> pendingReports = reportMapper.selectList(new LambdaQueryWrapper<Report>()
                    .eq(Report::getTargetUserId, targetUserId)
                    .eq(Report::getStatus, "pending")
                    .orderByDesc(Report::getCreatedAt));
            
            StringBuilder pendingContent = new StringBuilder();
            for (int i = 0; i < Math.min(pendingReports.size(), 3); i++) {
                Report r = pendingReports.get(i);
                pendingContent.append("\n").append(i + 1).append(". ")
                        .append(describeLocation(r.getTargetType(), r.getTargetId()))
                        .append(": ").append(truncateContent(getReportedContent(r.getTargetType(), r.getTargetId())));
            }
            
            // 构建管理员通知的extra信息，包含punishmentId供跳转处理
            String adminExtraJson = null;
            try {
                Map<String, Object> adminExtraMap = new HashMap<>();
                adminExtraMap.put("punishmentId", punishment.getId());
                adminExtraMap.put("targetUserId", targetUserId);
                adminExtraMap.put("action", "revoke_punishment");
                adminExtraJson = objectMapper.writeValueAsString(adminExtraMap);
            } catch (Exception e) {
                log.warn("序列化管理员通知额外信息失败", e);
            }
            
            // 发送通知给所有管理员，末尾显示前往处理按钮
            List<User> admins = userMapper.selectList(new LambdaQueryWrapper<User>()
                    .eq(User::getRole, "admin"));
            for (User admin : admins) {
                notificationService.sendNotification(
                        admin.getId(),
                        "system",
                        "用户被系统自动封禁",
                        String.format("用户 #%d（%s）因%s被系统自动封禁。未处理举报内容：%s。点击前往处理可撤销该处罚。", 
                                targetUserId, targetUser.getUsername(), banReason, pendingContent),
                        "punishment",
                        punishment.getId(),
                        adminExtraJson);
            }
            
            log.info("用户因{}被自动封禁: userId={}, pendingReportCount={}, punishmentId={}", 
                    banReason, targetUserId, newCount, punishment.getId());
        }

        log.info("举报提交成功: reporterId={}, targetType={}, targetId={}, targetUserId={}, shouldHide={}",
                reporterId, targetType, targetId, targetUserId, shouldHide);
    }

    /**
     * 根据目标类型隐藏内容
     */
    private void hideTargetContent(String targetType, Long targetId) {
        try {
            switch (targetType) {
                case "drift_bottle": {
                    DriftBottle bottle = driftBottleMapper.selectById(targetId);
                    if (bottle != null && "drifting".equals(bottle.getStatus())) {
                        bottle.setStatus("sunk");
                        bottle.setSunkAt(LocalDateTime.now());
                        driftBottleMapper.updateById(bottle);
                    }
                    break;
                }
                case "bottle_reply": {
                    // 漂流瓶回复不单独隐藏，可在查询时过滤
                    break;
                }
                case "campfire_message": {
                    // 篝火消息不单独隐藏，可在查询时过滤
                    break;
                }
                case "letter": {
                    // 信件不单独隐藏
                    break;
                }
            }
        } catch (Exception e) {
            log.error("隐藏内容失败: targetType={}, targetId={}", targetType, targetId, e);
        }
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
        report.setAppealCount(0);
        
        Long targetUserId = report.getTargetUserId();
        User targetUser = userMapper.selectById(targetUserId);
        
        // 审核通过且有处罚时，创建处罚单
        if ("approved".equals(result) && StringUtils.hasText(penaltyType)) {
            // 将小写的处罚类型转换为大写
            String punishmentType = convertToUpperCaseType(penaltyType);
            String reason = "举报审核通过：" + describePenaltyType(penaltyType) + "。" + 
                    (StringUtils.hasText(reviewComment) ? "审核备注：" + reviewComment : "");
            
            Punishment punishment = punishmentService.createPunishment(
                    targetUserId,
                    punishmentType,
                    reason,
                    Punishment.SOURCE_REPORT,
                    reportId
            );
            report.setPunishmentId(punishment.getId());
            // 重新读取用户以获取最新版本号和状态（createPunishment可能已更新用户状态）
            targetUser = userMapper.selectById(targetUserId);
        }
        
        reportMapper.updateById(report);

        if (targetUser != null) {
            int currentCount = targetUser.getPendingReportCount() == null ? 0 : targetUser.getPendingReportCount();
            int newCount = Math.max(0, currentCount - 1);
            targetUser.setPendingReportCount(newCount);

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
                targetContent.append("如有异议请提交申诉，最多可申诉3次。");
            }

            String extraJson = null;
            if ("approved".equals(result)) {
                try {
                    Map<String, Object> extraMap = new HashMap<>();
                    extraMap.put("reportId", reportId);
                    extraMap.put("result", result);
                    extraMap.put("penaltyType", penaltyType);
                    extraMap.put("punishmentId", report.getPunishmentId());
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

        log.info("举报审核完成: reportId={}, reviewerId={}, result={}, penaltyType={}, punishmentId={}", 
                reportId, reviewerId, result, penaltyType, report.getPunishmentId());
    }

    private String convertToUpperCaseType(String penaltyType) {
        switch (penaltyType) {
            case "warning": return Punishment.TYPE_WARNING;
            case "mute_24h": return Punishment.TYPE_MUTE_24H;
            case "mute_7d": return Punishment.TYPE_MUTE_7D;
            case "ban": return Punishment.TYPE_BAN;
            default: return penaltyType.toUpperCase();
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
        vo.setPunishmentId(report.getPunishmentId());
        vo.setAppealCount(report.getAppealCount());
        vo.setReportedContent(getReportedContent(report.getTargetType(), report.getTargetId()));
        vo.setLocation(describeLocation(report.getTargetType(), report.getTargetId()));
        return vo;
    }

    @Override
    public PageResult<ReportGroupVO> getReportGroupList(String status, int page, int size) {
        int offset = (page - 1) * size;
        List<ReportMapper.ReportGroupSummary> summaries = reportMapper.selectReportGroupSummariesPage(status, offset, size);
        Long total = reportMapper.selectReportGroupCount(status);

        List<ReportGroupVO> list = summaries.stream().map(this::toGroupVO).collect(Collectors.toList());
        return new PageResult<>(list, total != null ? total : 0, page, size);
    }

    @Override
    public ReportGroupVO getReportGroupDetail(String targetType, Long targetId) {
        List<Report> reports = reportMapper.selectByTarget(targetType, targetId);
        if (reports.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "举报记录不存在");
        }

        ReportGroupVO vo = new ReportGroupVO();
        vo.setTargetType(targetType);
        vo.setTargetId(targetId);

        Report first = reports.get(0);
        vo.setTargetUserId(first.getTargetUserId());

        // 获取用户信息
        Map<Long, User> userMap = userMapper.selectBatchIds(
                reports.stream()
                        .flatMap(r -> java.util.Arrays.asList(r.getReporterId(), r.getTargetUserId()).stream())
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        User targetUser = userMap.get(first.getTargetUserId());
        vo.setTargetUsername(targetUser == null ? null : targetUser.getUsername());

        vo.setReportedContent(getReportedContent(targetType, targetId));
        vo.setLocation(describeLocation(targetType, targetId));
        vo.setReporterCount((int) reports.stream().map(Report::getReporterId).distinct().count());
        vo.setFirstReportedAt(reports.stream().map(Report::getCreatedAt).min(LocalDateTime::compareTo).orElse(null));
        vo.setLastReportedAt(reports.stream().map(Report::getCreatedAt).max(LocalDateTime::compareTo).orElse(null));

        // 判断分组状态
        boolean hasPending = reports.stream().anyMatch(r -> "pending".equals(r.getStatus()));
        vo.setGroupStatus(hasPending ? "pending" : "reviewed");

        // 判断分组结果（如果全部已审核）
        if (!hasPending) {
            boolean allApproved = reports.stream().allMatch(r -> "approved".equals(r.getResult()));
            boolean allRejected = reports.stream().allMatch(r -> "rejected".equals(r.getResult()));
            if (allApproved) {
                vo.setGroupResult("approved");
            } else if (allRejected) {
                vo.setGroupResult("rejected");
            }
        }

        vo.setReports(toVOList(reports));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewReportGroup(Long reviewerId, String targetType, Long targetId, String result, String reviewComment, String penaltyType) {
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

        List<Report> reports = reportMapper.selectByTarget(targetType, targetId);
        if (reports.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "举报记录不存在");
        }

        Long targetUserId = reports.get(0).getTargetUserId();
        User targetUser = userMapper.selectById(targetUserId);

        // 审核通过且有处罚时，创建处罚单（只创建一次）
        Punishment punishment = null;
        if ("approved".equals(result) && StringUtils.hasText(penaltyType)) {
            String punishmentType = convertToUpperCaseType(penaltyType);
            String reason = "举报审核通过：" + describePenaltyType(penaltyType) + "。" + 
                    (StringUtils.hasText(reviewComment) ? "审核备注：" + reviewComment : "");
            
            punishment = punishmentService.createPunishment(
                    targetUserId,
                    punishmentType,
                    reason,
                    Punishment.SOURCE_REPORT,
                    reports.get(0).getId()
            );
            // 重新读取用户以获取最新版本号和状态
            targetUser = userMapper.selectById(targetUserId);
        }

        // 更新所有关联的举报记录
        int pendingCount = 0;
        for (Report report : reports) {
            if ("pending".equals(report.getStatus())) {
                pendingCount++;
                report.setStatus("reviewed");
                report.setResult(result);
                report.setReviewerId(reviewerId);
                report.setReviewComment(reviewComment);
                report.setReviewedAt(LocalDateTime.now());
                report.setAppealCount(0);
                if (punishment != null) {
                    report.setPunishmentId(punishment.getId());
                }
                reportMapper.updateById(report);
            }
        }

        // 更新被举报人 pending_report_count
        if (targetUser != null) {
            int currentCount = targetUser.getPendingReportCount() == null ? 0 : targetUser.getPendingReportCount();
            int newCount = Math.max(0, currentCount - pendingCount);
            targetUser.setPendingReportCount(newCount);
            userMapper.updateById(targetUser);
        }

        // 获取所有举报人ID
        List<Long> reporterIds = reports.stream().map(Report::getReporterId).distinct().collect(Collectors.toList());

        String resultLabel = "approved".equals(result) ? "举报成立" : "举报驳回";
        String targetLabel = describeTargetType(targetType);
        String reportedContent = getReportedContent(targetType, targetId);
        String locationLabel = describeLocation(targetType, targetId);

        // 发送通知给所有举报人
        String reporterContent = String.format(
                "您举报的%s内容（场所：%s，内容：%s），审核结果：%s。%s",
                targetLabel, locationLabel, reportedContent, resultLabel, 
                StringUtils.hasText(reviewComment) ? "审核备注：" + reviewComment : "");

        for (Long reporterId : reporterIds) {
            try {
                notificationService.sendNotification(
                        reporterId,
                        "report_result",
                        "举报审核结果",
                        reporterContent,
                        "report",
                        reports.get(0).getId());
            } catch (Exception e) {
                log.error("发送举报通知失败: reporterId={}", reporterId, e);
            }
        }

        // 发送通知给被举报人
        if (targetUserId != null && targetUser != null) {
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
            if ("approved".equals(result) && punishment != null) {
                try {
                    Map<String, Object> extraMap = new HashMap<>();
                    extraMap.put("targetType", targetType);
                    extraMap.put("targetId", targetId);
                    extraMap.put("result", result);
                    extraMap.put("penaltyType", penaltyType);
                    extraMap.put("punishmentId", punishment.getId());
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
                    reports.get(0).getId(),
                    extraJson);
        }

        log.info("聚合举报审核完成: targetType={}, targetId={}, reviewerId={}, result={}, penaltyType={}, reporterCount={}", 
                targetType, targetId, reviewerId, result, penaltyType, reporterIds.size());
    }

    private ReportGroupVO toGroupVO(ReportMapper.ReportGroupSummary summary) {
        ReportGroupVO vo = new ReportGroupVO();
        vo.setTargetType(summary.getTargetType());
        vo.setTargetId(summary.getTargetId());
        vo.setTargetUserId(summary.getTargetUserId());
        
        // 获取用户信息
        User targetUser = userMapper.selectById(summary.getTargetUserId());
        vo.setTargetUsername(targetUser == null ? null : targetUser.getUsername());
        
        vo.setReportedContent(getReportedContent(summary.getTargetType(), summary.getTargetId()));
        vo.setLocation(describeLocation(summary.getTargetType(), summary.getTargetId()));
        vo.setReporterCount(summary.getReporterCount());
        vo.setFirstReportedAt(summary.getFirstReportedAt());
        vo.setLastReportedAt(summary.getLastReportedAt());
        vo.setGroupStatus(summary.getGroupStatus());
        vo.setGroupResult(summary.getGroupResult());
        return vo;
    }
}