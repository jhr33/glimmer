package com.glimmer.controller.api;

import com.glimmer.common.response.PageResult;
import com.glimmer.common.response.Result;
import com.glimmer.common.util.SecurityUtils;
import com.glimmer.service.ArticleInteractionService;
import com.glimmer.service.dto.ArticleCommentVO;
import com.glimmer.service.dto.ArticleListVO;
import com.glimmer.service.dto.CollectArticleRequest;
import com.glimmer.service.dto.CollectStateVO;
import com.glimmer.service.dto.CommentLikeStateVO;
import com.glimmer.service.dto.CreateCommentRequest;
import com.glimmer.service.dto.CreateFolderRequest;
import com.glimmer.service.dto.FavoriteFolderVO;
import com.glimmer.service.dto.LikeStateVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 萤火交流会互动接口：评论 / 点赞 / 收藏 / 收藏文件夹
 * <p>
 * 评论列表游客可访问（SecurityConfig 白名单）；
 * 其余写操作与个人收藏数据必须登录。
 * </p>
 */
@Tag(name = "萤火交流会互动接口", description = "评论、点赞、收藏与自定义收藏文件夹")
@RestController
@RequestMapping("/api/articles")
public class ArticleInteractionController {

    private final ArticleInteractionService interactionService;

    public ArticleInteractionController(ArticleInteractionService interactionService) {
        this.interactionService = interactionService;
    }

    // ============================ 评论 ============================

    @Operation(summary = "评论列表（游客可访问）")
    @GetMapping("/{id}/comments")
    public Result<List<ArticleCommentVO>> listComments(@PathVariable("id") Long articleId) {
        Long currentUserId = SecurityUtils.getCurrentUserIdOrNull();
        return Result.success(interactionService.listComments(currentUserId, articleId));
    }

    @Operation(summary = "发表评论（可选择昵称/匿名身份）")
    @PostMapping("/{id}/comments")
    public Result<ArticleCommentVO> createComment(@PathVariable("id") Long articleId,
                                                  @Valid @RequestBody CreateCommentRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(interactionService.createComment(userId, articleId, request));
    }

    @Operation(summary = "删除自己的评论")
    @DeleteMapping("/comments/{commentId}")
    public Result<Void> deleteComment(@PathVariable Long commentId) {
        Long userId = SecurityUtils.getCurrentUserId();
        interactionService.deleteComment(userId, commentId);
        return Result.success();
    }

    // ============================ 点赞 ============================

    @Operation(summary = "点赞/取消点赞切换（一人一篇只能赞一次）")
    @PostMapping("/{id}/like")
    public Result<LikeStateVO> toggleLike(@PathVariable("id") Long articleId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(interactionService.toggleLike(userId, articleId));
    }

    @Operation(summary = "评论点赞/取消点赞切换（一人一评只能赞一次）")
    @PostMapping("/comments/{commentId}/like")
    public Result<CommentLikeStateVO> toggleCommentLike(@PathVariable Long commentId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(interactionService.toggleCommentLike(userId, commentId));
    }

    // ============================ 收藏文件夹 ============================

    @Operation(summary = "我的收藏文件夹列表")
    @GetMapping("/folders")
    public Result<List<FavoriteFolderVO>> listFolders() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(interactionService.listFolders(userId));
    }

    @Operation(summary = "新建收藏文件夹（自定义名称）")
    @PostMapping("/folders")
    public Result<FavoriteFolderVO> createFolder(@Valid @RequestBody CreateFolderRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(interactionService.createFolder(userId, request.getName()));
    }

    @Operation(summary = "删除空收藏文件夹")
    @DeleteMapping("/folders/{folderId}")
    public Result<Void> deleteFolder(@PathVariable Long folderId) {
        Long userId = SecurityUtils.getCurrentUserId();
        interactionService.deleteFolder(userId, folderId);
        return Result.success();
    }

    // ============================ 收藏 ============================

    @Operation(summary = "收藏文章到指定文件夹（已收藏则移动文件夹）")
    @PostMapping("/{id}/collect")
    public Result<CollectStateVO> collect(@PathVariable("id") Long articleId,
                                          @RequestBody(required = false) CollectArticleRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long folderId = request == null ? null : request.getFolderId();
        return Result.success(interactionService.collect(userId, articleId, folderId));
    }

    @Operation(summary = "取消收藏")
    @DeleteMapping("/{id}/collect")
    public Result<CollectStateVO> uncollect(@PathVariable("id") Long articleId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(interactionService.uncollect(userId, articleId));
    }

    @Operation(summary = "我的收藏（按文件夹筛选，分页）")
    @GetMapping("/collections")
    public Result<PageResult<ArticleListVO>> getMyCollections(
            @RequestParam(required = false) Long folderId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(interactionService.getMyCollections(userId, folderId, page, size));
    }
}
