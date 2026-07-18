package com.glimmer.service;

import com.glimmer.common.response.PageResult;
import com.glimmer.service.dto.ReportVO;

/**
 * 举报服务
 * 见开发文档 §2.8、§4.11
 */
public interface ReportService {

    /**
     * 提交举报
     *
     * @param reporterId 举报人ID
     * @param targetType 目标类型: drift_bottle/bottle_reply/letter/campfire_message
     * @param targetId   目标资源ID
     * @param content    举报原因
     */
    void createReport(Long reporterId, String targetType, Long targetId, String content);

    /**
     * 我提交的举报列表（按 created_at 倒序）
     */
    PageResult<ReportVO> getMyReports(Long reporterId, int page, int size);

    /**
     * 举报列表（管理员），可按 status 筛选
     */
    PageResult<ReportVO> getReportList(String status, int page, int size);

    /**
     * 举报详情（管理员）
     */
    ReportVO getReportDetail(Long reportId);

    /**
     * 审核举报
     *
     * @param reviewerId    审核管理员ID
     * @param reportId      举报ID
     * @param result        审核结果: approved/rejected
     * @param reviewComment 审核备注
     * @param penaltyType   处罚类型: null/warning/mute_24h/mute_7d/ban
     */
    void reviewReport(Long reviewerId, Long reportId, String result, String reviewComment, String penaltyType);
}
