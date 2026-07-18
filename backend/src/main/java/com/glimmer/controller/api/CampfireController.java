package com.glimmer.controller.api;

import com.glimmer.common.response.PageResult;
import com.glimmer.common.response.Result;
import com.glimmer.common.util.SecurityUtils;
import com.glimmer.service.CampfireService;
import com.glimmer.service.dto.CampfireMessageVO;
import com.glimmer.service.dto.CampfireVO;
import com.glimmer.service.dto.CreateCampfireRequest;
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

import java.util.List;

/**
 * 篝火接口
 * 见开发文档 §4.8
 */
@Tag(name = "篝火接口", description = "篝火列表、创建、详情、历史消息、加入、退出")
@RestController
@RequestMapping("/api/campfires")
public class CampfireController {

    private final CampfireService campfireService;

    public CampfireController(CampfireService campfireService) {
        this.campfireService = campfireService;
    }

    @Operation(summary = "篝火列表（系统默认 + 我创建的 + 我加入的）")
    @GetMapping
    public Result<List<CampfireVO>> getCampfireList() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<CampfireVO> list = campfireService.getCampfireList(userId);
        return Result.success(list);
    }

    @Operation(summary = "创建篝火（消耗代币：10人→1，20人→2，30人→3）")
    @PostMapping
    public Result<CampfireVO> createCampfire(@Valid @RequestBody CreateCampfireRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        CampfireVO vo = campfireService.createCampfire(userId, request.getName(), request.getMaxMembers());
        return Result.success(vo);
    }

    @Operation(summary = "篝火详情（含成员数）")
    @GetMapping("/{campfireId}")
    public Result<CampfireVO> getCampfireDetail(@PathVariable Long campfireId) {
        Long userId = SecurityUtils.getCurrentUserId();
        CampfireVO vo = campfireService.getCampfireDetail(userId, campfireId);
        return Result.success(vo);
    }

    @Operation(summary = "历史消息（分页）")
    @GetMapping("/{campfireId}/messages")
    public Result<PageResult<CampfireMessageVO>> getHistoryMessages(
            @PathVariable Long campfireId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        PageResult<CampfireMessageVO> result = campfireService.getHistoryMessages(userId, campfireId, page, size);
        return Result.success(result);
    }

    @Operation(summary = "加入篝火")
    @PostMapping("/{campfireId}/join")
    public Result<Void> joinCampfire(@PathVariable Long campfireId) {
        Long userId = SecurityUtils.getCurrentUserId();
        campfireService.joinCampfire(userId, campfireId);
        return Result.success();
    }

    @Operation(summary = "退出篝火（创建者不可退出）")
    @PostMapping("/{campfireId}/leave")
    public Result<Void> leaveCampfire(@PathVariable Long campfireId) {
        Long userId = SecurityUtils.getCurrentUserId();
        campfireService.leaveCampfire(userId, campfireId);
        return Result.success();
    }

    @Operation(summary = "熄灭篝火（仅创建者可操作，系统默认篝火不可熄灭）")
    @PostMapping("/{campfireId}/extinguish")
    public Result<Void> extinguishCampfire(@PathVariable Long campfireId) {
        Long userId = SecurityUtils.getCurrentUserId();
        campfireService.extinguishCampfire(userId, campfireId);
        return Result.success();
    }
}
