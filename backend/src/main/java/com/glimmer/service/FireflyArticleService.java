package com.glimmer.service;

import com.glimmer.common.response.PageResult;
import com.glimmer.service.dto.ArticleListVO;
import com.glimmer.service.dto.ArticleVO;
import com.glimmer.service.dto.PublishArticleRequest;
import com.glimmer.service.dto.UpdateArticleRequest;

/**
 * 萤火交流会文章服务
 */
public interface FireflyArticleService {

    /**
     * 广场文章分页列表（仅公开且审核通过的文章，游客可访问）
     *
     * @param channel 频道: chat 杂谈 / tech 技术笔记，null 或 all 表示全部
     * @param tag     标签精确筛选，null 表示不筛选
     * @param sort    排序: latest 最新发布（默认）/ hot 热度（点赞数+评论数）优先
     * @param keyword 笔名检索关键字（模糊匹配），null 表示不检索
     */
    PageResult<ArticleListVO> getPublicArticles(String channel, String tag, String sort, String keyword, int page, int size);

    /**
     * 文章详情。公开且审核通过的文章所有人可见；
     * 私密或未过审文章仅作者可见（对他人返回404，不暴露存在性）
     *
     * @param currentUserId 当前访问者ID，游客为 null
     */
    ArticleVO getArticleDetail(Long currentUserId, Long id);

    /**
     * 发布文章（笔名随文章存储）
     *
     * @return 新文章ID
     */
    Long publishArticle(Long userId, PublishArticleRequest request);

    /**
     * 编辑文章（仅作者）
     */
    void updateArticle(Long userId, Long id, UpdateArticleRequest request);

    /**
     * 删除文章（仅作者，物理删除）
     */
    void deleteArticle(Long userId, Long id);

    /**
     * 快速切换文章公开/私密状态（仅作者，我的随记使用）
     */
    void changeVisibility(Long userId, Long id, boolean isPublic);

    /**
     * 我的随记：自己的全部文章（含私密），可按频道/公开状态筛选
     */
    PageResult<ArticleListVO> getMyArticles(Long userId, String channel, Boolean isPublic, int page, int size);

    // ============================ 管理员审核 ============================

    /**
     * 管理员文章列表（全量，可按审核状态筛选、按笔名/标题检索）
     *
     * @param reviewStatus 审核状态筛选: APPROVED/PENDING_REVIEW/RETURNED，null 表示全部
     */
    PageResult<ArticleListVO> getAdminArticles(String reviewStatus, String keyword, int page, int size);

    /**
     * 管理员审核文章：approve 审核通过（恢复展示）/ return 屏蔽打回（通知作者修改后重新提审）
     */
    void reviewArticle(Long adminId, Long articleId, String action, String reason);

    /**
     * 管理员删除违规文章（物理删除，级联清理互动数据）
     */
    void adminDeleteArticle(Long adminId, Long articleId);
}
