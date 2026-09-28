package com.glimmer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.response.PageResult;
import com.glimmer.common.util.BannedWordFilterService;
import com.glimmer.entity.ArticleCollect;
import com.glimmer.entity.ArticleComment;
import com.glimmer.entity.ArticleLike;
import com.glimmer.entity.CollectFolder;
import com.glimmer.entity.CommentLike;
import com.glimmer.entity.FireflyArticle;
import com.glimmer.entity.User;
import com.glimmer.mapper.ArticleCollectMapper;
import com.glimmer.mapper.ArticleCommentMapper;
import com.glimmer.mapper.ArticleLikeMapper;
import com.glimmer.mapper.CollectFolderMapper;
import com.glimmer.mapper.CommentLikeMapper;
import com.glimmer.mapper.FireflyArticleMapper;
import com.glimmer.mapper.UserMapper;
import com.glimmer.service.ArticleInteractionService;
import com.glimmer.service.NotificationService;
import com.glimmer.service.UserService;
import com.glimmer.service.dto.ArticleCommentVO;
import com.glimmer.service.dto.ArticleListVO;
import com.glimmer.service.dto.ArticleVO;
import com.glimmer.service.dto.CollectStateVO;
import com.glimmer.service.dto.CommentLikeStateVO;
import com.glimmer.service.dto.CreateCommentRequest;
import com.glimmer.service.dto.FavoriteFolderVO;
import com.glimmer.service.dto.LikeStateVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 交流会文章互动服务实现：评论 / 点赞 / 收藏 / 自定义收藏文件夹
 * <p>
 * 并发安全：点赞、收藏依赖数据库唯一索引兜底（uk_article_user），
 * 计数变更使用 SQL 原子增减，避免读改写丢失更新。
 * </p>
 */
@Slf4j
@Service
public class ArticleInteractionServiceImpl implements ArticleInteractionService {

    /** 默认收藏文件夹名称（用户首次收藏且未指定文件夹时自动创建） */
    private static final String DEFAULT_FOLDER_NAME = "默认收藏";

    /** 列表摘要最大长度（与文章服务保持一致） */
    private static final int SUMMARY_MAX_LENGTH = 120;

    private final ArticleCommentMapper commentMapper;
    private final ArticleLikeMapper likeMapper;
    private final CommentLikeMapper commentLikeMapper;
    private final ArticleCollectMapper collectMapper;
    private final CollectFolderMapper folderMapper;
    private final FireflyArticleMapper articleMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final BannedWordFilterService bannedWordFilterService;
    private final NotificationService notificationService;

    public ArticleInteractionServiceImpl(ArticleCommentMapper commentMapper,
                                         ArticleLikeMapper likeMapper,
                                         CommentLikeMapper commentLikeMapper,
                                         ArticleCollectMapper collectMapper,
                                         CollectFolderMapper folderMapper,
                                         FireflyArticleMapper articleMapper,
                                         UserMapper userMapper,
                                         UserService userService,
                                         BannedWordFilterService bannedWordFilterService,
                                         NotificationService notificationService) {
        this.commentMapper = commentMapper;
        this.likeMapper = likeMapper;
        this.commentLikeMapper = commentLikeMapper;
        this.collectMapper = collectMapper;
        this.folderMapper = folderMapper;
        this.articleMapper = articleMapper;
        this.userMapper = userMapper;
        this.userService = userService;
        this.bannedWordFilterService = bannedWordFilterService;
        this.notificationService = notificationService;
    }

    // ============================ 评论 ============================

