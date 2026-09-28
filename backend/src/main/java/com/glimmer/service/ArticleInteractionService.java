package com.glimmer.service;

import com.glimmer.common.response.PageResult;
import com.glimmer.service.dto.ArticleCommentVO;
import com.glimmer.service.dto.ArticleListVO;
import com.glimmer.service.dto.ArticleVO;
import com.glimmer.service.dto.CollectStateVO;
import com.glimmer.service.dto.CommentLikeStateVO;
import com.glimmer.service.dto.CreateCommentRequest;
import com.glimmer.service.dto.FavoriteFolderVO;
import com.glimmer.service.dto.LikeStateVO;

import java.util.List;

/**
 * 交流会文章互动服务：评论 / 点赞 / 收藏 / 自定义收藏文件夹
 */
public interface ArticleInteractionService {

    /**
     * 评论列表（公开，游客可访问），按时间正序
     */
    List<ArticleCommentVO> listComments(Long currentUserId, Long articleId);

    /**
     * 发表评论（禁言用户由 checkUserNotMuted 拦截）
     */
    ArticleCommentVO createComment(Long userId, Long articleId, CreateCommentRequest request);

    /**
     * 删除自己的评论（软删除，同步评论计数）
     */
    void deleteComment(Long userId, Long commentId);

    /**
     * 点赞 / 取消点赞切换：同一用户对同一文章只能点赞一次
     *
     * @return 操作后的点赞状态与最新总数
     */
    LikeStateVO toggleLike(Long userId, Long articleId);

    /**
     * 评论点赞 / 取消点赞切换：同一用户对同一条评论只能点赞一次
     *
     * @return 操作后的点赞状态与评论最新点赞总数
     */
    CommentLikeStateVO toggleCommentLike(Long userId, Long commentId);

    /**
     * 当前用户的收藏文件夹列表（含各文件夹收藏数）
     */
    List<FavoriteFolderVO> listFolders(Long userId);

    /**
     * 新建自定义收藏文件夹（同名冲突抛 4009）
     */
    FavoriteFolderVO createFolder(Long userId, String name);

    /**
     * 删除空文件夹（文件夹下仍有收藏时拒绝）
     */
    void deleteFolder(Long userId, Long folderId);

    /**
     * 收藏文章到指定文件夹；已收藏时移动到目标文件夹
     *
     * @param folderId 目标文件夹ID，为空时自动选用/创建"默认收藏"
     */
    CollectStateVO collect(Long userId, Long articleId, Long folderId);

    /**
     * 取消收藏
     */
    CollectStateVO uncollect(Long userId, Long articleId);

    /**
     * 我的收藏：按文件夹筛选（folderId 为空查全部），按收藏时间倒序分页
     */
    PageResult<ArticleListVO> getMyCollections(Long userId, Long folderId, int page, int size);

    /**
     * 列表批量回填当前用户的点赞/收藏状态与互动计数
     */
    void fillListState(Long currentUserId, List<ArticleListVO> list);

    /**
     * 详情回填互动计数与当前用户点赞/收藏状态
     */
    void fillDetailState(Long currentUserId, ArticleVO vo);

    /**
     * 删除文章时级联清理评论/点赞/收藏数据
     */
    void deleteAllByArticle(Long articleId);
}
