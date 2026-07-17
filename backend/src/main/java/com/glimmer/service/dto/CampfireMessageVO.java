package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 篝火消息视图
 */
@Data
public class CampfireMessageVO {

    private Long id;
    private Long campfireId;
    private Long userId;
    private String anonymousName;
    private String content;
    private LocalDateTime createdAt;
}
