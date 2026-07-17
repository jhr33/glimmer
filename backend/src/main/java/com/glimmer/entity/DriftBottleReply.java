package com.glimmer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 漂流瓶回复表（drift_bottle_reply）
 */
@Data
@TableName("drift_bottle_reply")
public class DriftBottleReply {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bottleId;

    private Long userId;

    private String content;

    /** 感谢者用户ID列表 */
    private String thankedBy;

    private LocalDateTime createdAt;
}
