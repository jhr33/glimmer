package com.glimmer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知表（notification）
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 类型: report_result/feedback_reply/announcement/system */
    private String type;

    private String title;

    private String content;

    private String refType;

    private Long refId;

    /** 额外信息（JSON格式），如举报审核结果、处罚类型等 */
    private String extra;

    /** 是否已读: 0未读/1已读 */
    private Integer isRead;

    private LocalDateTime createdAt;
}
