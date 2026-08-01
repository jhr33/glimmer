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

    private String password;

    private String nickname;

    private String anonymousName;

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
