package com.glimmer.controller.api;

import com.glimmer.common.response.PageResult;
import com.glimmer.common.response.Result;
import com.glimmer.service.AnnouncementService;
import com.glimmer.service.dto.AnnouncementListVO;
import com.glimmer.service.dto.AnnouncementVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公告接口（游客可访问）
 * 见开发文档 §4.13
 */
@Tag(name = "公告接口", description = "公告列表与详情（游客可访问）")
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @Operation(summary = "公告列表（分页，仅 published）")
    @GetMapping
    public Result<PageResult<AnnouncementListVO>> getAnnouncementList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResult<AnnouncementListVO> result = announcementService.getAnnouncementList(page, size);
        return Result.success(result);
    }

    @Operation(summary = "公告详情")
    @GetMapping("/{id}")
    public Result<AnnouncementVO> getAnnouncementDetail(@PathVariable Long id) {
        AnnouncementVO vo = announcementService.getAnnouncementDetail(id);
        return Result.success(vo);
    }
}
