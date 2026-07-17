package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 意见信视图
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
}
