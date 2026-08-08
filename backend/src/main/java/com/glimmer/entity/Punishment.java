package com.glimmer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 处罚单表（punishment）
 */
@Data
@TableName("punishment")
public class Punishment {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被处罚用户ID */
    private Long userId;

    /** 处罚类型: WARNING/MUTE_24H/MUTE_7D/BAN */
    private String type;

    /** 处罚原因 */
    private String reason;

    /** 来源: ADMIN(管理员手动)/REPORT(举报通过)/AUTO(系统自动) */
    private String sourceType;

    /** 来源ID（如report_id或admin_operation_id） */
    private Long sourceId;

    /** 处罚开始时间 */
    private LocalDateTime startAt;

    /** 处罚结束时间（永久封禁为NULL） */
    private LocalDateTime endAt;

    /** 状态: ACTIVE生效/REVOKED已撤销/EXPIRED已过期 */
    private String status;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // 处罚类型常量
    public static final String TYPE_WARNING = "WARNING";
    public static final String TYPE_MUTE_24H = "MUTE_24H";
    public static final String TYPE_MUTE_7D = "MUTE_7D";
    public static final String TYPE_BAN = "BAN";

    // 来源类型常量
    public static final String SOURCE_ADMIN = "ADMIN";
    public static final String SOURCE_REPORT = "REPORT";
    public static final String SOURCE_AUTO = "AUTO";

    // 状态常量
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REVOKED = "REVOKED";
    public static final String STATUS_EXPIRED = "EXPIRED";
}