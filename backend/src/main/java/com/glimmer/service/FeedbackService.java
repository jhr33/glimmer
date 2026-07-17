package com.glimmer.service;

import com.glimmer.common.response.PageResult;
import com.glimmer.service.dto.FeedbackVO;

/**
 * 意见信服务
 * 见开发文档 §2.9、§4.12
 */
public interface FeedbackService {

    /**
     * 提交意见信
     */
    void createFeedback(Long userId, String content);

    /**
     * 我的意见信列表（按 created_at 倒序）
     */
    PageResult<FeedbackVO> getMyFeedbacks(Long userId, int page, int size);

    /**
     * 意见信详情（仅提交者本人）
     */
    FeedbackVO getFeedbackDetail(Long userId, Long feedbackId);

    /**
     * 意见信列表（管理员），可按 status 筛选
     */
    PageResult<FeedbackVO> getFeedbackList(String status, int page, int size);

    /**
     * 意见信详情（管理员）
     */
    FeedbackVO getFeedbackDetail(Long feedbackId);

    /**
     * 回复意见信
     *
     * @param adminId    回复管理员ID
     * @param feedbackId 意见信ID
     * @param reply      回复内容
     */
    void replyFeedback(Long adminId, Long feedbackId, String reply);
}
