package com.glimmer.controller.ws;

import com.glimmer.common.exception.BusinessException;
import com.glimmer.service.CampfireService;
import com.glimmer.service.dto.CampfireMessageVO;
import com.glimmer.service.dto.SendMessageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

/**
 * 篝火 WebSocket 消息控制器
 * 见开发文档 §3.4.3 / §4.8.2
 * - SEND /app/campfire/{campfireId}/send
 * - 广播到 /topic/campfire/{campfireId}（由 CampfireService 内部完成）
 * - 业务异常（未加入篝火/被封禁）通过 @MessageExceptionHandler 返回 ERROR 帧到 /user/queue/errors
 */
@Slf4j
@Tag(name = "篝火 WebSocket 接口", description = "STOMP 消息发送")
@Controller
public class ChatController {

    private final CampfireService campfireService;

    public ChatController(CampfireService campfireService) {
        this.campfireService = campfireService;
    }

    /**
     * 处理篝火消息发送
     * userId 通过握手时注入的 Principal 获取（值为 userId 字符串）
     */
    @Operation(summary = "发送篝火消息（WebSocket）")
    @MessageMapping("/campfire/{campfireId}/send")
    public void sendMessage(@DestinationVariable Long campfireId,
                           @Valid @Payload SendMessageRequest request,
                           SimpMessageHeaderAccessor headerAccessor) {
        Principal principal = headerAccessor.getUser();
        if (principal == null) {
            log.warn("WebSocket 消息无鉴权用户: campfireId={}", campfireId);
            throw new BusinessException(com.glimmer.common.exception.ErrorCode.UNAUTHORIZED);
        }
        Long userId = Long.valueOf(principal.getName());
        // 调用 service：插入消息 + 广播到 /topic/campfire/{campfireId}
        CampfireMessageVO vo = campfireService.sendMessage(userId, campfireId, request.getContent());
        log.debug("WebSocket 篝火消息已处理: campfireId={}, messageId={}", campfireId, vo.getId());
    }

    /**
     * 业务异常处理：返回 ERROR 帧到当前用户的 /user/queue/errors
     */
    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public Map<String, Object> handleBusinessException(BusinessException e) {
        log.warn("WebSocket 业务异常: code={}, message={}", e.getCode(), e.getMessage());
        Map<String, Object> error = new HashMap<>();
        error.put("type", "ERROR");
        error.put("code", e.getCode());
        error.put("message", e.getMessage());
        return error;
    }

    /**
     * 其他异常处理
     */
    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public Map<String, Object> handleException(Exception e) {
        log.error("WebSocket 异常", e);
        Map<String, Object> error = new HashMap<>();
        error.put("type", "ERROR");
        error.put("code", 500);
        error.put("message", "服务器内部错误");
        return error;
    }
}
