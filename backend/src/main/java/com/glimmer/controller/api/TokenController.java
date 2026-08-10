package com.glimmer.controller.api;

import com.glimmer.common.response.PageResult;
import com.glimmer.common.response.Result;
import com.glimmer.common.util.SecurityUtils;
import com.glimmer.service.TokenService;
import com.glimmer.service.dto.SignInResponse;
import com.glimmer.service.dto.SignInStatusResponse;
import com.glimmer.service.dto.TransactionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 代币接口（签到、流水查询）
 * 见开发文档 §4.5
 */
@Tag(name = "代币接口", description = "签到、签到状态、代币流水")
@RestController
@RequestMapping("/api/token")
public class TokenController {

    private final TokenService tokenService;

    public TokenController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Operation(summary = "签到（每日1次，返回获得代币数）")
    @PostMapping("/sign-in")
    public Result<SignInResponse> signIn() {
        Long userId = SecurityUtils.getCurrentUserId();
        SignInResponse response = tokenService.signIn(userId);
        return Result.success(response);
    }

    @Operation(summary = "查询今日签到状态")
    @GetMapping("/sign-in/today")
    public Result<SignInStatusResponse> getSignInStatus() {
        Long userId = SecurityUtils.getCurrentUserId();
        SignInStatusResponse response = tokenService.getSignInStatus(userId);
        return Result.success(response);
    }

    @Operation(summary = "查询某月签到日历")
    @GetMapping("/sign-in/calendar")
    public Result<Map<String, Object>> getSignInCalendar(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "0") int month) {
        Long userId = SecurityUtils.getCurrentUserId();
        // 默认取当前年月
        LocalDate now = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        if (year <= 0) year = now.getYear();
        if (month <= 0 || month > 12) month = now.getMonthValue();
        List<LocalDate> dates = tokenService.getMonthlySignInDates(userId, year, month);
        return Result.success(Map.of(
                "year", year,
                "month", month,
                "signedDates", dates,
                "signedCount", dates.size()
        ));
    }

    @Operation(summary = "代币流水查询（分页，可按类型/来源筛选）")
    @GetMapping("/transactions")
    public Result<PageResult<TransactionVO>> getTransactions(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        PageResult<TransactionVO> result = tokenService.getTransactions(userId, type, source, page, size);
        return Result.success(result);
    }
}
