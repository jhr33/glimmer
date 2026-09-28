package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息视图（不含密码等敏感字段）
 */
@Data
public class UserVO {

    private Long id;
    /**
     * 对外账号 UID（= 10000 + id，运行时计算不入库）
     * 替代 username 作为对外展示与登录账号
     */
    private Long uid;
    private String username;
    /** 手机号（脱敏展示：138****5678） */
    private String phone;
    private String nickname;
    /** 自定义头像URL（null = 系统默认头像，前端按 userId 取默认头像） */
    private String avatarUrl;
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
