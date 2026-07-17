package com.glimmer.controller.api;

import com.glimmer.common.response.PageResult;
import com.glimmer.common.response.Result;
import com.glimmer.common.util.SecurityUtils;
import com.glimmer.service.ReportService;
import com.glimmer.service.dto.CreateReportRequest;
import com.glimmer.service.dto.ReportVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 举报接口（需登录）
 * 见开发文档 §4.11
 */
@Tag(name = "举报接口", description = "提交举报、我的举报列表")
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "提交举报")
    @PostMapping
    public Result<Void> createReport(@Valid @RequestBody CreateReportRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        reportService.createReport(userId, request.getTargetType(), request.getTargetId(), request.getContent());
        return Result.success();
    }

    @Operation(summary = "我提交的举报列表（分页）")
    @GetMapping("/mine")
    public Result<PageResult<ReportVO>> getMyReports(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        PageResult<ReportVO> result = reportService.getMyReports(userId, page, size);
        return Result.success(result);
    }
}
