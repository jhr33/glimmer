package com.glimmer.controller.api;

import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.response.Result;
import com.glimmer.common.util.SecurityUtils;
import com.glimmer.entity.User;
import com.glimmer.mapper.UserMapper;
import com.glimmer.service.EchoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 回音机器人托管控制 API
 * 见开发文档 §9 AI 机器人系统
 */
@Slf4j
@RestController
@RequestMapping("/api/echo")
@Tag(name = "回音机器人", description = "AI 自动托管控制")
public class EchoController {

    private final EchoService echoService;
    private final UserMapper userMapper;

    public EchoController(EchoService echoService, UserMapper userMapper) {
        this.echoService = echoService;
        this.userMapper = userMapper;
    }

    /**
     * 获取托管状态
     */
    @Operation(summary = "获取回音托管状态")
    @GetMapping("/auto-mode")
    public Result<Map<String, Object>> getAutoMode() {
        boolean enabled = echoService.getAutoMode();
        return Result.success(Map.of("enabled", enabled));
    }

    /**
     * 设置托管开关
     * 仅管理员或回音账号可操作
     */
    @Operation(summary = "设置回音托管开关")
    @PostMapping("/auto-mode")
    public Result<Void> setAutoMode(@RequestBody Map<String, Boolean> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userMapper.selectById(userId);

        if (user == null) {
            return Result.error(ErrorCode.NOT_FOUND);
        }

        // 仅回音自身可操作（管理员不再代管，避免开关状态在不同账号间混淆）
        boolean isSelf = EchoService.BOT_USERNAME.equals(user.getUsername());
        if (!isSelf) {
            return Result.error(ErrorCode.FORBIDDEN, "无权限操作回音托管开关，仅回音账号可控制");
        }

        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return Result.error(ErrorCode.PARAM_ERROR, "enabled 参数不能为空");
        }

        echoService.setAutoMode(enabled);
        log.info("用户 {} 操作回音托管: enabled={}", user.getUsername(), enabled);

        return Result.success();
    }
}
