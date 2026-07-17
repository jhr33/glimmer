package com.glimmer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.response.PageResult;
import com.glimmer.entity.Feedback;
import com.glimmer.entity.User;
import com.glimmer.mapper.FeedbackMapper;
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

    private final FeedbackMapper feedbackMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    public FeedbackServiceImpl(FeedbackMapper feedbackMapper, UserMapper userMapper,
                               NotificationService notificationService) {
        this.feedbackMapper = feedbackMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createFeedback(Long userId, String content) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setContent(content);
        feedback.setStatus("pending");
        feedbackMapper.insert(feedback);
        log.info("意见信提交成功: userId={}, feedbackId={}", userId, feedback.getId());
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
        return vo;
    }
}
