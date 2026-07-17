package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员视角的公告视图（含状态、正文）
 */
@Data
public class AnnouncementAdminVO {

    private Long id;
    private String title;
    private String content;
    private Long publisherId;
    private String status;
    private LocalDateTime createdAt;
}
