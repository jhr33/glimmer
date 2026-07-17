package com.glimmer.controller.api;

import com.glimmer.common.response.Result;
import com.glimmer.common.util.SecurityUtils;
import com.glimmer.service.UserService;
import com.glimmer.service.dto.GardenVO;
import com.glimmer.service.dto.UpdateNicknameRequest;
import com.glimmer.service.dto.UserProfileVO;
import com.glimmer.service.dto.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口
 * 见开发文档 §4.4
 */
@Tag(name = "用户接口", description = "当前用户信息、修改昵称、花园、他人主页")
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/me")
    public Result<UserVO> getCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        UserVO vo = userService.getCurrentUserInfo(userId);
        return Result.success(vo);
    }

    @Operation(summary = "修改昵称")
    @PutMapping("/nickname")
    public Result<Void> updateNickname(@Valid @RequestBody UpdateNicknameRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        userService.updateNickname(userId, request);
        return Result.success();
    }

    @Operation(summary = "获取萤火花园数据（萤火值、亮度等级、花朵列表）")
    @GetMapping("/{userId}/garden")
    public Result<GardenVO> getUserGarden(@PathVariable Long userId) {
        // 花园数据对登录用户公开（自己或他人均可查看）
        SecurityUtils.getCurrentUserId();
        GardenVO vo = userService.getUserGarden(userId);
        return Result.success(vo);
    }

    @Operation(summary = "查看他人主页（仅公开信息）")
    @GetMapping("/{userId}/profile")
    public Result<UserProfileVO> getUserProfile(@PathVariable Long userId) {
        SecurityUtils.getCurrentUserId();
        UserProfileVO vo = userService.getUserProfile(userId);
        return Result.success(vo);
    }
}
