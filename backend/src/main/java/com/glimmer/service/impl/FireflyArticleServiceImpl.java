package com.glimmer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.response.PageResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.glimmer.common.util.BannedWordFilterService;
import com.glimmer.entity.FireflyArticle;
import com.glimmer.entity.User;
import com.glimmer.mapper.FireflyArticleMapper;
import com.glimmer.mapper.UserFollowMapper;
import com.glimmer.mapper.UserMapper;
import com.glimmer.entity.UserFollow;
import com.glimmer.service.ArticleInteractionService;
import com.glimmer.service.FireflyArticleService;
import com.glimmer.service.NotificationService;
import com.glimmer.service.UserService;
import com.glimmer.service.dto.ArticleListVO;
import com.glimmer.service.dto.ArticleVO;
import com.glimmer.service.dto.PublishArticleRequest;
import com.glimmer.service.dto.UpdateArticleRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 萤火交流会文章服务实现
 */
@Slf4j
@Service
public class FireflyArticleServiceImpl implements FireflyArticleService {

    /** 合法频道白名单：chat 杂谈 / tech 技术笔记 */
    private static final Set<String> ALLOWED_CHANNELS = Set.of("chat", "tech");

    /** 单篇文章最多标签数 */
    private static final int MAX_TAG_COUNT = 5;

    /** 单个标签最大长度 */
    private static final int MAX_TAG_LENGTH = 10;

    /** 列表摘要最大长度 */
    private static final int SUMMARY_MAX_LENGTH = 120;

    /** 列表页图片最多展示张数 */
    private static final int LIST_IMAGE_PREVIEW_COUNT = 3;

    /** 正文内嵌图片的 Markdown 标记：![任意说明](http(s)图片地址) */
    private static final java.util.regex.Pattern INLINE_IMAGE_PATTERN =
            java.util.regex.Pattern.compile("!\\[[^]]*]\\((https?://[^)\\s]+)\\)");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FireflyArticleMapper fireflyArticleMapper;
    private final UserFollowMapper userFollowMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final BannedWordFilterService bannedWordFilterService;
    private final ArticleInteractionService articleInteractionService;
    private final NotificationService notificationService;

    public FireflyArticleServiceImpl(FireflyArticleMapper fireflyArticleMapper,
                                     UserFollowMapper userFollowMapper,
                                     UserMapper userMapper,
                                     UserService userService,
                                     BannedWordFilterService bannedWordFilterService,
                                     ArticleInteractionService articleInteractionService,
                                     NotificationService notificationService) {
        this.fireflyArticleMapper = fireflyArticleMapper;
        this.userFollowMapper = userFollowMapper;
        this.userMapper = userMapper;
        this.userService = userService;
        this.bannedWordFilterService = bannedWordFilterService;
        this.articleInteractionService = articleInteractionService;
        this.notificationService = notificationService;
    }

    @Override
    public PageResult<ArticleListVO> getPublicArticles(String channel, String tag, String sort, String keyword, int page, int size) {
        // 预处理检索关键字：避免在 wrapper 条件参数中直接调用 keyword.trim() 造成的空指针
        String trimmedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String trimmedTag = StringUtils.hasText(tag) ? tag.trim() : null;

        LambdaQueryWrapper<FireflyArticle> wrapper = new LambdaQueryWrapper<FireflyArticle>()
                .eq(FireflyArticle::getIsPublic, 1)
                // 审核流：仅审核通过的文章对广场可见（默认通过，被举报/打回的文章不可见）
                .eq(FireflyArticle::getReviewStatus, FireflyArticle.REVIEW_APPROVED)
                .eq(isValidChannel(channel), FireflyArticle::getChannel, channel)
                // FIND_IN_SET 精确匹配逗号分隔字段中的某个标签（点击文章上的标签筛选时使用）
                .apply(trimmedTag != null, "FIND_IN_SET({0}, tags) > 0", trimmedTag)
                // 关键字检索：一个关键字同时模糊匹配 笔名 / 标题 / 标签（OR），
                // 用嵌套 and(...) 保证括号隔离，不与前面的 channel/tag 等 AND 条件互相串味
                .and(trimmedKeyword != null, w -> w
                        .like(FireflyArticle::getPenName, trimmedKeyword)
                        .or().like(FireflyArticle::getTitle, trimmedKeyword)
                        .or().like(FireflyArticle::getTags, trimmedKeyword));

        if ("hot".equalsIgnoreCase(sort)) {
            // 热度排序：点赞数 + 评论数降序，同热度按发布时间倒序
            wrapper.last("ORDER BY (like_count + comment_count) DESC, created_at DESC");
        } else {
            wrapper.orderByDesc(FireflyArticle::getCreatedAt);
        }

        IPage<FireflyArticle> result = fireflyArticleMapper.selectPage(new Page<>(page, size), wrapper);
        List<FireflyArticle> records = result.getRecords();
        List<ArticleListVO> list = records.stream()
                .map(this::toListVO)
                .collect(Collectors.toList());
        fillAuthorAvatars(list, records);
        return new PageResult<>(list, result.getTotal(), page, size);
    }

