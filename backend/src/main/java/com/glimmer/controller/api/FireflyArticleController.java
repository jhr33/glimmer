package com.glimmer.controller.api;

import com.glimmer.common.response.PageResult;
import com.glimmer.common.response.Result;
import com.glimmer.common.util.SecurityUtils;
import com.glimmer.service.ArticleInteractionService;
import com.glimmer.service.FireflyArticleService;
import com.glimmer.service.dto.ArticleListVO;
import com.glimmer.service.dto.ArticleVO;
import com.glimmer.service.dto.PublishArticleRequest;
import com.glimmer.service.dto.UpdateArticleRequest;
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
 * 萤火交流会文章接口
 * <p>
 * 广场 GET 接口游客可访问（见 SecurityConfig 白名单）；
 * 发布/编辑/删除/我的随记 必须登录，Controller 内强制取当前用户ID。
 * </p>
 */
@Tag(name = "萤火交流会接口", description = "文章广场、发布、我的随记管理")
@RestController
@RequestMapping("/api/articles")
public class FireflyArticleController {

    private final FireflyArticleService fireflyArticleService;
    private final ArticleInteractionService articleInteractionService;

    public FireflyArticleController(FireflyArticleService fireflyArticleService,
                                    ArticleInteractionService articleInteractionService) {
        this.fireflyArticleService = fireflyArticleService;
        this.articleInteractionService = articleInteractionService;
    }

    @Operation(summary = "广场文章列表（分页，仅公开且审核通过，游客可访问；支持热度/最新排序与笔名检索）")
    @GetMapping
    public Result<PageResult<ArticleListVO>> getPublicArticles(
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResult<ArticleListVO> result = fireflyArticleService.getPublicArticles(channel, tag, sort, keyword, page, size);
        // 回填当前登录用户的点赞/收藏状态（游客只返回计数）
        articleInteractionService.fillListState(SecurityUtils.getCurrentUserIdOrNull(), result.getList());
        return Result.success(result);
    }

    @Operation(summary = "文章详情（公开文章游客可访问，私密文章仅作者）")
    @GetMapping("/{id}")
    public Result<ArticleVO> getArticleDetail(@PathVariable Long id) {
        // 游客场景返回 null，由 Service 判断私密文章访问权限
        Long currentUserId = SecurityUtils.getCurrentUserIdOrNull();
        ArticleVO vo = fireflyArticleService.getArticleDetail(currentUserId, id);
        articleInteractionService.fillDetailState(currentUserId, vo);
        return Result.success(vo);
    }

    @Operation(summary = "发布文章（笔名随文章署名）")
    @PostMapping
    public Result<Long> publishArticle(@Valid @RequestBody PublishArticleRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long articleId = fireflyArticleService.publishArticle(userId, request);
        return Result.success(articleId);
    }

    @Operation(summary = "编辑文章（仅作者）")
    @PutMapping("/{id}")
    public Result<Void> updateArticle(@PathVariable Long id,
                                      @Valid @RequestBody UpdateArticleRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        fireflyArticleService.updateArticle(userId, id, request);
        return Result.success();
    }

    @Operation(summary = "删除文章（仅作者）")
    @DeleteMapping("/{id}")
    public Result<Void> deleteArticle(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        fireflyArticleService.deleteArticle(userId, id);
        return Result.success();
    }

    @Operation(summary = "切换文章公开/私密状态（仅作者）")
    @PutMapping("/{id}/visibility")
    public Result<Void> changeVisibility(@PathVariable Long id,
                                         @RequestParam Boolean isPublic) {
        Long userId = SecurityUtils.getCurrentUserId();
        fireflyArticleService.changeVisibility(userId, id, Boolean.TRUE.equals(isPublic));
        return Result.success();
    }

    @Operation(summary = "我的随记：自己的文章列表（含私密，必须登录）")
    @GetMapping("/mine")
    public Result<PageResult<ArticleListVO>> getMyArticles(
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Boolean isPublic,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        PageResult<ArticleListVO> result = fireflyArticleService.getMyArticles(userId, channel, isPublic, page, size);
        articleInteractionService.fillListState(userId, result.getList());
        return Result.success(result);
    }
}
