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

    /** 处罚类型: null/空(无处罚)/warning(警告)/mute_24h(禁言24小时)/mute_7d(禁言7天)/ban(永久封禁) */
    private String muteType;

    /** 禁言结束时间（永久封禁时为null） */
    private LocalDateTime muteEndTime;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
