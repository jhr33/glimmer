package com.glimmer.controller.api;

import com.glimmer.common.response.PageResult;
import com.glimmer.common.response.Result;
import com.glimmer.common.util.SecurityUtils;
import com.glimmer.service.LetterService;
import com.glimmer.service.dto.LetterVO;
import com.glimmer.service.dto.ReplyLetterRequest;
import com.glimmer.service.dto.WriteLetterRequest;
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
 * 信件接口
 * 见开发文档 §4.7
 */
@Tag(name = "信件接口", description = "写信、回复、收发件箱、感谢")
@RestController
@RequestMapping("/api/letters")
public class LetterController {

    private final LetterService letterService;

    public LetterController(LetterService letterService) {
        this.letterService = letterService;
    }

    @Operation(summary = "写信（消耗1代币）")
    @PostMapping
    public Result<Void> writeLetter(@Valid @RequestBody WriteLetterRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        letterService.writeLetter(userId, request.getReceiverId(), request.getContent(),
                request.getSourceBottleReplyId());
        return Result.success();
    }

    @Operation(summary = "回复信件")
    @PostMapping("/{letterId}/reply")
    public Result<Void> replyLetter(@PathVariable Long letterId,
                                    @Valid @RequestBody ReplyLetterRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        letterService.replyLetter(userId, letterId, request.getContent());
        return Result.success();
    }

    @Operation(summary = "收件箱（分页）")
    @GetMapping("/inbox")
    public Result<PageResult<LetterVO>> getInbox(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        PageResult<LetterVO> result = letterService.getInbox(userId, page, size);
        return Result.success(result);
    }

    @Operation(summary = "发件箱（分页）")
    @GetMapping("/sent")
    public Result<PageResult<LetterVO>> getSent(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        PageResult<LetterVO> result = letterService.getSent(userId, page, size);
        return Result.success(result);
    }

    @Operation(summary = "信件详情（校验收发双方）")
    @GetMapping("/{letterId}")
    public Result<LetterVO> getLetterDetail(@PathVariable Long letterId) {
        Long userId = SecurityUtils.getCurrentUserId();
        LetterVO vo = letterService.getLetterDetail(userId, letterId);
        return Result.success(vo);
    }

    @Operation(summary = "标记信件为已读")
    @PostMapping("/{letterId}/read")
    public Result<Void> markAsRead(@PathVariable Long letterId) {
        Long userId = SecurityUtils.getCurrentUserId();
        letterService.markAsRead(userId, letterId);
        return Result.success();
    }

    @Operation(summary = "感谢信件（每人限1次）")
    @PostMapping("/{letterId}/thank")
    public Result<Void> thankLetter(@PathVariable Long letterId) {
        Long userId = SecurityUtils.getCurrentUserId();
        letterService.thankLetter(userId, letterId);
        return Result.success();
    }
}
