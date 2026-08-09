package com.glimmer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 篝火消息表（campfire_message）
 * <p>
 * content 字段明文存储（不加密）。
 * 理由：群聊性质是公开广场，而非私密倾诉，加密对隐私增益有限；
 *       聊天内容超过 24 小时仅前端隐藏，DB 记录保留用于举报审核 / 申诉取证；
 *       如未来需要审计追溯，明文便于快速检索。
 * 私密场景（AI 树洞、信件）仍加密存储（AiMessage / Letter entity）。
 */
@Data
@TableName("campfire_message")
public class CampfireMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long campfireId;

    private Long userId;

    private String anonymousName;

    private String content;

    private LocalDateTime createdAt;
}
