package com.glimmer.controller.api;

import com.glimmer.common.response.PageResult;
import com.glimmer.common.response.Result;
import com.glimmer.common.util.SecurityUtils;
import com.glimmer.service.DriftBottleService;
import com.glimmer.service.dto.BottlePickVO;
import com.glimmer.service.dto.BottleReplyVO;
import com.glimmer.service.dto.BottleSummaryVO;
import com.glimmer.service.dto.BottleVO;
import com.glimmer.service.dto.ReplyBottleRequest;
import com.glimmer.service.dto.ThrowBottleRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 漂流瓶接口
 * 见开发文档 §4.6
 */
@Tag(name = "漂流瓶接口", description = "扔瓶、捡瓶、回复、感谢、沉底")
@RestController
public class DriftBottleController {

    private final DriftBottleService driftBottleService;

    public DriftBottleController(DriftBottleService driftBottleService) {
        this.driftBottleService = driftBottleService;
    }

    @Operation(summary = "扔漂流瓶")
    @PostMapping("/api/bottles")
    public Result<Void> throwBottle(@Valid @RequestBody ThrowBottleRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        driftBottleService.throwBottle(userId, request.getContent());
        return Result.success();
    }

    @Operation(summary = "捡漂流瓶（随机返回1个瓶子，不含内容）")
    @PostMapping("/api/bottles/pick")
    public Result<Map<String, Object>> pickBottle() {
        Long userId = SecurityUtils.getCurrentUserId();
        BottlePickVO vo = driftBottleService.pickBottle(userId);
        Map<String, Object> data = new HashMap<>();
        if (vo == null) {
            data.put("found", false);
        } else {
            data.put("found", true);
            data.put("bottle", vo);
        }
        return Result.success(data);
    }

    @Operation(summary = "查看漂流瓶内容（仅已捡到后可查看）")
    @GetMapping("/api/bottles/{bottleId}")
    public Result<BottleVO> getBottleContent(@PathVariable Long bottleId) {
        Long userId = SecurityUtils.getCurrentUserId();
        BottleVO vo = driftBottleService.getBottleContent(userId, bottleId);
        return Result.success(vo);
    }

    @Operation(summary = "放回漂流瓶（不查看内容）")
    @PostMapping("/api/bottles/{bottleId}/release")
    public Result<Void> releaseBottle(@PathVariable Long bottleId) {
        Long userId = SecurityUtils.getCurrentUserId();
        driftBottleService.releaseBottle(userId, bottleId);
        return Result.success();
    }

    @Operation(summary = "回复漂流瓶（每人限1次）")
    @PostMapping("/api/bottles/{bottleId}/replies")
    public Result<Void> replyBottle(@PathVariable Long bottleId,
                                    @Valid @RequestBody ReplyBottleRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        driftBottleService.replyBottle(userId, bottleId, request.getContent());
        return Result.success();
    }

    @Operation(summary = "查看我的瓶子回复（仅瓶主可看）")
    @GetMapping("/api/bottles/{bottleId}/replies")
    public Result<List<BottleReplyVO>> getBottleReplies(@PathVariable Long bottleId) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<BottleReplyVO> list = driftBottleService.getBottleReplies(userId, bottleId);
        return Result.success(list);
    }

    @Operation(summary = "感谢漂流瓶（每人限1次）")
    @PostMapping("/api/bottles/{bottleId}/thank")
    public Result<Void> thankBottle(@PathVariable Long bottleId) {
        Long userId = SecurityUtils.getCurrentUserId();
        driftBottleService.thankBottle(userId, bottleId);
        return Result.success();
    }

    @Operation(summary = "感谢瓶子回复（每人限1次）")
    @PostMapping("/api/bottle-replies/{replyId}/thank")
    public Result<Void> thankBottleReply(@PathVariable Long replyId) {
        Long userId = SecurityUtils.getCurrentUserId();
        driftBottleService.thankBottleReply(userId, replyId);
        return Result.success();
    }

    @Operation(summary = "沉底自己的瓶子（仅瓶主）")
    @PostMapping("/api/bottles/{bottleId}/sink")
    public Result<Void> sinkBottle(@PathVariable Long bottleId) {
        Long userId = SecurityUtils.getCurrentUserId();
        driftBottleService.sinkBottle(userId, bottleId);
        return Result.success();
    }

    @Operation(summary = "我扔出的瓶子列表（分页）")
    @GetMapping("/api/bottles/mine")
    public Result<PageResult<BottleVO>> getMyBottles(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        PageResult<BottleVO> result = driftBottleService.getMyBottles(userId, page, size);
        return Result.success(result);
    }

    @Operation(summary = "漂流瓶列表（游客可看，仅摘要）")
    @GetMapping("/api/bottles")
    public Result<PageResult<BottleSummaryVO>> getBottleList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResult<BottleSummaryVO> result = driftBottleService.getBottleList(page, size);
        return Result.success(result);
    }
}
