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

    /** 类型: free免费(每日重置)/paid付费 */
    private String conversationType;

    /** 已用轮次 (1轮 = 1用户消息 + 1AI回复) */
    private Integer quotaUsed;

    /** 轮次上限 */
    private Integer quotaLimit;

    /** 配额重置日期（仅free类型，每日00:00重置） */
    private java.time.LocalDate quotaResetDate;

    /** DeepSeek 生成的会话摘要（2-3句） */
    private String summary;

    /** 会话标题（首条消息前 20 字，用于列表展示） */
    private String title;
}
