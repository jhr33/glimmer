package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 篝火成员视图
 */
@Data
public class CampfireMemberVO {

    private Long id;
    private Long campfireId;
    private Long userId;

    /**
     * 成员在该篝火内的显示名称
     * nickname 模式：用户的 nickname
     * anonymous 模式：按 userId+campfireId+日期 生成的稳定匿名名称
     */
    private String anonymousName;

    private LocalDateTime joinedAt;
}
