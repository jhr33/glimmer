package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 意见与申诉视图
 */
@Data
public class FeedbackVO {

    private Long id;
    private Long userId;
    private String username;
    private String content;
    private String reply;
    private String status;
    private Long replyAdminId;
    private LocalDateTime repliedAt;
    private LocalDateTime createdAt;
    /** 类型: feedback(意见反馈)/appeal(申诉) */
    private String type;
    /** 关联举报ID（申诉时） */
    private Long reportId;
    /** 关联举报信息（申诉时） */
    private ReportVO report;
}
