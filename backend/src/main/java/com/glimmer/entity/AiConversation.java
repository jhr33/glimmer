package com.glimmer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI对话表（ai_conversation）
 */
@Data
@TableName("ai_conversation")
public class AiConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 状态: active活跃/closed用户关闭/timeout超时关闭 */
    private String status;

    private Integer messageCount;

    private Integer maxMessages;

    private LocalDateTime startedAt;

    private LocalDateTime lastActiveAt;
}
