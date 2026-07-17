package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 信件视图
 */
@Data
public class LetterVO {

    private Long id;
    private Long senderId;
    private Long receiverId;
    private Long parentId;
    /** 来源类型: direct直接写信/bottle_reply漂流瓶回复延伸 */
    private String sourceType;
    private Long sourceId;
    private String content;
    private Integer isReplied;
    private Integer isRead;
    private LocalDateTime createdAt;

    /** 发送者昵称（用于显示） */
private String senderNickname;
/** 接收者昵称（用于发件箱显示） */
private String receiverNickname;

/** 来源漂流瓶内容（bottle_reply类型时） */
private String sourceBottleContent;
/** 来源漂流瓶回复内容（bottle_reply类型时） */
private String sourceReplyContent;
}
