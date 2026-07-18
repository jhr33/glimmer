package com.glimmer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 意见信/反馈表（feedback）
 */
@Data
@TableName("feedback")
public class Feedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String content;

    private String reply;

    /** 状态: pending待回复/replied已回复 */
    private String status;

    private Long replyAdminId;

    private LocalDateTime repliedAt;

    /** 类型: feedback(意见反馈)/appeal(申诉) */
    private String type;

    /** 关联举报ID（申诉时必填） */
    private Long reportId;

    private LocalDateTime createdAt;
}
