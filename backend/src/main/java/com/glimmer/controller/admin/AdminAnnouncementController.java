package com.glimmer.controller.admin;

import com.glimmer.common.response.PageResult;
import com.glimmer.common.response.Result;
import com.glimmer.common.util.SecurityUtils;
import com.glimmer.service.AnnouncementService;
import com.glimmer.service.dto.AnnouncementAdminVO;
import com.glimmer.service.dto.PublishAnnouncementRequest;
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
 * 管理员公告接口（需 admin 角色）
 * 见开发文档 §4.15
 */
@Tag(name = "管理员-公告接口", description = "发布公告、公告列表（含下架）、下架公告")
@RestController
@RequestMapping("/api/admin/announcements")
public class AdminAnnouncementController {

    private final AnnouncementService announcementService;

    public AdminAnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @Operation(summary = "发布公告（广播通知）")
    @PostMapping
    public Result<Long> publishAnnouncement(@Valid @RequestBody PublishAnnouncementRequest request) {
        Long publisherId = SecurityUtils.getCurrentUserId();
        Long id = announcementService.publishAnnouncement(publisherId, request.getTitle(), request.getContent());
        return Result.success(id);
    }

    @Operation(summary = "公告列表（含下架，可按状态筛选）")
    @GetMapping
    public Result<PageResult<AnnouncementAdminVO>> getAnnouncementList(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResult<AnnouncementAdminVO> result = announcementService.getAnnouncementListForAdmin(status, page, size);
        return Result.success(result);
    }

    @Operation(summary = "下架公告")
    @PostMapping("/{id}/take-down")
    public Result<Void> takeDownAnnouncement(@PathVariable Long id) {
        announcementService.takeDownAnnouncement(id);
        return Result.success();
    }
}
