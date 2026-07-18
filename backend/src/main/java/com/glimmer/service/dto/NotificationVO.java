package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知视图
 */
@Data
public class NotificationVO {

    private Long id;
    /** 类型: report_result/feedback_reply/announcement/system */
    private String type;
    private String title;
    private String content;
    private String refType;
    private Long refId;
    private String extra;
    private Integer isRead;
    private LocalDateTime createdAt;
}
