package com.glimmer.service.dto;

import lombok.Data;

/**
 * 申诉检查结果
 */
@Data
public class AppealCheckResult {

    /** 是否存在待处理的申诉 */
    private boolean hasPendingAppeal;

    /** 处罚单状态: ACTIVE/REVOKED/EXPIRED */
    private String punishmentStatus;

    /** 处罚单状态描述 */
    private String punishmentStatusDesc;

    /** 是否可以申诉（无待处理申诉且处罚生效中） */
    private boolean canAppeal;

    /** 不可申诉的原因 */
    private String reason;
}
