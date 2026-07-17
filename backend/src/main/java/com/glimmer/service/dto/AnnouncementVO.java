package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告详情视图
 */
@Data
public class AnnouncementVO {

    private Long id;
    private String title;
    private String content;
    private Long publisherId;
    private LocalDateTime createdAt;
}
