package com.glimmer.controller.admin;

import com.glimmer.common.response.PageResult;
import com.glimmer.common.response.Result;
import com.glimmer.common.util.SecurityUtils;
import com.glimmer.service.UserService;
import com.glimmer.service.dto.UpdateUserStatusRequest;
import com.glimmer.service.dto.UserAdminVO;
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
 * 管理员用户接口（需 admin 角色）
 * 见开发文档 §4.15
 */
@Tag(name = "管理员-用户接口", description = "用户列表、封禁/解封")
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "用户列表（分页，可按 status/role 筛选）")
    @GetMapping
    public Result<PageResult<UserAdminVO>> getUserList(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResult<UserAdminVO> result = userService.getUserListForAdmin(status, role, page, size);
        return Result.success(result);
    }

    @Operation(summary = "封禁/解封用户")
    @PostMapping("/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id,
                                         @Valid @RequestBody UpdateUserStatusRequest request) {
        Long adminId = SecurityUtils.getCurrentUserId();
        userService.updateUserStatus(adminId, id, request.getStatus());
        return Result.success();
    }
}
