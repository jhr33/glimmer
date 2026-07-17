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
}
