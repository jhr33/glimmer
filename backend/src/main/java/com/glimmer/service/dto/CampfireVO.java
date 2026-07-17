package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 篝火视图
 */
@Data
public class CampfireVO {

    private Long id;
    private String name;
    /** 类型: default系统默认/custom用户创建 */
    private String type;
    private Integer maxMembers;
    private Long creatorId;
    private String status;
    private LocalDateTime createdAt;
    /** 成员数 */
    private Long memberCount;
}
