package com.glimmer.controller.admin;

import com.glimmer.common.response.PageResult;
import com.glimmer.common.response.Result;
import com.glimmer.common.util.SecurityUtils;
import com.glimmer.service.ReportService;
import com.glimmer.service.dto.ReportVO;
import com.glimmer.service.dto.ReviewReportRequest;
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
 * 管理员举报接口（需 admin 角色）
 * 见开发文档 §4.15
 */
@Tag(name = "管理员-举报接口", description = "举报列表、详情、审核")
@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final ReportService reportService;

    public AdminReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "举报列表（分页，可按状态筛选）")
    @GetMapping
    public Result<PageResult<ReportVO>> getReportList(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResult<ReportVO> result = reportService.getReportList(status, page, size);
        return Result.success(result);
    }

    @Operation(summary = "举报详情")
    @GetMapping("/{id}")
    public Result<ReportVO> getReportDetail(@PathVariable Long id) {
        ReportVO vo = reportService.getReportDetail(id);
        return Result.success(vo);
    }

    @Operation(summary = "审核举报")
    @PostMapping("/{id}/review")
    public Result<Void> reviewReport(@PathVariable Long id,
                                     @Valid @RequestBody ReviewReportRequest request) {
        Long reviewerId = SecurityUtils.getCurrentUserId();
        reportService.reviewReport(reviewerId, id, request.getResult(), request.getReviewComment());
        return Result.success();
    }
}
