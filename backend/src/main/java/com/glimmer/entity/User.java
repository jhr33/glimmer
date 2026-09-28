package com.glimmer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表（user）
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** 手机号（唯一索引，可空；用于手机号登录/注册） */
    private String phone;

    private String password;

    /** 最近一次修改密码时间（Asia/Shanghai 时区），用于限制一天只能修改一次 */
    private LocalDateTime passwordChangedAt;

    private String nickname;

    /** 自定义头像URL（NULL = 系统默认头像，前端按 userId 取默认头像之一） */
    private String avatarUrl;

    private String anonymousName;

    /** 匿名昵称过期时间：超过后下次使用匿名身份时重新生成（24小时轮换） */
    private LocalDateTime anonymousNameExpiresAt;

    private String role;

    private String status;

    private Integer tokenBalance;

    private Integer totalFirefly;

    private Integer fireflyBalance;

    private Integer totalSignDays;

    private Integer pendingReportCount;

    /** AI 记忆关键信息 (JSON 字符串, 如 {"studying":"考研","mood":"压力大"}) */
    private String aiContext;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
