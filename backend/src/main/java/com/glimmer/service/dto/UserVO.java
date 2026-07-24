package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息视图（不含密码等敏感字段）
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String anonymousName;
    private String role;
    private String status;
    private String muteType;
    private LocalDateTime muteEndTime;
    private Integer tokenBalance;
    private Integer totalFirefly;
    private Integer fireflyBalance;
    private Integer totalSignDays;
}
