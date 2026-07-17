package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 漂流瓶回复视图
 */
@Data
public class BottleReplyVO {

    private Long id;
    private Long bottleId;
    private Long userId;
    private String content;
    private LocalDateTime createdAt;
}
