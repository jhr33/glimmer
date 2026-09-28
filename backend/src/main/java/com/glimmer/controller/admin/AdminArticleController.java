package com.glimmer.controller.admin;

import com.glimmer.common.response.PageResult;
import com.glimmer.common.response.Result;
import com.glimmer.common.util.SecurityUtils;
import com.glimmer.service.FireflyArticleService;
import com.glimmer.service.dto.ArticleListVO;
import com.glimmer.service.dto.ReviewArticleRequest;
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

/**
 * 管理员文章审核接口（需 ADMIN 角色）
 * <p>
 * 审核流：新文章默认通过；被举报的文章进入待审并对广场隐藏；
 * 管理员可审核通过 / 屏蔽打回（通知作者修改重提）/ 删除违规文章。
 * </p>
 */
@Tag(name = "管理员文章审核接口", description = "文章审核、屏蔽打回、违规删除")
@RestController
@RequestMapping("/api/admin/articles")
public class AdminArticleController {

    private final FireflyArticleService fireflyArticleService;

    public AdminArticleController(FireflyArticleService fireflyArticleService) {
        this.fireflyArticleService = fireflyArticleService;
    }

    @Operation(summary = "文章审核列表（全量，可按审核状态筛选、笔名/标题检索）")
    @GetMapping
    public Result<PageResult<ArticleListVO>> listArticles(
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(fireflyArticleService.getAdminArticles(reviewStatus, keyword, page, size));
    }

    @Operation(summary = "审核文章（approve 通过 / return 屏蔽打回）")
    @PostMapping("/{id}/review")
    public Result<Void> reviewArticle(@PathVariable Long id,
                                      @Valid @RequestBody ReviewArticleRequest request) {
        Long adminId = SecurityUtils.getCurrentUserId();
        fireflyArticleService.reviewArticle(adminId, id, request.getAction(), request.getReason());
        return Result.success();
    }

    @Operation(summary = "删除违规文章（物理删除，级联清理互动数据）")
    @DeleteMapping("/{id}")
    public Result<Void> deleteArticle(@PathVariable Long id) {
        Long adminId = SecurityUtils.getCurrentUserId();
        fireflyArticleService.adminDeleteArticle(adminId, id);
        return Result.success();
    }
}