    @Override
    public ArticleVO getArticleDetail(Long currentUserId, Long id) {
        FireflyArticle article = fireflyArticleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        boolean isOwner = article.getUserId().equals(currentUserId);
        // 私密或未过审（待审/打回）文章非作者访问：统一返回404，不向他人暴露文章存在性
        if (!isOwner) {
            boolean hidden = (article.getIsPublic() == null || article.getIsPublic() == 0)
                    || !FireflyArticle.REVIEW_APPROVED.equals(article.getReviewStatus());
            if (hidden) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
            }
        }

        // 非作者访问才计入浏览量（作者自己回看不算）
        if (!isOwner) {
            fireflyArticleMapper.update(null, new LambdaUpdateWrapper<FireflyArticle>()
                    .eq(FireflyArticle::getId, id)
                    .setSql("view_count = view_count + 1"));
            article.setViewCount(article.getViewCount() == null ? 1 : article.getViewCount() + 1);
        }
        ArticleVO vo = toVO(article, isOwner);
        // 回填当前用户是否已关注该「账户+笔名」作者（游客/作者本人不显示关注态），
        // 同时回填关注记录ID，便于详情页一键取关
        if (currentUserId != null && !isOwner) {
            UserFollow followRecord = userFollowMapper.selectOne(new LambdaQueryWrapper<UserFollow>()
                    .eq(UserFollow::getFollowerId, currentUserId)
                    .eq(UserFollow::getFolloweeId, article.getUserId())
                    .eq(UserFollow::getPenName, article.getPenName())
                    .last("LIMIT 1"));
            vo.setFollowing(followRecord != null);
            vo.setFollowId(followRecord == null ? null : followRecord.getId());
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long publishArticle(Long userId, PublishArticleRequest request) {
        // 1. 禁言用户不可发布（与漂流瓶等写操作保持一致的 Service 层拦截）
        userService.checkUserNotMuted(userId);

        String channel = normalizeChannel(request.getChannel());
        String title = request.getTitle().trim();
        String content = request.getContent().trim();
        String penName = request.getPenName().trim();
        List<String> tags = normalizeTags(request.getTags());

        // 2. 违禁词检测：笔名/标题/正文均需过审。
        // 正文先剔除内嵌图片标记（OSS URL 不是用户表达内容），只检测文字部分
        bannedWordFilterService.check(penName, "publishArticle.penName");
        bannedWordFilterService.check(title, "publishArticle.title");
        bannedWordFilterService.check(stripInlineImages(content), "publishArticle.content");

        FireflyArticle article = new FireflyArticle();
        article.setUserId(userId);
        article.setPenName(penName);
        article.setChannel(channel);
        article.setTitle(title);
        article.setContent(content);
        article.setTags(joinTags(tags));
        // 图片不限制张数：从正文 Markdown 标记提取 URL 落库，供列表页缩略图预览
        article.setImages(joinImages(extractInlineImageUrls(content)));
        article.setIsPublic(Boolean.FALSE.equals(request.getIsPublic()) ? 0 : 1);
        article.setViewCount(0);
        // 审核流：新文章默认通过，直接对广场可见
        article.setReviewStatus(FireflyArticle.REVIEW_APPROVED);
        fireflyArticleMapper.insert(article);
        log.info("萤火文章发布成功: articleId={}, userId={}, channel={}", article.getId(), userId, channel);
        return article.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateArticle(Long userId, Long id, UpdateArticleRequest request) {
        FireflyArticle article = getOwnedArticleOrThrow(userId, id);

        // 禁言用户不可编辑发布内容
        userService.checkUserNotMuted(userId);

        String channel = normalizeChannel(request.getChannel());
        String title = request.getTitle().trim();
        String content = request.getContent().trim();
        String penName = request.getPenName().trim();
        List<String> tags = normalizeTags(request.getTags());

        bannedWordFilterService.check(penName, "updateArticle.penName");
        bannedWordFilterService.check(title, "updateArticle.title");
        bannedWordFilterService.check(stripInlineImages(content), "updateArticle.content");

        article.setPenName(penName);
        article.setChannel(channel);
        article.setTitle(title);
        article.setContent(content);
        article.setTags(joinTags(tags));
        // 正文内嵌图片已变化，图片列表以正文提取结果为准（不再限制张数）；
        // 兼容旧客户端：显式传入 images 且正文中没有图片标记时才沿用传入值
        // 记录旧图片列，用于判断删光图片后是否需要显式置 NULL
        String oldImagesJson = article.getImages();
        List<String> inlineImages = extractInlineImageUrls(content);
        String newImagesJson = null;
        if (!inlineImages.isEmpty()) {
            newImagesJson = joinImages(inlineImages);
            article.setImages(newImagesJson);
        } else if (request.getImages() != null) {
            newImagesJson = joinImages(request.getImages());
            article.setImages(newImagesJson);
        }
        // isPublic 为 null 时保持原状态不变
        if (request.getIsPublic() != null) {
            article.setIsPublic(Boolean.TRUE.equals(request.getIsPublic()) ? 1 : 0);
        }
        // 审核流：被屏蔽打回的文章修改后重新提审，需管理员审核通过后才会重新展示
        if (FireflyArticle.REVIEW_RETURNED.equals(article.getReviewStatus())) {
            article.setReviewStatus(FireflyArticle.REVIEW_PENDING);
            article.setReviewReason(null);
            log.info("打回文章重新提审: articleId={}, userId={}", id, userId);
        }
        fireflyArticleMapper.updateById(article);
        // MyBatis-Plus 默认 NOT_NULL 策略会跳过 null 字段，
        // 用户删光正文图片时需显式 set images=NULL，否则旧图片列表残留
        if (request.getImages() != null && newImagesJson == null
                && org.springframework.util.StringUtils.hasText(oldImagesJson)) {
            fireflyArticleMapper.update(null, new LambdaUpdateWrapper<FireflyArticle>()
                    .eq(FireflyArticle::getId, id)
                    .set(FireflyArticle::getImages, null));
        }
        log.info("萤火文章编辑成功: articleId={}, userId={}", id, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteArticle(Long userId, Long id) {
        FireflyArticle article = getOwnedArticleOrThrow(userId, id);
        // 先级联清理评论/点赞/收藏，再删除文章本体（同事务）
        articleInteractionService.deleteAllByArticle(article.getId());
        fireflyArticleMapper.deleteById(article.getId());
        log.info("萤火文章删除成功: articleId={}, userId={}", id, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeVisibility(Long userId, Long id, boolean isPublic) {
        FireflyArticle article = getOwnedArticleOrThrow(userId, id);
        article.setIsPublic(isPublic ? 1 : 0);
        fireflyArticleMapper.updateById(article);
        log.info("萤火文章公开状态切换: articleId={}, userId={}, isPublic={}", id, userId, isPublic);
    }

    @Override
    public PageResult<ArticleListVO> getMyArticles(Long userId, String channel, Boolean isPublic, int page, int size) {
        LambdaQueryWrapper<FireflyArticle> wrapper = new LambdaQueryWrapper<FireflyArticle>()
                .eq(FireflyArticle::getUserId, userId)
                .eq(isValidChannel(channel), FireflyArticle::getChannel, channel)
                .eq(isPublic != null, FireflyArticle::getIsPublic, Boolean.TRUE.equals(isPublic) ? 1 : 0)
                .orderByDesc(FireflyArticle::getCreatedAt);

        IPage<FireflyArticle> result = fireflyArticleMapper.selectPage(new Page<>(page, size), wrapper);
        List<FireflyArticle> records = result.getRecords();
        List<ArticleListVO> list = records.stream()
                .map(this::toListVO)
                .collect(Collectors.toList());
        fillAuthorAvatars(list, records);
        return new PageResult<>(list, result.getTotal(), page, size);
    }

    // ============================ 私有方法 ============================

    /**
     * 查询文章并校验归属权：不存在抛404，非作者抛403
     */
    private FireflyArticle getOwnedArticleOrThrow(Long userId, Long id) {
        FireflyArticle article = fireflyArticleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        if (!article.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能操作自己的文章");
        }
        return article;
    }

    /**
     * 校验频道参数，非法值抛参数错误
     */
    private String normalizeChannel(String channel) {
        if (!isValidChannel(channel)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "频道参数非法");
        }
        return channel;
    }

    private boolean isValidChannel(String channel) {
        return StringUtils.hasText(channel) && ALLOWED_CHANNELS.contains(channel);
    }

    /**
     * 标签清洗：去空白、去重（保序）、限制数量与单标签长度
     */
    private List<String> normalizeTags(List<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String raw : rawTags) {
            if (raw == null) {
                continue;
            }
            String tag = raw.trim();
            if (!StringUtils.hasText(tag)) {
                continue;
            }
            // 标签内部不允许出现分隔符逗号
            tag = tag.replace(",", " ").trim();
            if (tag.length() > MAX_TAG_LENGTH) {
                tag = tag.substring(0, MAX_TAG_LENGTH);
            }
            distinct.add(tag);
            if (distinct.size() >= MAX_TAG_COUNT) {
                break;
            }
        }
        return new ArrayList<>(distinct);
    }

    /**
     * 标签列表转逗号分隔字符串
     */
    private String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        return String.join(",", tags);
    }

    /**
     * 逗号分隔字符串转标签列表
     */
    private List<String> parseTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return Collections.emptyList();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    /**
     * 图片列表转 JSON 字符串（不限制张数；空列表返回 null）
     */
    private String joinImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(images);
        } catch (Exception e) {
            log.error("图片列表序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从正文中按出现顺序提取内嵌图片 URL（去重，不限制数量）
     */
    private List<String> extractInlineImageUrls(String content) {
        if (!StringUtils.hasText(content)) {
            return Collections.emptyList();
        }
        List<String> urls = new java.util.ArrayList<>();
        java.util.regex.Matcher matcher = INLINE_IMAGE_PATTERN.matcher(content);
        while (matcher.find()) {
            String url = matcher.group(1);
            if (!urls.contains(url)) {
                urls.add(url);
            }
        }
        return urls;
    }

    /**
     * 剔除正文中的内嵌图片标记，只保留文字（用于违禁词检测与摘要生成）
     */
    private String stripInlineImages(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        return INLINE_IMAGE_PATTERN.matcher(content).replaceAll("");
    }

    /**
     * JSON 字符串转图片列表
     */
    private List<String> parseImages(String imagesJson) {
        if (!StringUtils.hasText(imagesJson)) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(imagesJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("图片列表反序列化失败: json={}, error={}", imagesJson, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 截取前 N 张图片（列表页预览）
     */
    private List<String> limitImages(List<String> images, int limit) {
        if (images == null || images.isEmpty()) {
            return Collections.emptyList();
        }
        return images.size() > limit ? images.subList(0, limit) : images;
    }

    /**
     * 截取正文摘要：先剔除内嵌图片标记，再将换行折叠为空格，取前 120 字
     */
    private String buildSummary(String content) {
        if (content == null) {
            return "";
        }
        String textOnly = stripInlineImages(content);
        String folded = textOnly.replaceAll("\\s+", " ").trim();
        return folded.length() > SUMMARY_MAX_LENGTH ? folded.substring(0, SUMMARY_MAX_LENGTH) + "..." : folded;
    }

    private ArticleListVO toListVO(FireflyArticle article) {
        ArticleListVO vo = new ArticleListVO();
        vo.setId(article.getId());
        vo.setAuthorId(article.getUserId());
        vo.setPenName(article.getPenName());
        vo.setChannel(article.getChannel());
        vo.setTitle(article.getTitle());
        vo.setSummary(buildSummary(article.getContent()));
        vo.setTags(parseTags(article.getTags()));
        // 列表页只展示前3张图片
        vo.setImages(limitImages(parseImages(article.getImages()), LIST_IMAGE_PREVIEW_COUNT));
        vo.setIsPublic(article.getIsPublic() != null && article.getIsPublic() == 1);
        vo.setViewCount(article.getViewCount());
        vo.setLikeCount(article.getLikeCount());
        vo.setCommentCount(article.getCommentCount());
        vo.setFavoriteCount(article.getFavoriteCount());
        vo.setReviewStatus(article.getReviewStatus());
        vo.setReviewReason(article.getReviewReason());
        vo.setCreatedAt(article.getCreatedAt());
        return vo;
    }

    private ArticleVO toVO(FireflyArticle article, boolean isOwner) {
        ArticleVO vo = new ArticleVO();
        vo.setId(article.getId());
        vo.setUserId(article.getUserId());
        vo.setPenName(article.getPenName());
        vo.setChannel(article.getChannel());
        vo.setTitle(article.getTitle());
        vo.setContent(article.getContent());
        vo.setTags(parseTags(article.getTags()));
        vo.setImages(parseImages(article.getImages()));
        vo.setIsPublic(article.getIsPublic() != null && article.getIsPublic() == 1);
        vo.setViewCount(article.getViewCount());
        vo.setLikeCount(article.getLikeCount());
        vo.setCommentCount(article.getCommentCount());
        vo.setFavoriteCount(article.getFavoriteCount());
        vo.setReviewStatus(article.getReviewStatus());
        vo.setReviewReason(article.getReviewReason());
        vo.setCreatedAt(article.getCreatedAt());
        vo.setUpdatedAt(article.getUpdatedAt());
        vo.setOwner(isOwner);
        vo.setFollowing(false);
        // 作者头像：交流会匿名社交属性，文章作者固定展示系统默认头像（前端按 userId 取默认头像）
        // 不暴露作者自定义头像，避免作者身份跨文章关联泄露
        // vo.setAuthorAvatar(...) 不设置，保持 null
        return vo;
    }

    /**
     * 批量填充文章列表作者头像（避免 N+1）。
     * 交流会匿名社交：作者头像统一不暴露真实头像，authorAvatar 保持 null，
     * 前端按 userId 取系统默认头像。方法保留作占位，便于未来政策调整。
     */
    private void fillAuthorAvatars(List<ArticleListVO> list, List<FireflyArticle> articles) {
        // 匿名社交策略：列表作者头像统一为 null（前端显示系统默认头像）
        if (list == null) return;
        list.forEach(vo -> vo.setAuthorAvatar(null));
    }

    // ============================ 管理员审核 ============================

    @Override
    public PageResult<ArticleListVO> getAdminArticles(String reviewStatus, String keyword, int page, int size) {
        LambdaQueryWrapper<FireflyArticle> wrapper = new LambdaQueryWrapper<FireflyArticle>()
                .eq(StringUtils.hasText(reviewStatus), FireflyArticle::getReviewStatus, reviewStatus)
                // 管理员检索：笔名或标题模糊匹配
                .and(StringUtils.hasText(keyword), w -> w
                        .like(FireflyArticle::getPenName, keyword.trim())
                        .or()
                        .like(FireflyArticle::getTitle, keyword.trim()))
                .orderByDesc(FireflyArticle::getUpdatedAt);

        IPage<FireflyArticle> result = fireflyArticleMapper.selectPage(new Page<>(page, size), wrapper);
        List<FireflyArticle> records = result.getRecords();
        List<ArticleListVO> list = records.stream()
                // 管理员列表额外返回正文全文，便于内容审核
                .map(a -> {
                    ArticleListVO vo = toListVO(a);
                    vo.setContent(a.getContent());
                    return vo;
                })
                .collect(Collectors.toList());
        fillAuthorAvatars(list, records);
        return new PageResult<>(list, result.getTotal(), page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewArticle(Long adminId, Long articleId, String action, String reason) {
        if (!"approve".equals(action) && !"return".equals(action)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "审核动作只能为 approve 或 return");
        }
        FireflyArticle article = fireflyArticleMapper.selectById(articleId);
        if (article == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        if ("approve".equals(action)) {
            article.setReviewStatus(FireflyArticle.REVIEW_APPROVED);
            article.setReviewReason(null);
            fireflyArticleMapper.updateById(article);
            log.info("管理员审核通过文章: articleId={}, adminId={}", articleId, adminId);
            return;
        }

        // 屏蔽打回：广场不可见，作者可见并可修改后重新提审
        String trimmedReason = StringUtils.hasText(reason) ? reason.trim() : "内容不合适";
        article.setReviewStatus(FireflyArticle.REVIEW_RETURNED);
        article.setReviewReason(trimmedReason);
        fireflyArticleMapper.updateById(article);

        // 通知作者：点击通知可跳转文章详情（作者视角可见打回原因）
        notificationService.sendNotification(article.getUserId(), "system",
                "文章被屏蔽打回",
                String.format("你的文章《%s》因内容不合适被屏蔽打回。原因：%s。请修改后重新提交，审核通过后才会重新展示。",
                        article.getTitle(), trimmedReason),
                "article", articleId);
        log.info("管理员屏蔽打回文章: articleId={}, adminId={}, reason={}", articleId, adminId, trimmedReason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminDeleteArticle(Long adminId, Long articleId) {
        FireflyArticle article = fireflyArticleMapper.selectById(articleId);
        if (article == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        // 级联清理评论/点赞/收藏/评论点赞，再删除文章本体（同事务）
        articleInteractionService.deleteAllByArticle(article.getId());
        fireflyArticleMapper.deleteById(article.getId());
        log.info("管理员删除违规文章: articleId={}, adminId={}, title={}", articleId, adminId, article.getTitle());
    }
}
