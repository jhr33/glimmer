package com.glimmer.controller.api;

import com.glimmer.common.response.PageResult;
import com.glimmer.common.response.Result;
import com.glimmer.common.util.SecurityUtils;
import com.glimmer.service.FeedbackService;
import com.glimmer.service.dto.CreateAppealRequest;
import com.glimmer.service.dto.CreateFeedbackRequest;
import com.glimmer.service.dto.FeedbackVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 意见信接口（需登录）
 * 见开发文档 §4.12
 */
@Tag(name = "意见与申诉接口", description = "提交意见、提交申诉、我的意见与申诉")
@RestController
@RequestMapping("/api/feedbacks")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @Operation(summary = "提交意见信")
    @PostMapping
    public Result<Void> createFeedback(@Valid @RequestBody CreateFeedbackRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        feedbackService.createFeedback(userId, request.getContent());
        return Result.success();
    }

    @Operation(summary = "提交申诉")
    @PostMapping("/appeal")
    public Result<Void> createAppeal(@Valid @RequestBody CreateAppealRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        feedbackService.createAppeal(userId, request.getReportId(), request.getContent());
        return Result.success();
    }

    @Operation(summary = "我的意见信列表（分页）")
    @GetMapping("/mine")
    public Result<PageResult<FeedbackVO>> getMyFeedbacks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        PageResult<FeedbackVO> result = feedbackService.getMyFeedbacks(userId, page, size);
        return Result.success(result);
    }

    @Operation(summary = "意见信详情（仅提交者可看）")
    @GetMapping("/{feedbackId}")
    public Result<FeedbackVO> getFeedbackDetail(@PathVariable Long feedbackId) {
        Long userId = SecurityUtils.getCurrentUserId();
        FeedbackVO vo = feedbackService.getFeedbackDetail(userId, feedbackId);
        return Result.success(vo);
    }
}
