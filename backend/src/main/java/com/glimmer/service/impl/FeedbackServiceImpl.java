package com.glimmer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.response.PageResult;
import com.glimmer.entity.Campfire;
import com.glimmer.entity.CampfireMessage;
import com.glimmer.entity.DriftBottle;
import com.glimmer.entity.DriftBottleReply;
import com.glimmer.entity.Feedback;
import com.glimmer.entity.Letter;
import com.glimmer.entity.Punishment;
import com.glimmer.entity.Report;
import com.glimmer.entity.User;
import com.glimmer.mapper.CampfireMapper;
import com.glimmer.mapper.CampfireMessageMapper;
import com.glimmer.mapper.DriftBottleMapper;
import com.glimmer.mapper.DriftBottleReplyMapper;
import com.glimmer.mapper.FeedbackMapper;
import com.glimmer.mapper.LetterMapper;
import com.glimmer.mapper.ReportMapper;
import com.glimmer.mapper.UserMapper;
import com.glimmer.service.FeedbackService;
import com.glimmer.service.NotificationService;
import com.glimmer.service.PunishmentService;
import com.glimmer.mapper.PunishmentMapper;
import com.glimmer.service.dto.AppealCheckResult;
import com.glimmer.service.dto.FeedbackVO;
import com.glimmer.service.dto.PunishmentVO;
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
    private final CampfireMapper campfireMapper;
    private final PunishmentService punishmentService;
    private final PunishmentMapper punishmentMapper;

    public FeedbackServiceImpl(FeedbackMapper feedbackMapper, UserMapper userMapper,
                               NotificationService notificationService, ReportMapper reportMapper,
                               DriftBottleMapper driftBottleMapper,
                               DriftBottleReplyMapper driftBottleReplyMapper,
                               LetterMapper letterMapper,
                               CampfireMessageMapper campfireMessageMapper,
                               CampfireMapper campfireMapper,
                               PunishmentService punishmentService,
                               PunishmentMapper punishmentMapper) {
        this.feedbackMapper = feedbackMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
        this.reportMapper = reportMapper;
        this.driftBottleMapper = driftBottleMapper;
        this.driftBottleReplyMapper = driftBottleReplyMapper;
        this.letterMapper = letterMapper;
        this.campfireMessageMapper = campfireMessageMapper;
        this.campfireMapper = campfireMapper;
        this.punishmentService = punishmentService;
        this.punishmentMapper = punishmentMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createFeedback(Long userId, String content) {
        createFeedback(userId, content, null, null, "feedback");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createAppeal(Long userId, Long reportId, Long punishmentId, String content) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        // 优先通过 punishmentId 验证处罚存在且属于当前用户
        Long targetReportId = reportId;
        if (punishmentId != null) {
            Punishment punishment = punishmentService.getById(punishmentId);
            if (punishment == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "处罚单不存在");
            }
            if (!userId.equals(punishment.getUserId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "只能申诉自己的处罚");
            }
            // 检查处罚状态：REVOKED（已撤销）或 WARNING（警告）类型不可申诉
            if (Punishment.STATUS_REVOKED.equals(punishment.getStatus()) 
                    || Punishment.TYPE_WARNING.equals(punishment.getType())) {
                throw new BusinessException(ErrorCode.CONFLICT, "当前申诉已通过，处罚已解除");
            }
            if (!Punishment.STATUS_ACTIVE.equals(punishment.getStatus())) {
                throw new BusinessException(ErrorCode.CONFLICT, "该处罚已撤销或过期");
            }
            
            // 尝试从处罚单关联的来源查找举报ID
            if (targetReportId == null && punishment.getSourceId() != null && 
                Punishment.SOURCE_REPORT.equals(punishment.getSourceType())) {
                targetReportId = punishment.getSourceId();
            }

            // 检查该处罚单是否有正在审核中的申诉
            long pendingAppealCount = feedbackMapper.selectCount(new LambdaQueryWrapper<Feedback>()
                    .eq(Feedback::getType, "appeal")
                    .eq(Feedback::getPunishmentId, punishmentId)
                    .eq(Feedback::getStatus, "pending"));
            if (pendingAppealCount > 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "您已针对该信息提交申诉，正在等待审核");
            }
            
            // 检查该处罚单的申诉次数
            long appealCountForPunishment = feedbackMapper.selectCount(new LambdaQueryWrapper<Feedback>()
                    .eq(Feedback::getType, "appeal")
                    .eq(Feedback::getPunishmentId, punishmentId));
            
            // 第三次申诉后若处罚仍未取消，则不可再申诉
            if (appealCountForPunishment >= MAX_APPEALS_PER_REPORT) {
                throw new BusinessException(ErrorCode.CONFLICT, "该处罚单申诉次数已达上限（3次）");
            }
        }

        // 通过 reportId 验证举报存在且属于当前用户
        if (targetReportId != null) {
            Report report = reportMapper.selectById(targetReportId);
            if (report == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "举报不存在");
            }
            if (!userId.equals(report.getTargetUserId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "只能申诉自己被举报的内容");
            }
            if (!"approved".equals(report.getResult())) {
                throw new BusinessException(ErrorCode.CONFLICT, "只有举报成立的内容才能申诉");
            }
        }

        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        long todayAppealCount = feedbackMapper.selectCount(new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getUserId, userId)
                .eq(Feedback::getType, "appeal")
                .ge(Feedback::getCreatedAt, todayStart));
        if (todayAppealCount >= MAX_APPEALS_PER_DAY) {
            throw new BusinessException(ErrorCode.CONFLICT, "今日申诉次数已达上限（7次）");
        }

        createFeedback(userId, content, targetReportId, punishmentId, "appeal");
        log.info("申诉提交成功: userId={}, reportId={}, punishmentId={}", userId, targetReportId, punishmentId);
    }

    private void createFeedback(Long userId, String content, Long reportId, Long punishmentId, String type) {
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
        feedback.setPunishmentId(punishmentId);
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
        vo.setPunishmentId(feedback.getPunishmentId());
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
                reportVO.setPunishmentId(report.getPunishmentId());
                reportVO.setAppealCount(report.getAppealCount());
                reportVO.setReportedContent(getReportedContent(report.getTargetType(), report.getTargetId()));
                reportVO.setLocation(describeLocation(report.getTargetType(), report.getTargetId()));
                vo.setReport(reportVO);
            }
        }
        // 查询处罚单信息
        if (feedback.getPunishmentId() != null) {
            Punishment punishment = punishmentMapper.selectById(feedback.getPunishmentId());
            if (punishment != null) {
                PunishmentVO punishmentVO = new PunishmentVO();
                punishmentVO.setId(punishment.getId());
                punishmentVO.setUserId(punishment.getUserId());
                punishmentVO.setType(punishment.getType());
                punishmentVO.setTypeDescription(describePunishmentType(punishment.getType()));
                punishmentVO.setReason(punishment.getReason());
                punishmentVO.setStatus(punishment.getStatus());
                punishmentVO.setStatusDescription(describePunishmentStatus(punishment.getStatus()));
                punishmentVO.setStartAt(punishment.getStartAt());
                punishmentVO.setEndAt(punishment.getEndAt());
                punishmentVO.setSourceType(punishment.getSourceType());
                punishmentVO.setSourceId(punishment.getSourceId());
                vo.setPunishment(punishmentVO);
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
            // 申诉通过，撤销或变更处罚
            Long targetUserId = feedback.getUserId();
            
            // 获取要撤销的处罚单ID（优先使用feedback关联的punishmentId）
            Long punishmentId = feedback.getPunishmentId();
            
            // 如果没有直接关联处罚单，通过举报记录查找
            if (punishmentId == null && feedback.getReportId() != null) {
                Report report = reportMapper.selectById(feedback.getReportId());
                if (report != null) {
                    targetUserId = report.getTargetUserId();
                    punishmentId = report.getPunishmentId();
                    
                    // 如果举报没有直接关联处罚单，通过来源查询
                    if (punishmentId == null) {
                        List<Punishment> punishments = punishmentService.getBySource(
                                report.getId(), Punishment.SOURCE_REPORT);
                        if (!punishments.isEmpty()) {
                            punishmentId = punishments.get(0).getId();
                        }
                    }
                }
            }
            
            log.info("申诉审核通过: feedbackId={}, userId={}, punishmentId={}, newPenaltyType={}", 
                    feedbackId, targetUserId, punishmentId, newPenaltyType);
            
            if (!StringUtils.hasText(newPenaltyType)) {
                // 解除处罚：撤销对应的处罚单
                if (punishmentId != null) {
                    punishmentService.revokePunishment(punishmentId);
                    log.info("处罚单已撤销: punishmentId={}", punishmentId);
                }
            } else {
                // 变更处罚类型：先撤销原处罚，再创建新处罚
                if (punishmentId != null) {
                    punishmentService.revokePunishment(punishmentId);
                    log.info("原处罚单已撤销: punishmentId={}", punishmentId);
                }
                
                // 创建新的处罚单
                String punishmentType = convertToUpperCaseType(newPenaltyType);
                String reason = "申诉审核通过，变更处罚为：" + describePenaltyType(newPenaltyType) + 
                        (StringUtils.hasText(reply) ? "。审核备注：" + reply : "");
                
                Punishment newPunishment = punishmentService.createPunishment(
                        targetUserId,
                        punishmentType,
                        reason,
                        Punishment.SOURCE_ADMIN,
                        feedbackId
                );
                
                // 将新处罚单ID回填到feedback
                feedback.setPunishmentId(newPunishment.getId());
                feedbackMapper.updateById(feedback);
                
                log.info("新处罚单已创建: punishmentId={}, type={}", newPunishment.getId(), newPenaltyType);
            }

            // 获取被举报内容描述
            String reportedContent = getAppealReportedContent(feedback);
            String location = getAppealLocation(feedback);
            
            // 计算当前申诉是第几次（包括当前这次）
            long appealCount = feedbackMapper.selectCount(new LambdaQueryWrapper<Feedback>()
                    .eq(Feedback::getType, "appeal")
                    .eq(Feedback::getPunishmentId, feedback.getPunishmentId()));
            
            // 判断处罚是否已完全解除（无禁言处罚）
            boolean isPunishmentRevoked = !StringUtils.hasText(newPenaltyType);
            
            // 发送通知（独立 try-catch，避免通知失败影响用户状态更新）
            try {
                String resultContent;
                if (isPunishmentRevoked) {
                    // 处罚已解除，不显示继续申诉
                    resultContent = String.format("您对\"%s\"进行的申诉已通过，处罚已解除。%s",
                            reportedContent,
                            (StringUtils.hasText(reply) ? "审核备注：" + reply : ""));
                } else if (appealCount >= MAX_APPEALS_PER_REPORT) {
                    // 第三次申诉且处罚未解除，显示上限提示
                    resultContent = String.format("您对\"%s\"进行的申诉已到达上限，处罚不再变更。%s",
                            reportedContent,
                            (StringUtils.hasText(reply) ? "审核备注：" + reply : ""));
                } else {
                    // 处罚未完全解除，可继续申诉
                    resultContent = String.format("您对\"%s\"进行的申诉已通过，处罚已变更为%s。%s如对结果有异议可点击此处继续申诉。",
                            reportedContent,
                            describePenaltyType(newPenaltyType),
                            (StringUtils.hasText(reply) ? "审核备注：" + reply : ""));
                }
                // 构建extra信息，包含punishmentId和reportId供前端继续申诉使用
                String extraInfo = buildAppealExtra(feedback.getPunishmentId(), feedback.getReportId());
                notificationService.sendNotification(
                        feedback.getUserId(),
                        "appeal_result",
                        "申诉审核结果",
                        resultContent,
                        "feedback",
                        feedbackId,
                        extraInfo);
            } catch (Exception e) {
                log.error("发送申诉通知失败: userId={}, feedbackId={}", feedback.getUserId(), feedbackId, e);
            }
        } else {
            // 获取被举报内容描述
            String reportedContent = getAppealReportedContent(feedback);
            
            // 计算当前申诉是第几次（包括当前这次）
            long appealCount = feedbackMapper.selectCount(new LambdaQueryWrapper<Feedback>()
                    .eq(Feedback::getType, "appeal")
                    .eq(Feedback::getPunishmentId, feedback.getPunishmentId()));
            
            // 发送通知（独立 try-catch，避免通知失败影响申诉状态更新）
            try {
                String resultContent;
                if (appealCount >= MAX_APPEALS_PER_REPORT) {
                    // 第三次申诉未通过，显示上限提示
                    resultContent = String.format("您对\"%s\"进行的申诉已到达上限，处罚不再变更。%s",
                            reportedContent,
                            (StringUtils.hasText(reply) ? "审核备注：" + reply : ""));
                } else {
                    // 可继续申诉
                    resultContent = String.format("您对\"%s\"进行的申诉未通过。%s如对结果有异议可点击此处继续申诉。",
                            reportedContent,
                            (StringUtils.hasText(reply) ? "审核备注：" + reply : ""));
                }
                // 构建extra信息，包含punishmentId和reportId供前端继续申诉使用
                String extraInfo = buildAppealExtra(feedback.getPunishmentId(), feedback.getReportId());
                notificationService.sendNotification(
                        feedback.getUserId(),
                        "appeal_result",
                        "申诉审核结果",
                        resultContent,
                        "feedback",
                        feedbackId,
                        extraInfo);
            } catch (Exception e) {
                log.error("发送申诉通知失败: userId={}, feedbackId={}", feedback.getUserId(), feedbackId, e);
            }
        }

        log.info("申诉审核完成: feedbackId={}, result={}, adminId={}", feedbackId, result, adminId);
    }

    /**
     * 构建申诉通知的extra信息，包含punishmentId和reportId供前端继续申诉使用
     */
    private String buildAppealExtra(Long punishmentId, Long reportId) {
        try {
            Map<String, Object> extraMap = new HashMap<>();
            if (punishmentId != null) {
                extraMap.put("punishmentId", punishmentId);
            }
            if (reportId != null) {
                extraMap.put("reportId", reportId);
            }
            if (extraMap.isEmpty()) {
                return null;
            }
            return com.fasterxml.jackson.databind.json.JsonMapper.builder()
                    .build()
                    .writeValueAsString(extraMap);
        } catch (Exception e) {
            log.warn("构建申诉extra信息失败", e);
            return null;
        }
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

    private String describePunishmentType(String type) {
        if (type == null) return "未知";
        switch (type.toUpperCase()) {
            case "WARNING": return "警告";
            case "MUTE_24H": return "禁言24小时";
            case "MUTE_7D": return "禁言7天";
            case "BAN": return "永久封禁";
            default: return type;
        }
    }

    private String describePunishmentStatus(String status) {
        if (status == null) return "未知";
        switch (status.toUpperCase()) {
            case "ACTIVE": return "生效中";
            case "REVOKED": return "已撤销";
            case "EXPIRED": return "已过期";
            default: return status;
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
                        return "漂流瓶广场";
                    }
                    return "漂流瓶广场";
                }
                case "letter": {
                    return "私信";
                }
                case "campfire_message": {
                    CampfireMessage message = campfireMessageMapper.selectById(targetId);
                    if (message != null && message.getCampfireId() != null) {
                        Campfire campfire = campfireMapper.selectById(message.getCampfireId());
                        if (campfire != null && campfire.getName() != null) {
                            return "篝火：" + campfire.getName();
                        }
                        return "篝火";
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

    /**
     * 获取申诉关联的被举报内容描述
     */
    private String getAppealReportedContent(Feedback feedback) {
        try {
            // 通过处罚单查找
            if (feedback.getPunishmentId() != null) {
                Punishment punishment = punishmentService.getById(feedback.getPunishmentId());
                if (punishment != null && Punishment.SOURCE_REPORT.equals(punishment.getSourceType()) && punishment.getSourceId() != null) {
                    Report report = reportMapper.selectById(punishment.getSourceId());
                    if (report != null) {
                        return getReportedContent(report.getTargetType(), report.getTargetId());
                    }
                }
            }
            
            // 通过举报ID查找
            if (feedback.getReportId() != null) {
                Report report = reportMapper.selectById(feedback.getReportId());
                if (report != null) {
                    return getReportedContent(report.getTargetType(), report.getTargetId());
                }
            }
            
            return "被举报内容";
        } catch (Exception e) {
            log.warn("获取申诉关联内容失败: feedbackId={}", feedback.getId(), e);
            return "被举报内容";
        }
    }

    /**
     * 获取申诉关联的发言场所
     */
    private String getAppealLocation(Feedback feedback) {
        try {
            // 通过处罚单查找
            if (feedback.getPunishmentId() != null) {
                Punishment punishment = punishmentService.getById(feedback.getPunishmentId());
                if (punishment != null && Punishment.SOURCE_REPORT.equals(punishment.getSourceType()) && punishment.getSourceId() != null) {
                    Report report = reportMapper.selectById(punishment.getSourceId());
                    if (report != null) {
                        return describeLocation(report.getTargetType(), report.getTargetId());
                    }
                }
            }
            
            // 通过举报ID查找
            if (feedback.getReportId() != null) {
                Report report = reportMapper.selectById(feedback.getReportId());
                if (report != null) {
                    return describeLocation(report.getTargetType(), report.getTargetId());
                }
            }
            
            return "";
        } catch (Exception e) {
            log.warn("获取申诉关联场所失败: feedbackId={}", feedback.getId(), e);
            return "";
        }
    }

    @Override
    public boolean hasPendingAppeal(Long punishmentId) {
        if (punishmentId == null) {
            return false;
        }
        long count = feedbackMapper.selectCount(new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getType, "appeal")
                .eq(Feedback::getPunishmentId, punishmentId)
                .eq(Feedback::getStatus, "pending"));
        return count > 0;
    }

    @Override
    public AppealCheckResult checkAppealEligibility(Long punishmentId) {
        AppealCheckResult result = new AppealCheckResult();
        result.setCanAppeal(true);
        
        if (punishmentId == null) {
            result.setCanAppeal(false);
            result.setReason("缺少处罚单信息");
            return result;
        }
        
        // 检查是否有待处理申诉
        long pendingCount = feedbackMapper.selectCount(new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getType, "appeal")
                .eq(Feedback::getPunishmentId, punishmentId)
                .eq(Feedback::getStatus, "pending"));
        result.setHasPendingAppeal(pendingCount > 0);
        
        if (pendingCount > 0) {
            result.setCanAppeal(false);
            result.setReason("已有待处理申诉，请等待管理员审核");
        }
        
        // 检查处罚状态
        Punishment punishment = punishmentMapper.selectById(punishmentId);
        if (punishment != null) {
            result.setPunishmentStatus(punishment.getStatus());
            result.setPunishmentStatusDesc(describePunishmentStatus(punishment.getStatus()));
            
            // 处罚已撤销或已过期
            if (Punishment.STATUS_REVOKED.equals(punishment.getStatus()) 
                    || Punishment.STATUS_EXPIRED.equals(punishment.getStatus())) {
                result.setCanAppeal(false);
                result.setReason("处罚已解除，无需申诉");
            }
        }
        
        return result;
    }
}
