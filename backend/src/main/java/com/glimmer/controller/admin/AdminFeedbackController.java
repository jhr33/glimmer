package com.glimmer.controller.admin;

import com.glimmer.common.response.PageResult;
import com.glimmer.common.response.Result;
import com.glimmer.common.util.SecurityUtils;
import com.glimmer.service.FeedbackService;
import com.glimmer.service.dto.FeedbackVO;
import com.glimmer.service.dto.ReplyFeedbackRequest;
import com.glimmer.service.dto.ReviewAppealRequest;
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
 * 管理员意见信接口（需 admin 角色）
 * 见开发文档 §4.15
 */
@Tag(name = "管理员-意见信接口", description = "意见信列表、详情、回复")
@RestController
@RequestMapping("/api/admin/feedbacks")
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    public AdminFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @Operation(summary = "意见信列表（分页，可按状态筛选）")
    @GetMapping
    public Result<PageResult<FeedbackVO>> getFeedbackList(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResult<FeedbackVO> result = feedbackService.getFeedbackList(status, page, size);
        return Result.success(result);
    }

    @Operation(summary = "意见信详情")
    @GetMapping("/{id}")
    public Result<FeedbackVO> getFeedbackDetail(@PathVariable Long id) {
        FeedbackVO vo = feedbackService.getFeedbackDetail(id);
        return Result.success(vo);
    }

    @Operation(summary = "回复意见信")
    @PostMapping("/{id}/reply")
    public Result<Void> replyFeedback(@PathVariable Long id,
                                      @Valid @RequestBody ReplyFeedbackRequest request) {
        Long adminId = SecurityUtils.getCurrentUserId();
        feedbackService.replyFeedback(adminId, id, request.getReply());
        return Result.success();
    }

    @Operation(summary = "申诉列表（分页，可按状态筛选）")
    @GetMapping("/appeals")
    public Result<PageResult<FeedbackVO>> getAppealList(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResult<FeedbackVO> result = feedbackService.getAppealList(status, page, size);
        return Result.success(result);
    }

    @Operation(summary = "申诉详情")
    @GetMapping("/appeals/{id}")
    public Result<FeedbackVO> getAppealDetail(@PathVariable Long id) {
        FeedbackVO vo = feedbackService.getAppealDetail(id);
        return Result.success(vo);
    }

    @Operation(summary = "审核申诉")
    @PostMapping("/appeals/{id}/review")
    public Result<Void> reviewAppeal(@PathVariable Long id,
                                     @Valid @RequestBody ReviewAppealRequest request) {
        Long adminId = SecurityUtils.getCurrentUserId();
        feedbackService.reviewAppeal(adminId, id, request.getResult(), request.getReply(), request.getNewPenaltyType());
        return Result.success();
    }
}
