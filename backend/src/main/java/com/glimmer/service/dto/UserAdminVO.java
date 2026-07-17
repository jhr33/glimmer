package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员视角的用户视图（含状态、角色等管理字段，不含密码）
 */
@Data
public class UserAdminVO {

    private Long id;
    private String username;
    private String nickname;
    private String anonymousName;
    private String role;
    private String status;
    private Integer tokenBalance;
    private Integer totalFirefly;
    private Integer fireflyBalance;
    private Integer totalSignDays;
    private Integer pendingReportCount;
    private LocalDateTime createdAt;
}
