package com.glimmer.controller.api;

import com.glimmer.common.response.Result;
import com.glimmer.common.util.SecurityUtils;
import com.glimmer.service.GameScoreService;
import com.glimmer.service.dto.GameAliasVO;
import com.glimmer.service.dto.LeaderboardItemVO;
import com.glimmer.service.dto.MyBestScoreVO;
import com.glimmer.service.dto.SaveAliasRequest;
import com.glimmer.service.dto.SubmitScoreRequest;
import com.glimmer.service.dto.SubmitScoreResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小游戏接口（成绩提交 / 排行榜）
 * 排行榜游客可见，提交成绩与个人最佳需登录
 */
@Tag(name = "小游戏接口", description = "游戏成绩提交、排行榜")
@RestController
@RequestMapping("/api/game")
public class GameController {

    private final GameScoreService gameScoreService;

    public GameController(GameScoreService gameScoreService) {
        this.gameScoreService = gameScoreService;
    }

    @Operation(summary = "提交一局游戏成绩（需登录）")
    @PostMapping("/scores")
    public Result<SubmitScoreResultVO> submitScore(@RequestBody SubmitScoreRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(gameScoreService.submitScore(userId, request));
    }

    @Operation(summary = "游戏排行榜（游客可浏览）")
    @GetMapping("/leaderboard")
    public Result<List<LeaderboardItemVO>> getLeaderboard(
            @RequestParam(defaultValue = "snake") String gameType,
            @RequestParam(defaultValue = "20") int limit) {
        // 游客传 null：排行榜仅不做"当前玩家"高亮
        Long currentUserId = SecurityUtils.getCurrentUserIdOrNull();
        return Result.success(gameScoreService.getLeaderboard(gameType, limit, currentUserId));
    }

    @Operation(summary = "我的最佳成绩与名次（需登录）")
    @GetMapping("/scores/my-best")
    public Result<MyBestScoreVO> getMyBest(
            @RequestParam(defaultValue = "snake") String gameType) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(gameScoreService.getMyBest(userId, gameType));
    }

    @Operation(summary = "查询我的游戏匿名代号（需登录）")
    @GetMapping("/alias")
    public Result<GameAliasVO> getMyAlias(
            @RequestParam(defaultValue = "snake") String gameType) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(gameScoreService.getMyAlias(userId, gameType));
    }

    @Operation(summary = "设置/修改我的游戏匿名代号（需登录）")
    @PutMapping("/alias")
    public Result<GameAliasVO> saveMyAlias(@RequestBody SaveAliasRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(gameScoreService.saveMyAlias(userId, request));
    }
}