    @Override
    public List<ArticleCommentVO> listComments(Long currentUserId, Long articleId) {
        // 文章对访问者不可见（私密/未过审且非作者）直接404，与详情接口保持一致
        ensureArticleVisible(currentUserId, articleId);
        FireflyArticle article = articleMapper.selectById(articleId);
        Long authorId = article == null ? null : article.getUserId();
        List<ArticleComment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<ArticleComment>()
                        .eq(ArticleComment::getArticleId, articleId)
                        .eq(ArticleComment::getStatus, ArticleComment.STATUS_ACTIVE)
                        .orderByAsc(ArticleComment::getCreatedAt));
        List<ArticleCommentVO> voList = comments.stream()
                .map(c -> toCommentVO(c, currentUserId, authorId))
                .collect(Collectors.toList());
        // 批量回填评论头像：匿名评论显示系统默认头像，昵称评论显示作者自定义头像
        // 作者本人评论即便选择 nickname 模式，avatarUrl 也保持 null（不暴露作者真实头像）
        fillCommentAvatars(voList, comments, authorId);
        // 批量回填当前用户对每条评论的点赞状态（避免 N+1 查询）
        if (currentUserId != null && !voList.isEmpty()) {
            List<Long> commentIds = voList.stream().map(ArticleCommentVO::getId).collect(Collectors.toList());
            Set<Long> likedIds = Set.copyOf(commentLikeMapper.selectLikedCommentIds(currentUserId, commentIds));
            voList.forEach(vo -> vo.setLiked(likedIds.contains(vo.getId())));
        }
        return voList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArticleCommentVO createComment(Long userId, Long articleId, CreateCommentRequest request) {
        // 1. 禁言/封禁用户不能评论（系统自动封禁用户登录后在此被拦截）
        userService.checkUserNotMuted(userId);

        FireflyArticle article = ensureArticleVisible(userId, articleId);
        // 私密文章仅作者本人可评论
        boolean isOwner = article.getUserId().equals(userId);
        if (article.getIsPublic() != null && article.getIsPublic() == 0 && !isOwner) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
        }

        String content = request.getContent().trim();
        // 2. 违禁词检测
        bannedWordFilterService.check(content, "articleComment");

