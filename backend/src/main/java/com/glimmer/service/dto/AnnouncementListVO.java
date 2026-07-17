package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告列表摘要视图（不含正文）
 */
@Data
public class AnnouncementListVO {

    private Long id;
    private String title;
    private LocalDateTime createdAt;
}
