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
     * 提交申诉
     *
     * @param userId   用户ID
     * @param reportId 举报ID
     * @param content  申诉内容
     */
    void createAppeal(Long userId, Long reportId, String content);

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

    /**
     * 申诉列表（管理员），可按状态筛选
     */
    PageResult<FeedbackVO> getAppealList(String status, int page, int size);

    /**
     * 申诉详情（管理员），包含关联的举报信息
     */
    FeedbackVO getAppealDetail(Long feedbackId);

    /**
     * 审核申诉
     *
     * @param adminId    审核管理员ID
     * @param feedbackId 申诉ID
     * @param result     审核结果: approved(申诉成功)/rejected(申诉失败)
     * @param reply      审核回复内容
     * @param newPenaltyType 新的处罚类型（申诉成功时使用，null表示解除处罚）
     */
    void reviewAppeal(Long adminId, Long feedbackId, String result, String reply, String newPenaltyType);
}
