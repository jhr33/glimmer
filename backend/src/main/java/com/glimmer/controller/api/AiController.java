package com.glimmer.controller.api;

import com.glimmer.common.response.PageResult;
import com.glimmer.common.response.Result;
import com.glimmer.common.util.SecurityUtils;
import com.glimmer.service.AiConversationService;
import com.glimmer.service.dto.AiConversationVO;
import com.glimmer.service.dto.ConversationDetailVO;
import com.glimmer.service.dto.SendMessageRequest;
import com.glimmer.service.dto.SendMessageResponse;
import com.glimmer.service.dto.UnlockQuotaResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    /**
     * AI 流式请求专用线程池。
     * <p>
     * 替代原来的 new Thread()：
     * - 限制最大并发流式请求数，避免线程无限增长
     * - 线程可复用，减少创建开销
     * - 应用关闭时可优雅停机
     */
    private final ExecutorService aiStreamExecutor = Executors.newFixedThreadPool(
            4,
            r -> {
                Thread t = new Thread(r, "ai-stream-worker");
                t.setDaemon(true);
                return t;
            }
    );

    public AiController(AiConversationService aiConversationService, ObjectMapper objectMapper) {
        this.aiConversationService = aiConversationService;
        this.objectMapper = objectMapper;
    }

    @PreDestroy
    public void shutdown() {
        aiStreamExecutor.shutdown();
        try {
            if (!aiStreamExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                aiStreamExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            aiStreamExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
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

        // 超时 2 分钟（原 5 分钟过长，DeepSeek readTimeout 已设为 120s）
        // 超时后必须 complete() 释放浏览器连接，否则连接池耗尽
        SseEmitter emitter = new SseEmitter(2 * 60 * 1000L);

        // 使用线程池替代 new Thread()，限制并发并支持复用
        aiStreamExecutor.submit(() -> {
            try {
                aiConversationService.sendMessageStream(userId, conversationId, request.getContent(), emitter, objectMapper);
            } catch (Exception e) {
                log.error("AI 流式处理异常: conversationId={}", conversationId, e);
                emitter.completeWithError(e);
            }
        });

        // 客户端断开 / 超时 / 出错时，必须 complete() 以释放浏览器连接
        emitter.onCompletion(() -> log.info("SSE 连接已关闭: conversationId={}", conversationId));
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时: conversationId={}", conversationId);
            emitter.complete();
        });
        emitter.onError(throwable -> {
            log.warn("SSE 连接异常: conversationId={}", conversationId, throwable);
            emitter.complete();
        });

        return emitter;
    }

    @Operation(summary = "关闭会话")
    @PostMapping("/{conversationId}/close")
    public Result<Void> closeConversation(@PathVariable Long conversationId) {
        Long userId = SecurityUtils.getCurrentUserId();
        aiConversationService.closeConversation(userId, conversationId);
        return Result.success();
    }

    @Operation(summary = "解锁对话额度（消耗1代币，额度上限+10）")
    @PostMapping("/{conversationId}/unlock")
    public Result<UnlockQuotaResponse> unlockQuota(@PathVariable Long conversationId) {
        Long userId = SecurityUtils.getCurrentUserId();
        UnlockQuotaResponse response = aiConversationService.unlockQuota(userId, conversationId);
        return Result.success(response);
    }
}
