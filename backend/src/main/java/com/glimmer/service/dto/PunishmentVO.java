package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 处罚单视图
 */
@Data
public class PunishmentVO {

    private Long id;
    private Long userId;
    /** 处罚类型: WARNING/MUTE_24H/MUTE_7D/BAN */
    private String type;
    /** 处罚原因 */
    private String reason;
    /** 状态: ACTIVE生效/REVOKED已撤销/EXPIRED已过期 */
    private String status;
    /** 处罚开始时间 */
    private LocalDateTime startAt;
    /** 处罚结束时间（永久封禁为NULL） */
    private LocalDateTime endAt;
    /** 来源类型 */
    private String sourceType;
    /** 来源ID */
    private Long sourceId;
    /** 处罚类型描述 */
    private String typeDescription;
    /** 状态描述 */
    private String statusDescription;
}
