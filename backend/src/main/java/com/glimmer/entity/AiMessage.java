package com.glimmer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.glimmer.config.EncryptedFieldTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI消息表（ai_message）
 */
@Data
@TableName(value = "ai_message", autoResultMap = true)
public class AiMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    /** 角色: user用户/ai助手 */
    private String role;

    /** 消息内容（DB 中为 AES-GCM 密文，业务层读写明文） */
    @TableField(typeHandler = EncryptedFieldTypeHandler.class)
    private String content;

    private LocalDateTime createdAt;
}
