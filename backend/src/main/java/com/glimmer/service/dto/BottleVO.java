package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 漂流瓶详情视图
 */
@Data
public class BottleVO {

    private Long id;
    private String content;
    private Long userId;
    private String status;
    private LocalDateTime createdAt;

    /** 回复数（我的瓶子列表用） */
    private Integer replyCount;
}
