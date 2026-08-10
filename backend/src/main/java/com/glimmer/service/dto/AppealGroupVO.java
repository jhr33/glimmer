package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 申诉分组视图：将同一举报单的多次申诉合并为一条记录。
 * <p>
 * 列表展示被举报内容摘要与申诉总体情况，点击查看详情可展开所有申诉记录。
 */
@Data
public class AppealGroupVO {

    /** 分组键（reportId 优先，为空时用 punishmentId，都为空时用 "none_" + 首条 feedbackId） */
    private String groupKey;

    /** 关联举报ID */
    private Long reportId;

    /** 关联处罚单ID（取最新一条申诉的） */
    private Long punishmentId;

    /** 被举报内容摘要 */
    private String reportedContent;

    /** 被举报内容类型: drift_bottle / bottle_reply / letter / campfire_message */
    private String targetType;

    /** 发言场所描述 */
    private String location;

    /** 该组申诉总次数 */
    private Integer appealCount;

    /** 最新一次申诉的状态: pending 待审核 / replied 已回复 */
    private String latestStatus;

    /** 最新一次申诉时间 */
    private LocalDateTime latestCreatedAt;

    /** 首次申诉时间 */
    private LocalDateTime firstCreatedAt;

    /** 该组下所有申诉记录（按时间正序，详情展开时使用） */
    private List<FeedbackVO> appeals;

    /** 关联处罚单信息（原始处罚详情，每条申诉的 punishment 字段显示申诉后处罚结果） */
    private PunishmentVO punishment;
}
