package com.glimmer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.response.PageResult;
import com.glimmer.entity.FireflyArticle;
import com.glimmer.entity.User;
import com.glimmer.entity.UserFollow;
import com.glimmer.mapper.FireflyArticleMapper;
import com.glimmer.mapper.UserFollowMapper;
import com.glimmer.mapper.UserMapper;
import com.glimmer.service.UserFollowService;
import com.glimmer.service.dto.ArticleListVO;
import com.glimmer.service.dto.FollowVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 交流会作者关注服务实现
 * <p>
 * 关注目标为「账户 + 笔名」组合（笔名跟随文章署名存储，非账户字段）：
 * 同一账户可被关注多个笔名；自定义备注用于区分不同账户使用同一笔名的情况。
 * 唯一键 uk_follower_author_pen 兜底并发重复关注。
 * </p>
 */
@Slf4j
@Service
public class UserFollowServiceImpl implements UserFollowService {

    private final UserFollowMapper userFollowMapper;
    private final UserMapper userMapper;
    private final FireflyArticleMapper fireflyArticleMapper;

    public UserFollowServiceImpl(UserFollowMapper userFollowMapper,
                                 UserMapper userMapper,
                                 FireflyArticleMapper fireflyArticleMapper) {
        this.userFollowMapper = userFollowMapper;
        this.userMapper = userMapper;
        this.fireflyArticleMapper = fireflyArticleMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long follow(Long followerId, Long followeeId, String penName, String remark) {
        // 不能关注自己（自己文章的笔名无关注意义）
        if (followerId.equals(followeeId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能关注自己");
        }
        User followee = userMapper.selectById(followeeId);
        if (followee == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "该作者不存在");
        }
        String trimmedPenName = penName.trim();
        String trimmedRemark = StringUtils.hasText(remark) ? remark.trim() : null;

        UserFollow follow = new UserFollow();
        follow.setFollowerId(followerId);
        follow.setFolloweeId(followeeId);
        follow.setPenName(trimmedPenName);
        follow.setRemark(trimmedRemark);
        try {
            userFollowMapper.insert(follow);
        } catch (DuplicateKeyException e) {
            // 已关注同一「账户+笔名」：若本次携带备注则顺带更新备注，否则提示重复
            if (trimmedRemark != null) {
                UserFollow existing = userFollowMapper.selectOne(new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, followerId)
                        .eq(UserFollow::getFolloweeId, followeeId)
                        .eq(UserFollow::getPenName, trimmedPenName));
                if (existing != null) {
                    existing.setRemark(trimmedRemark);
                    userFollowMapper.updateById(existing);
                    return existing.getId();
                }
            }
            throw new BusinessException(ErrorCode.CONFLICT, "已关注该作者");
        }
        log.info("关注作者成功: followerId={}, followeeId={}, penName={}", followerId, followeeId, trimmedPenName);
        return follow.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfollow(Long followerId, Long followId) {
        UserFollow follow = getOwnedFollowOrThrow(followerId, followId);
        userFollowMapper.deleteById(follow.getId());
        log.info("取消关注作者: followId={}, followerId={}", followId, followerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRemark(Long followerId, Long followId, String remark) {
        UserFollow follow = getOwnedFollowOrThrow(followerId, followId);
        follow.setRemark(StringUtils.hasText(remark) ? remark.trim() : null);
        userFollowMapper.updateById(follow);
        log.info("修改关注备注: followId={}, followerId={}, remark={}", followId, followerId, follow.getRemark());
    }

    @Override
    public PageResult<FollowVO> listFollows(Long followerId, int page, int size) {
        IPage<UserFollow> result = userFollowMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, followerId)
                        .orderByDesc(UserFollow::getCreatedAt));
        List<FollowVO> list = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return new PageResult<>(list, result.getTotal(), page, size);
    }

    @Override
    public PageResult<ArticleListVO> getFollowingFeed(Long followerId, int page, int size) {
        // 1. 取出全部关注组合（关注数量通常有限，一次查完再拼 OR 条件）
        List<UserFollow> follows = userFollowMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, followerId)
                .orderByDesc(UserFollow::getCreatedAt));
        if (follows.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0L, page, size);
        }

        // 2. 拼接 OR 条件：(user_id = ? AND pen_name = ?) OR ...
        LambdaQueryWrapper<FireflyArticle> wrapper = new LambdaQueryWrapper<FireflyArticle>()
                .eq(FireflyArticle::getIsPublic, 1)
                .eq(FireflyArticle::getReviewStatus, FireflyArticle.REVIEW_APPROVED)
                .and(w -> {
                    for (int i = 0; i < follows.size(); i++) {
                        UserFollow f = follows.get(i);
                        w.or(inner -> inner
                                .eq(FireflyArticle::getUserId, f.getFolloweeId())
                                .eq(FireflyArticle::getPenName, f.getPenName()));
                    }
                })
                .orderByDesc(FireflyArticle::getCreatedAt);

        IPage<FireflyArticle> result = fireflyArticleMapper.selectPage(new Page<>(page, size), wrapper);
        List<FireflyArticle> records = result.getRecords();
        List<ArticleListVO> list = records.stream()
                .map(this::toListVO)
                .collect(Collectors.toList());
        // 批量回填作者头像（无自定义头像时为 null，前端按 userId 显示系统默认头像）
        fillAuthorAvatars(list, records);
        return new PageResult<>(list, result.getTotal(), page, size);
    }

    /**
     * 批量填充关注文章流作者头像（避免 N+1）：按作者ID批量查询 user.avatar_url。
     * 作者无自定义头像时 authorAvatar 保持 null，前端按 userId 显示系统默认头像。
     */
    private void fillAuthorAvatars(List<ArticleListVO> list, List<FireflyArticle> articles) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Set<Long> userIds = articles.stream()
                .map(FireflyArticle::getUserId)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, String> avatarMap = userMapper.selectBatchIds(userIds).stream()
                .filter(u -> StringUtils.hasText(u.getAvatarUrl()))
                .collect(Collectors.toMap(User::getId, User::getAvatarUrl, (a, b) -> a));
        for (int i = 0; i < list.size() && i < articles.size(); i++) {
            list.get(i).setAuthorAvatar(avatarMap.get(articles.get(i).getUserId()));
        }
    }

    // ============================ 私有方法 ============================

    /** 查询关注记录并校验归属权：不存在或非本人抛 404 */
    private UserFollow getOwnedFollowOrThrow(Long followerId, Long followId) {
        UserFollow follow = userFollowMapper.selectById(followId);
        if (follow == null || !follow.getFollowerId().equals(followerId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "关注记录不存在");
        }
        return follow;
    }

    private FollowVO toVO(UserFollow follow) {
        FollowVO vo = new FollowVO();
        vo.setId(follow.getId());
        vo.setFolloweeId(follow.getFolloweeId());
        vo.setPenName(follow.getPenName());
        vo.setRemark(follow.getRemark());
        vo.setCreatedAt(follow.getCreatedAt());
        // 该账户该笔名下当前公开且审核通过的文章数
        vo.setArticleCount(fireflyArticleMapper.selectCount(new LambdaQueryWrapper<FireflyArticle>()
                .eq(FireflyArticle::getUserId, follow.getFolloweeId())
                .eq(FireflyArticle::getPenName, follow.getPenName())
                .eq(FireflyArticle::getIsPublic, 1)
                .eq(FireflyArticle::getReviewStatus, FireflyArticle.REVIEW_APPROVED)));
        return vo;
    }

    /** 文章 → 列表 VO（关注文章流使用，字段与广场列表保持一致） */
    private ArticleListVO toListVO(FireflyArticle article) {
        ArticleListVO vo = new ArticleListVO();
        vo.setId(article.getId());
        vo.setAuthorId(article.getUserId());
        vo.setPenName(article.getPenName());
        vo.setChannel(article.getChannel());
        vo.setTitle(article.getTitle());
        vo.setSummary(summarize(article.getContent()));
        vo.setTags(parseTags(article.getTags()));
        vo.setIsPublic(article.getIsPublic() != null && article.getIsPublic() == 1);
        vo.setViewCount(article.getViewCount());
        vo.setLikeCount(article.getLikeCount());
        vo.setCommentCount(article.getCommentCount());
        vo.setFavoriteCount(article.getFavoriteCount());
        vo.setCreatedAt(article.getCreatedAt());
        return vo;
    }

    private String summarize(String content) {
        if (content == null) {
            return "";
        }
        String folded = content.replaceAll("\\s+", " ").trim();
        return folded.length() > 120 ? folded.substring(0, 120) + "..." : folded;
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
