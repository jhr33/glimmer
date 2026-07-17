package com.glimmer.controller.api;

import com.glimmer.common.response.Result;
import com.glimmer.common.util.SecurityUtils;
import com.glimmer.service.FlowerService;
import com.glimmer.service.dto.FlowerTypeVO;
import com.glimmer.service.dto.FlowerVO;
import com.glimmer.service.dto.RedeemFlowerRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 花园养成接口
 * 见开发文档 §4.10
 */
@Tag(name = "花园养成接口", description = "花种列表、兑换、我的花朵、浇水、详情")
@RestController
public class FlowerController {

    private final FlowerService flowerService;

    public FlowerController(FlowerService flowerService) {
        this.flowerService = flowerService;
    }

    @Operation(summary = "花种列表（仅 available=1）")
    @GetMapping("/api/flower-types")
    public Result<List<FlowerTypeVO>> getFlowerTypeList() {
        List<FlowerTypeVO> list = flowerService.getFlowerTypeList();
        return Result.success(list);
    }

    @Operation(summary = "兑换花种（扣萤火余额）")
    @PostMapping("/api/flowers/redeem")
    public Result<FlowerVO> redeemFlower(@Valid @RequestBody RedeemFlowerRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        FlowerVO vo = flowerService.redeemFlower(userId, request.getFlowerTypeId());
        return Result.success(vo);
    }

    @Operation(summary = "我的花朵列表")
    @GetMapping("/api/flowers")
    public Result<List<FlowerVO>> getMyFlowers() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<FlowerVO> list = flowerService.getMyFlowers(userId);
        return Result.success(list);
    }

    @Operation(summary = "花朵详情（含当前阶段、进度）")
    @GetMapping("/api/flowers/{flowerId}")
    public Result<FlowerVO> getFlowerDetail(@PathVariable Long flowerId) {
        Long userId = SecurityUtils.getCurrentUserId();
        FlowerVO vo = flowerService.getFlowerDetail(userId, flowerId);
        return Result.success(vo);
    }

    @Operation(summary = "浇水（每日1次）")
    @PostMapping("/api/flowers/{flowerId}/water")
    public Result<FlowerVO> waterFlower(@PathVariable Long flowerId) {
        Long userId = SecurityUtils.getCurrentUserId();
        FlowerVO vo = flowerService.waterFlower(userId, flowerId);
        return Result.success(vo);
    }
}
