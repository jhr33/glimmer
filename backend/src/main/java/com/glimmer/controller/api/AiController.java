package com.glimmer.controller.api;

import com.glimmer.common.response.PageResult;
import com.glimmer.common.response.Result;
import com.glimmer.common.util.SecurityUtils;
import com.glimmer.service.AiConversationService;
import com.glimmer.service.dto.AiConversationVO;
import com.glimmer.service.dto.ConversationDetailVO;
import com.glimmer.service.dto.SendMessageRequest;
import com.glimmer.service.dto.SendMessageResponse;
import com.glimmer.service.dto.StreamMessageDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 对话接口
 * 见开发文档 §4.9
 */
@Slf4j
@Tag(name = "AI 对话接口", description = "开启会话、会话列表、详情、发送消息、关闭")
@RestController
@RequestMapping("/api/ai/conversations")
public class AiController {

    private final AiConversationService aiConversationService;
    private final ObjectMapper objectMapper;

    public AiController(AiConversationService aiConversationService, ObjectMapper objectMapper) {
        this.aiConversationService = aiConversationService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "开启新会话（消耗1代币）")
    @PostMapping
    public Result<AiConversationVO> startConversation() {
        Long userId = SecurityUtils.getCurrentUserId();
        AiConversationVO vo = aiConversationService.startConversation(userId);
        return Result.success(vo);
    }

    @Operation(summary = "我的会话列表（分页）")
    @GetMapping
    public Result<PageResult<AiConversationVO>> getConversationList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        PageResult<AiConversationVO> result = aiConversationService.getConversationList(userId, page, size);
        return Result.success(result);
    }

    @Operation(summary = "会话详情（含全部消息）")
    @GetMapping("/{conversationId}")
    public Result<ConversationDetailVO> getConversationDetail(@PathVariable Long conversationId) {
        Long userId = SecurityUtils.getCurrentUserId();
        ConversationDetailVO vo = aiConversationService.getConversationDetail(userId, conversationId);
        return Result.success(vo);
    }

    @Operation(summary = "发送消息（同步返回 AI 回复）")
    @PostMapping("/{conversationId}/messages")
    public Result<SendMessageResponse> sendMessage(@PathVariable Long conversationId,
                                                    @Valid @RequestBody SendMessageRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        SendMessageResponse response = aiConversationService.sendMessage(userId, conversationId, request.getContent());
        return Result.success(response);
    }

    @Operation(summary = "发送消息（流式返回 AI 回复，边收边发）")
    @PostMapping(value = "/{conversationId}/messages/stream")
    public SseEmitter sendMessageStream(@PathVariable Long conversationId,
                                        @Valid @RequestBody SendMessageRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        
        // 设置超时时间为5分钟（流式响应需要较长时间）
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        
        // 在独立线程中异步执行流式发送，避免阻塞请求线程
        new Thread(() -> {
            aiConversationService.sendMessageStream(userId, conversationId, request.getContent(), emitter, objectMapper);
        }, "ai-stream-" + conversationId).start();
        
        // 客户端断开连接时的处理
        emitter.onCompletion(() -> log.info("SSE 连接已关闭"));
        emitter.onTimeout(() -> log.warn("SSE 连接超时"));
        
        return emitter;
    }

    @Operation(summary = "关闭会话")
    @PostMapping("/{conversationId}/close")
    public Result<Void> closeConversation(@PathVariable Long conversationId) {
        Long userId = SecurityUtils.getCurrentUserId();
        aiConversationService.closeConversation(userId, conversationId);
        return Result.success();
    }
}
