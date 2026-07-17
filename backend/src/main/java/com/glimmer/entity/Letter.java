package com.glimmer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 信件表（letter）
 */
@Data
@TableName("letter")
public class Letter {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long senderId;

    private Long receiverId;

    private Long parentId;

    /** 来源类型: direct直接写信/bottle_reply漂流瓶回复延伸 */
    private String sourceType;

    private Long sourceId;

    private String content;

    /** 是否已被回复（0未回复/1已回复） */
    private Integer isReplied;

    /** 是否已读（0未读/1已读） */
    private Integer isRead;

    /** 感谢者用户ID列表 */
    private String thankedBy;

    private LocalDateTime createdAt;
}
