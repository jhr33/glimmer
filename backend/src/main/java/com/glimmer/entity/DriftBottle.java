package com.glimmer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 漂流瓶表（drift_bottle）
 */
@Data
@TableName("drift_bottle")
public class DriftBottle {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String content;

    /** 状态: drifting漂流中/sunk沉底 */
    private String status;

    /** 感谢者用户ID列表，JSON数组如[1,3,5] */
    private String thankedBy;

    private LocalDateTime createdAt;

    private LocalDateTime sunkAt;

    /**
     * 沉底原因（仅 status=sunk 时有意义）：
     * user = 用户主动沉底（通过 /bottles/{id}/sink 接口）
     * auto_report = 被多次举报自动隐藏（5分钟内3次举报）
     * 该字段用于在举报审核不成立时，可精准恢复"仅因自动举报隐藏"的漂流瓶。
     */
    private String hideReason;
}
