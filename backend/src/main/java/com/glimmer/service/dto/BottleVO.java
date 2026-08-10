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

    /**
     * 瓶子作者的匿名名称（user.anonymousName）
     */
    private String anonymousName;

    private String status;
    private LocalDateTime createdAt;

    /** 回复数（我的瓶子列表用） */
    private Integer replyCount;

    /**
     * 是否为 AI 机器人（回音）扔的瓶子
     * true 时前端不显示举报按钮
     */
    private Boolean isFromBot;
}
