package com.glimmer.controller.api;

import com.glimmer.common.response.PageResult;
import com.glimmer.common.response.Result;
import com.glimmer.common.util.SecurityUtils;
import com.glimmer.service.UserFollowService;
import com.glimmer.service.dto.ArticleListVO;
import com.glimmer.service.dto.CreateFollowRequest;
import com.glimmer.service.dto.FollowVO;
import com.glimmer.service.dto.UpdateFollowRemarkRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 交流会作者关注接口（必须登录）
 * <p>
 * 关注目标为「账户 + 笔名」组合，支持自定义备注；
 * "我的关注"包含关注列表与关注文章流两部分。
 * </p>
 */
@Tag(name = "作者关注接口", description = "关注作者、备注管理、我的关注文章流")
@RestController
@RequestMapping("/api/articles")
public class FollowController {

    private final UserFollowService userFollowService;

    public FollowController(UserFollowService userFollowService) {
        this.userFollowService = userFollowService;
    }

    @Operation(summary = "关注作者（账户+笔名，可携带备注）")
    @PostMapping("/follow")
    public Result<Long> follow(@Valid @RequestBody CreateFollowRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long followId = userFollowService.follow(userId, request.getFolloweeId(),
                request.getPenName(), request.getRemark());
        return Result.success(followId);
    }

    @Operation(summary = "取消关注（按关注记录ID）")
    @DeleteMapping("/follow/{followId}")
    public Result<Void> unfollow(@PathVariable Long followId) {
        Long userId = SecurityUtils.getCurrentUserId();
        userFollowService.unfollow(userId, followId);
        return Result.success();
    }

    @Operation(summary = "修改关注备注")
    @PutMapping("/follow/{followId}/remark")
    public Result<Void> updateRemark(@PathVariable Long followId,
                                     @Valid @RequestBody UpdateFollowRemarkRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        userFollowService.updateRemark(userId, followId, request.getRemark());
        return Result.success();
    }

    @Operation(summary = "我的关注列表（分页，含该笔名下可见文章数）")
    @GetMapping("/following")
    public Result<PageResult<FollowVO>> listFollows(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(userFollowService.listFollows(userId, page, size));
    }

    @Operation(summary = "我的关注文章流（已关注账户+笔名组合下的公开文章）")
    @GetMapping("/following/feed")
    public Result<PageResult<ArticleListVO>> followingFeed(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(userFollowService.getFollowingFeed(userId, page, size));
    }
}
