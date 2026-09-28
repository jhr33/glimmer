package com.glimmer.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送消息请求（篝火聊天 / AI 对话复用）
 */
@Data
public class SendMessageRequest {

    /** 内容（msgType=text 时必填，image 时可为空） */
    @Size(max = 2000, message = "内容最长2000个字符")
    private String content;

    /** 消息类型: text文本/image图片，默认 text */
    private String msgType = "text";

    /** 图片消息URL（msgType=image 时必填） */
    private String imageUrl;

    /** 被引用/回复的消息ID */
    private Long quotedMessageId;
}