        // 3. 解析回复关系与被回复者展示名快照
        Long parentId = request.getParentId();
        String replyToName = null;
        if (parentId != null) {
            ArticleComment parent = commentMapper.selectById(parentId);
            if (parent == null
                    || !ArticleComment.STATUS_ACTIVE.equals(parent.getStatus())
                    || !parent.getArticleId().equals(articleId)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "回复的评论不存在");
            }
            parentId = parent.getId();
            replyToName = parent.getDisplayName();
        }

        // 4. 解析对外展示名：
        // - 文章作者本人评论：强制使用文章 penName 作为展示名，匿名身份（不暴露真实昵称/头像）
        // - 其他用户：匿名走 user.anonymous_name（24小时统一身份），否则用昵称
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        String displayMode = StringUtils.hasText(request.getDisplayMode()) ? request.getDisplayMode() : "anonymous";
        boolean isArticleAuthor = isOwner; // isOwner 在上方已计算 = article.getUserId().equals(userId)
        String displayName;
        boolean anonymous;
        if (isArticleAuthor) {
            // 作者本人评论固定使用文章笔名 + 匿名身份，前端显示"作者"标记
            displayName = article.getPenName();
            anonymous = true;
        } else {
            displayName = userService.resolveDisplayName(user, displayMode);
            anonymous = !"nickname".equalsIgnoreCase(displayMode);
        }

        // 5. 落库并原子维护评论计数
        ArticleComment comment = new ArticleComment();
        comment.setArticleId(articleId);
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setReplyToName(replyToName);
        comment.setDisplayName(displayName);
        comment.setIsAnonymous(anonymous ? 1 : 0);
        comment.setContent(content);
        comment.setStatus(ArticleComment.STATUS_ACTIVE);
        commentMapper.insert(comment);
        commentMapper.increaseCommentCount(articleId);
        log.info("文章评论发表成功: commentId={}, articleId={}, userId={}, anonymous={}, isAuthor={}",
                comment.getId(), articleId, userId, anonymous, isArticleAuthor);

        // 3.5 互动通知：评论通知文章作者；回复评论通知被回复者（自己操作自己的内容不通知）
        String preview = truncatePreview(content);
        if (!isOwner) {
            notificationService.sendNotification(article.getUserId(), "interaction",
                    "文章收到新评论",
                    String.format("「%s」评论了你的文章《%s》：%s", displayName, article.getTitle(), preview),
                    "article", articleId);
        }
        if (parentId != null) {
            ArticleComment parent = commentMapper.selectById(parentId);
            // 被回复者不是自己且不是文章作者（作者已在上一条收到评论通知，避免重复打扰）
            if (parent != null && !parent.getUserId().equals(userId)
                    && !parent.getUserId().equals(article.getUserId())) {
                notificationService.sendNotification(parent.getUserId(), "interaction",
                        "评论收到新回复",
                        String.format("「%s」回复了你的评论：%s", displayName, preview),
                        "article", articleId);
            }
        }

        ArticleCommentVO vo = toCommentVO(comment, userId, article.getUserId());
        // 作者本人评论：avatarUrl 保持 null（前端显示系统默认头像 + "作者"标记）
        // 其他用户的昵称评论：回填评论作者当前的自定义头像；匿名评论保持 null
        if (!isArticleAuthor && !anonymous && StringUtils.hasText(user.getAvatarUrl())) {
            vo.setAvatarUrl(user.getAvatarUrl());
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long userId, Long commentId) {
        ArticleComment comment = commentMapper.selectById(commentId);
        if (comment == null || ArticleComment.STATUS_DELETED.equals(comment.getStatus())) {
            // 已删除/不存在按幂等处理
            return;
        }
        if (!comment.getUserId().equals(userId)) {
            // 普通用户只能删除自己的评论；管理员可删除任意评论（配合举报审核处理违规评论）
            User operator = userMapper.selectById(userId);
            boolean isAdmin = operator != null && "admin".equals(operator.getRole());
            if (!isAdmin) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "只能删除自己的评论");
            }
            log.info("管理员删除评论: adminId={}, commentId={}, authorId={}", userId, commentId, comment.getUserId());
        }
        comment.setStatus(ArticleComment.STATUS_DELETED);
        commentMapper.updateById(comment);
        commentMapper.decreaseCommentCount(comment.getArticleId());
        // 级联清理该评论的点赞明细，避免孤儿数据
        commentLikeMapper.delete(new LambdaQueryWrapper<CommentLike>()
                .eq(CommentLike::getCommentId, commentId));
        log.info("文章评论删除: commentId={}, articleId={}, userId={}", commentId, comment.getArticleId(), userId);
    }

    // ============================ 点赞 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LikeStateVO toggleLike(Long userId, Long articleId) {
        userService.checkUserNotMuted(userId);
        FireflyArticle article = ensureArticleVisible(userId, articleId);

        ArticleLike existing = likeMapper.selectOne(
                new LambdaQueryWrapper<ArticleLike>()
                        .eq(ArticleLike::getArticleId, articleId)
                        .eq(ArticleLike::getUserId, userId));
        boolean liked;
        if (existing != null) {
            // 已赞 → 取消赞
            likeMapper.deleteById(existing.getId());
            likeMapper.decreaseLikeCount(articleId);
            liked = false;
        } else {
            // 未赞 → 点赞；唯一索引 uk_article_user 兜底并发双击
            try {
                ArticleLike like = new ArticleLike();
                like.setArticleId(articleId);
                like.setUserId(userId);
                likeMapper.insert(like);
                likeMapper.increaseLikeCount(articleId);
                liked = true;
                // 互动通知：点赞文章通知作者（作者自己点赞不通知）
                if (!article.getUserId().equals(userId)) {
                    User actor = userMapper.selectById(userId);
                    String actorName = resolveActorName(actor);
                    notificationService.sendNotification(article.getUserId(), "interaction",
                            "文章收到新点赞",
                            String.format("「%s」赞了你的文章《%s》", actorName, article.getTitle()),
                            "article", articleId);
                }
            } catch (DuplicateKeyException e) {
                // 并发下已被另一请求插入：视为已赞，计数不再重复增加
                liked = true;
            }
        }
        FireflyArticle latest = articleMapper.selectById(articleId);
        return new LikeStateVO(liked, latest.getLikeCount());
    }

    // ============================ 评论点赞 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentLikeStateVO toggleCommentLike(Long userId, Long commentId) {
        userService.checkUserNotMuted(userId);

        ArticleComment comment = commentMapper.selectById(commentId);
        // 已删除/不存在的评论不可点赞
        if (comment == null || !ArticleComment.STATUS_ACTIVE.equals(comment.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在");
        }
        // 评论所在文章对当前用户不可见时不可点赞（私密/未过审且非作者）
        ensureArticleVisible(userId, comment.getArticleId());

        CommentLike existing = commentLikeMapper.selectOne(
                new LambdaQueryWrapper<CommentLike>()
                        .eq(CommentLike::getCommentId, commentId)
                        .eq(CommentLike::getUserId, userId));
        boolean liked;
        if (existing != null) {
            // 已赞 → 取消赞
            commentLikeMapper.deleteById(existing.getId());
            commentLikeMapper.decreaseCommentLikeCount(commentId);
            liked = false;
        } else {
            // 未赞 → 点赞；唯一索引 uk_comment_user 兜底并发双击
            try {
                CommentLike like = new CommentLike();
                like.setCommentId(commentId);
                like.setUserId(userId);
                commentLikeMapper.insert(like);
                commentLikeMapper.increaseCommentLikeCount(commentId);
                liked = true;
                // 互动通知：评论点赞通知评论作者（自己赞自己不通知）
                if (!comment.getUserId().equals(userId)) {
                    FireflyArticle article = articleMapper.selectById(comment.getArticleId());
                    String articleTitle = article == null ? "" : article.getTitle();
                    User actor = userMapper.selectById(userId);
                    String actorName = resolveActorName(actor);
                    notificationService.sendNotification(comment.getUserId(), "interaction",
                            "评论收到新点赞",
                            String.format("「%s」赞了你在文章《%s》中的评论", actorName, articleTitle),
                            "article", comment.getArticleId());
                }
            } catch (DuplicateKeyException e) {
                liked = true;
            }
        }
        ArticleComment latest = commentMapper.selectById(commentId);
        return new CommentLikeStateVO(liked, latest.getLikeCount());
    }

    // ============================ 收藏文件夹 ============================

    @Override
    public List<FavoriteFolderVO> listFolders(Long userId) {
        List<CollectFolder> folders = folderMapper.selectList(
                new LambdaQueryWrapper<CollectFolder>()
                        .eq(CollectFolder::getUserId, userId)
                        .orderByAsc(CollectFolder::getCreatedAt));
        List<FavoriteFolderVO> result = new ArrayList<>(folders.size());
        for (CollectFolder folder : folders) {
            FavoriteFolderVO vo = new FavoriteFolderVO();
            vo.setId(folder.getId());
            vo.setName(folder.getName());
            vo.setCreatedAt(folder.getCreatedAt());
            vo.setCollectCount((int) collectMapper.countByFolder(folder.getId()));
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FavoriteFolderVO createFolder(Long userId, String name) {
        String folderName = name.trim();
        if (!StringUtils.hasText(folderName)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件夹名称不能为空");
        }
        // 同一用户文件夹名唯一（应用层校验给出友好提示，库内无唯一索引以兼容历史数据库大小写）
        Long sameNameCount = folderMapper.selectCount(
                new LambdaQueryWrapper<CollectFolder>()
                        .eq(CollectFolder::getUserId, userId)
                        .eq(CollectFolder::getName, folderName));
        if (sameNameCount != null && sameNameCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "同名文件夹已存在");
        }
        CollectFolder folder = new CollectFolder();
        folder.setUserId(userId);
        folder.setName(folderName);
        folderMapper.insert(folder);
        log.info("新建收藏文件夹: folderId={}, userId={}, name={}", folder.getId(), userId, folderName);

        FavoriteFolderVO vo = new FavoriteFolderVO();
        vo.setId(folder.getId());
        vo.setName(folder.getName());
        vo.setCollectCount(0);
        vo.setCreatedAt(folder.getCreatedAt());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFolder(Long userId, Long folderId) {
        CollectFolder folder = getOwnedFolderOrThrow(userId, folderId);
        long count = collectMapper.countByFolder(folder.getId());
        if (count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "文件夹下还有收藏，请先移出后再删除");
        }
        folderMapper.deleteById(folder.getId());
        log.info("删除收藏文件夹: folderId={}, userId={}", folderId, userId);
    }

    // ============================ 收藏 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectStateVO collect(Long userId, Long articleId, Long folderId) {
        userService.checkUserNotMuted(userId);
        FireflyArticle article = ensureArticleVisible(userId, articleId);

        // 解析目标文件夹：指定则校验归属，未指定则自动选用/创建"默认收藏"
        CollectFolder targetFolder;
        if (folderId != null) {
            targetFolder = getOwnedFolderOrThrow(userId, folderId);
        } else {
            targetFolder = getOrCreateDefaultFolder(userId);
        }

        ArticleCollect existing = collectMapper.selectOne(
                new LambdaQueryWrapper<ArticleCollect>()
                        .eq(ArticleCollect::getArticleId, articleId)
                        .eq(ArticleCollect::getUserId, userId));
        if (existing != null) {
            // 已收藏：移动到目标文件夹（一篇文章对同一用户只有一条收藏记录）
            if (!existing.getFolderId().equals(targetFolder.getId())) {
                existing.setFolderId(targetFolder.getId());
                collectMapper.updateById(existing);
                log.info("移动文章收藏: articleId={}, userId={}, folderId={}", articleId, userId, targetFolder.getId());
            }
            FireflyArticle latest = articleMapper.selectById(articleId);
            return new CollectStateVO(true, targetFolder.getId(), latest.getFavoriteCount());
        }

        try {
            ArticleCollect collect = new ArticleCollect();
            collect.setArticleId(articleId);
            collect.setUserId(userId);
            collect.setFolderId(targetFolder.getId());
            collectMapper.insert(collect);
            collectMapper.increaseFavoriteCount(articleId);
            // 互动通知：首次收藏通知作者（作者自己收藏不通知；并发重复收藏由唯一键拦截不重复通知）
            if (!article.getUserId().equals(userId)) {
                User actor = userMapper.selectById(userId);
                String actorName = resolveActorName(actor);
                notificationService.sendNotification(article.getUserId(), "interaction",
                        "文章被收藏",
                        String.format("「%s」收藏了你的文章《%s》", actorName, article.getTitle()),
                        "article", articleId);
            }
        } catch (DuplicateKeyException e) {
            // 并发兜底：唯一键冲突说明已收藏，改为移动文件夹
            ArticleCollect concurrent = collectMapper.selectOne(
                    new LambdaQueryWrapper<ArticleCollect>()
                            .eq(ArticleCollect::getArticleId, articleId)
                            .eq(ArticleCollect::getUserId, userId));
            if (concurrent != null && !concurrent.getFolderId().equals(targetFolder.getId())) {
                concurrent.setFolderId(targetFolder.getId());
                collectMapper.updateById(concurrent);
            }
        }
        log.info("文章收藏成功: articleId={}, userId={}, folderId={}", articleId, userId, targetFolder.getId());
        FireflyArticle latest = articleMapper.selectById(articleId);
        return new CollectStateVO(true, targetFolder.getId(), latest.getFavoriteCount());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectStateVO uncollect(Long userId, Long articleId) {
        ArticleCollect existing = collectMapper.selectOne(
                new LambdaQueryWrapper<ArticleCollect>()
                        .eq(ArticleCollect::getArticleId, articleId)
                        .eq(ArticleCollect::getUserId, userId));
        if (existing == null) {
            // 未收藏按幂等处理，直接返回当前状态
            FireflyArticle article = articleMapper.selectById(articleId);
            return new CollectStateVO(false, null, article == null ? 0 : article.getFavoriteCount());
        }
        collectMapper.deleteById(existing.getId());
        collectMapper.decreaseFavoriteCount(articleId);
        log.info("取消文章收藏: articleId={}, userId={}", articleId, userId);
        FireflyArticle latest = articleMapper.selectById(articleId);
        return new CollectStateVO(false, null, latest.getFavoriteCount());
    }

    @Override
    public PageResult<ArticleListVO> getMyCollections(Long userId, Long folderId, int page, int size) {
        // 1. 按收藏关系分页（收藏时间倒序）
        Page<ArticleCollect> collectPage = collectMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ArticleCollect>()
                        .eq(ArticleCollect::getUserId, userId)
                        .eq(folderId != null, ArticleCollect::getFolderId, folderId)
                        .orderByDesc(ArticleCollect::getCreatedAt));
        if (collectPage.getRecords().isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0L, page, size);
        }

        // 2. 批量取文章（作者删除文章后收藏记录可能成为孤儿数据，直接过滤）
        List<Long> articleIds = collectPage.getRecords().stream()
                .map(ArticleCollect::getArticleId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, FireflyArticle> articleMap = articleMapper.selectBatchIds(articleIds).stream()
                .collect(Collectors.toMap(FireflyArticle::getId, a -> a, (a, b) -> a));
        Map<Long, ArticleCollect> collectMap = collectPage.getRecords().stream()
                .collect(Collectors.toMap(ArticleCollect::getArticleId, c -> c, (a, b) -> a));

        List<ArticleListVO> list = new ArrayList<>();
        for (ArticleCollect collect : collectPage.getRecords()) {
            FireflyArticle article = articleMap.get(collect.getArticleId());
            if (article == null) {
                continue;
            }
            ArticleListVO vo = toListVO(article);
            vo.setCollected(true);
            vo.setFolderId(collect.getFolderId());
            vo.setCollectedAt(collect.getCreatedAt());
            list.add(vo);
        }
        // 批量回填作者头像（无自定义头像时为 null，前端按 userId 显示系统默认头像）
        fillAuthorAvatars(list);
        // 我的收藏页一并回填点赞状态
        fillLikedState(userId, list);
        return new PageResult<>(list, collectPage.getTotal(), page, size);
    }

    // ============================ 状态回填 ============================

    @Override
    public void fillListState(Long currentUserId, List<ArticleListVO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        // 游客：只给默认 false，不查库
        list.forEach(vo -> {
            vo.setLiked(false);
            vo.setCollected(false);
        });
        if (currentUserId == null) {
            return;
        }
        fillLikedState(currentUserId, list);
        List<Long> articleIds = list.stream().map(ArticleListVO::getId).collect(Collectors.toList());
        Map<Long, ArticleCollect> collectMap = collectMapper
                .selectByUserAndArticles(currentUserId, articleIds).stream()
                .collect(Collectors.toMap(ArticleCollect::getArticleId, c -> c, (a, b) -> a));
        list.forEach(vo -> {
            ArticleCollect collect = collectMap.get(vo.getId());
            vo.setCollected(collect != null);
            if (collect != null) {
                vo.setFolderId(collect.getFolderId());
            }
        });
    }

    @Override
    public void fillDetailState(Long currentUserId, ArticleVO vo) {
        vo.setLiked(false);
        vo.setCollected(false);
        if (currentUserId == null) {
            return;
        }
        Long likeCount = likeMapper.selectCount(
                new LambdaQueryWrapper<ArticleLike>()
                        .eq(ArticleLike::getArticleId, vo.getId())
                        .eq(ArticleLike::getUserId, currentUserId));
        vo.setLiked(likeCount != null && likeCount > 0);

        ArticleCollect collect = collectMapper.selectOne(
                new LambdaQueryWrapper<ArticleCollect>()
                        .eq(ArticleCollect::getArticleId, vo.getId())
                        .eq(ArticleCollect::getUserId, currentUserId));
        vo.setCollected(collect != null);
        if (collect != null) {
            vo.setFolderId(collect.getFolderId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAllByArticle(Long articleId) {
        // 先取出该文章全部评论ID，级联清理评论点赞明细，再清理评论/点赞/收藏
        List<Long> commentIds = commentMapper.selectList(new LambdaQueryWrapper<ArticleComment>()
                        .select(ArticleComment::getId)
                        .eq(ArticleComment::getArticleId, articleId))
                .stream().map(ArticleComment::getId).collect(Collectors.toList());
        if (!commentIds.isEmpty()) {
            commentLikeMapper.delete(new LambdaQueryWrapper<CommentLike>()
                    .in(CommentLike::getCommentId, commentIds));
        }
        commentMapper.delete(new LambdaQueryWrapper<ArticleComment>()
                .eq(ArticleComment::getArticleId, articleId));
        likeMapper.delete(new LambdaQueryWrapper<ArticleLike>()
                .eq(ArticleLike::getArticleId, articleId));
        collectMapper.delete(new LambdaQueryWrapper<ArticleCollect>()
                .eq(ArticleCollect::getArticleId, articleId));
        log.info("文章互动数据级联清理完成: articleId={}", articleId);
    }

    // ============================ 私有方法 ============================

    /** 批量回填点赞状态（收藏列表等场景复用） */
    private void fillLikedState(Long userId, List<ArticleListVO> list) {
        if (userId == null || list.isEmpty()) {
            return;
        }
        List<Long> articleIds = list.stream().map(ArticleListVO::getId).collect(Collectors.toList());
        Set<Long> likedIds = Set.copyOf(likeMapper.selectLikedArticleIds(userId, articleIds));
        list.forEach(vo -> vo.setLiked(likedIds.contains(vo.getId())));
    }

    /**
     * 校验文章对当前访问者可见并返回文章实体：
     * 公开且审核通过 → 所有人可见；私密或未过审（待审/打回）→ 仅作者本人可见，其余统一 404 不暴露存在性
     */
    private FireflyArticle ensureArticleVisible(Long currentUserId, Long articleId) {
        FireflyArticle article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        boolean isOwner = currentUserId != null && currentUserId.equals(article.getUserId());
        if (!isOwner) {
            boolean hidden = (article.getIsPublic() == null || article.getIsPublic() == 0)
                    || !FireflyArticle.REVIEW_APPROVED.equals(article.getReviewStatus());
            if (hidden) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
            }
        }
        return article;
    }

    /** 互动通知中的操作者展示名：优先昵称，缺失时用兜底名 */
    private String resolveActorName(User actor) {
        if (actor != null && StringUtils.hasText(actor.getNickname())) {
            return actor.getNickname();
        }
        return "匿名萤火虫";
    }

    /** 通知内容中的正文预览：截取前50字 */
    private String truncatePreview(String content) {
        if (content == null) {
            return "";
        }
        return content.length() > 50 ? content.substring(0, 50) + "..." : content;
    }

    /** 查询文件夹并校验归属权 */
    private CollectFolder getOwnedFolderOrThrow(Long userId, Long folderId) {
        CollectFolder folder = folderMapper.selectById(folderId);
        if (folder == null || !folder.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "收藏文件夹不存在");
        }
        return folder;
    }

    /** 获取用户首个文件夹，没有则自动创建"默认收藏" */
    private CollectFolder getOrCreateDefaultFolder(Long userId) {
        CollectFolder folder = folderMapper.selectOne(
                new LambdaQueryWrapper<CollectFolder>()
                        .eq(CollectFolder::getUserId, userId)
                        .orderByAsc(CollectFolder::getCreatedAt)
                        .last("LIMIT 1"));
        if (folder != null) {
            return folder;
        }
        folder = new CollectFolder();
        folder.setUserId(userId);
        folder.setName(DEFAULT_FOLDER_NAME);
        folderMapper.insert(folder);
        log.info("自动创建默认收藏文件夹: folderId={}, userId={}", folder.getId(), userId);
        return folder;
    }

    private ArticleCommentVO toCommentVO(ArticleComment comment, Long currentUserId, Long articleAuthorId) {
        ArticleCommentVO vo = new ArticleCommentVO();
        vo.setId(comment.getId());
        vo.setArticleId(comment.getArticleId());
        vo.setUserId(comment.getUserId());
        vo.setParentId(comment.getParentId());
        vo.setReplyToName(comment.getReplyToName());
        vo.setDisplayName(comment.getDisplayName());
        vo.setIsAnonymous(comment.getIsAnonymous() != null && comment.getIsAnonymous() == 1);
        vo.setContent(comment.getContent());
        vo.setLikeCount(comment.getLikeCount() == null ? 0 : comment.getLikeCount());
        vo.setLiked(false);
        vo.setCreatedAt(comment.getCreatedAt());
        vo.setOwner(currentUserId != null && currentUserId.equals(comment.getUserId()));
        // 是否文章作者本人评论（前端显示"作者"标记，且不显示"匿名"标签）
        vo.setIsAuthor(articleAuthorId != null && articleAuthorId.equals(comment.getUserId()));
        return vo;
    }

    /**
     * 批量回填评论头像（避免 N+1）：
     * 匿名评论（is_anonymous=1）头像固定 null，前端显示系统默认头像；
     * 昵称评论返回评论作者当前的自定义头像（可能为 null）；
     * 文章作者本人评论即便选了 nickname 模式，avatarUrl 也强制 null（不暴露作者真实头像）。
     *
     * @param voList          评论 VO 列表（与 comments 索引一一对应）
     * @param comments        原始评论实体列表
     * @param articleAuthorId 文章作者 userId（用于跳过作者评论的头像回填）
     */
    private void fillCommentAvatars(List<ArticleCommentVO> voList, List<ArticleComment> comments, Long articleAuthorId) {
        if (voList == null || voList.isEmpty()) {
            return;
        }
        // 仅"非作者 + 昵称评论"需要查作者头像，匿名评论和作者评论统一走默认头像
        Set<Long> nicknameUserIds = comments.stream()
                .filter(c -> (c.getIsAnonymous() == null || c.getIsAnonymous() != 1)
                        && (articleAuthorId == null || !articleAuthorId.equals(c.getUserId())))
                .map(ArticleComment::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> avatarMap = nicknameUserIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(nicknameUserIds).stream()
                        .filter(u -> StringUtils.hasText(u.getAvatarUrl()))
                        .collect(Collectors.toMap(User::getId, User::getAvatarUrl, (String a, String b) -> a));
        for (int i = 0; i < voList.size() && i < comments.size(); i++) {
            ArticleComment c = comments.get(i);
            // 作者评论和匿名评论都保持 null 头像
            boolean shouldFill = (c.getIsAnonymous() == null || c.getIsAnonymous() != 1)
                    && (articleAuthorId == null || !articleAuthorId.equals(c.getUserId()));
            if (shouldFill) {
                voList.get(i).setAvatarUrl(avatarMap.get(c.getUserId()));
            }
        }
    }

    /**
     * 批量回填文章列表作者头像。
     * 交流会匿名社交策略：作者头像统一为 null（前端按 userId 显示系统默认头像），
     * 不暴露作者真实头像，避免身份跨文章关联。
     */
    private void fillAuthorAvatars(List<ArticleListVO> list) {
        if (list == null) return;
        list.forEach(vo -> vo.setAuthorAvatar(null));
    }

    /** 文章 → 列表 VO（我的收藏页使用，与文章服务的列表字段保持一致） */
    private ArticleListVO toListVO(FireflyArticle article) {
        ArticleListVO vo = new ArticleListVO();
        vo.setId(article.getId());
        vo.setAuthorId(article.getUserId());
        vo.setPenName(article.getPenName());
        vo.setChannel(article.getChannel());
        vo.setTitle(article.getTitle());
        vo.setSummary(buildSummary(article.getContent()));
        vo.setTags(parseTags(article.getTags()));
        vo.setIsPublic(article.getIsPublic() != null && article.getIsPublic() == 1);
        vo.setViewCount(article.getViewCount());
        vo.setLikeCount(article.getLikeCount());
        vo.setCommentCount(article.getCommentCount());
        vo.setFavoriteCount(article.getFavoriteCount());
        vo.setCreatedAt(article.getCreatedAt());
        return vo;
    }

    private String buildSummary(String content) {
        if (content == null) {
            return "";
        }
        String folded = content.replaceAll("\\s+", " ").trim();
        return folded.length() > SUMMARY_MAX_LENGTH ? folded.substring(0, SUMMARY_MAX_LENGTH) + "..." : folded;
    }

    private List<String> parseTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return Collections.emptyList();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }
}
