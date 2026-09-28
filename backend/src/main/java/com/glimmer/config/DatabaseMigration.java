package com.glimmer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 轻量级数据库 Schema 迁移
 * 仅在需要时执行 ALTER TABLE，幂等安全
 */
@Slf4j
@Component
public class DatabaseMigration implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        // 萤火交流会：文章表（首次启动自动创建，已存在则跳过）
        createFireflyArticleTableIfNotExists();
        // 交流会互动：评论 / 点赞 / 收藏文件夹 / 文章收藏
        createArticleInteractionTablesIfNotExists();
        // 交流会：文章冗余互动计数（与明细表原子同步，避免列表 COUNT 全表扫描）
        addColumnIfNotExists("firefly_article", "like_count",
                "INT NOT NULL DEFAULT 0 COMMENT '点赞数'",
                "firefly_article.like_count");
        addColumnIfNotExists("firefly_article", "comment_count",
                "INT NOT NULL DEFAULT 0 COMMENT '评论数'",
                "firefly_article.comment_count");
        addColumnIfNotExists("firefly_article", "favorite_count",
                "INT NOT NULL DEFAULT 0 COMMENT '收藏数'",
                "firefly_article.favorite_count");
        // 匿名昵称统一：24小时过期时间（NULL 表示需要重新生成）
        addColumnIfNotExists("user", "anonymous_name_expires_at",
                "DATETIME DEFAULT NULL COMMENT '匿名昵称过期时间，24小时轮换'",
                "user.anonymous_name_expires_at");
        addColumnIfNotExists(
                "campfire_member", "anonymous_name",
                "VARCHAR(100) DEFAULT NULL COMMENT '篝火内身份名称'",
                "campfire_member.anonymous_name"
        );
        // AI 对话重构：配额、摘要、会话类型
        addColumnIfNotExists("ai_conversation", "conversation_type",
                "VARCHAR(10) DEFAULT 'paid' NOT NULL COMMENT '类型: free免费/paid付费'",
                "ai_conversation.conversation_type");
        addColumnIfNotExists("ai_conversation", "quota_used",
                "INT DEFAULT 0 NOT NULL COMMENT '已用轮次'",
                "ai_conversation.quota_used");
        addColumnIfNotExists("ai_conversation", "quota_limit",
                "INT DEFAULT 10 NOT NULL COMMENT '轮次上限'",
                "ai_conversation.quota_limit");
        addColumnIfNotExists("ai_conversation", "quota_reset_date",
                "DATE DEFAULT NULL COMMENT '配额重置日期(仅free)'",
                "ai_conversation.quota_reset_date");
        addColumnIfNotExists("ai_conversation", "summary",
                "TEXT DEFAULT NULL COMMENT '会话摘要'",
                "ai_conversation.summary");
        addColumnIfNotExists("ai_conversation", "title",
                "VARCHAR(50) DEFAULT NULL COMMENT '会话标题(首条消息摘要)'",
                "ai_conversation.title");
        // 用户表：AI 记忆关键信息
        addColumnIfNotExists("user", "ai_context",
                "JSON DEFAULT NULL COMMENT 'AI记忆关键信息JSON'",
                "user.ai_context");
        // 交流会文章审核流：默认通过，被举报进入待审，管理员可屏蔽打回
        addColumnIfNotExists("firefly_article", "review_status",
                "VARCHAR(20) NOT NULL DEFAULT 'APPROVED' COMMENT '审核状态: APPROVED通过/PENDING_REVIEW待审/RETURNED屏蔽打回'",
                "firefly_article.review_status");
        addColumnIfNotExists("firefly_article", "review_reason",
                "VARCHAR(255) DEFAULT NULL COMMENT '打回/屏蔽原因'",
                "firefly_article.review_reason");
        // 评论点赞：评论表冗余计数 + 点赞明细表
        addColumnIfNotExists("article_comment", "like_count",
                "INT NOT NULL DEFAULT 0 COMMENT '评论点赞数'",
                "article_comment.like_count");
        // 交流会：评论点赞明细 / 作者关注（同一账户可关注多个笔名）
        createArticleReviewTablesIfNotExists();
        // 手机号登录：user.phone（唯一索引，允许 NULL，MySQL 唯一索引中多个 NULL 不冲突）
        addColumnIfNotExists("user", "phone",
                "VARCHAR(11) DEFAULT NULL COMMENT '手机号'",
                "user.phone");
        addIndexIfNotExists("user", "uk_phone",
                "ALTER TABLE user ADD UNIQUE KEY uk_phone (phone)",
                "user.uk_phone");
        // 文章图片：JSON 数组字符串，最多 9 张
        addColumnIfNotExists("firefly_article", "images",
                "VARCHAR(2000) DEFAULT NULL COMMENT '图片URL列表(JSON数组，最多9张)'",
                "firefly_article.images");
        // 篝火图片消息：msg_type 区分 text/image，image_url 存 OSS 地址
        addColumnIfNotExists("campfire_message", "msg_type",
                "VARCHAR(16) NOT NULL DEFAULT 'text' COMMENT '消息类型: text文本/image图片'",
                "campfire_message.msg_type");
        addColumnIfNotExists("campfire_message", "image_url",
                "VARCHAR(500) DEFAULT NULL COMMENT '图片消息URL'",
                "campfire_message.image_url");
        // 用户头像：user.avatar_url 自定义头像（NULL=系统默认头像）；篝火消息头像快照（匿名时 NULL）
        addColumnIfNotExists("user", "avatar_url",
                "VARCHAR(500) DEFAULT NULL COMMENT '自定义头像URL(NULL=系统默认头像)'",
                "user.avatar_url");
        addColumnIfNotExists("campfire_message", "avatar_url",
                "VARCHAR(500) DEFAULT NULL COMMENT '消息头像快照(匿名时NULL显示默认头像)'",
                "campfire_message.avatar_url");
        // 小游戏板块：游戏成绩表（首期贪吃蛇，game_type 预留其他游戏扩展）
        createGameScoreTableIfNotExists();
        // 小游戏板块：玩家手动匿名代号表（用户×游戏唯一，仅对应游戏排行榜使用）
        createGamePlayerAliasTableIfNotExists();
    }

    /**
     * 小游戏成绩表：幂等建表
     * display_name 存昵称快照，排行榜无需关联 user 表；
     * is_anonymous/anonymous_name 支持玩家自选匿名上榜（匿名昵称快照仅供排行榜展示）
     */
    private void createGameScoreTableIfNotExists() {
        createTableIfNotExists("game_score",
                "CREATE TABLE game_score (" +
                "  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '成绩ID'," +
                "  user_id BIGINT NOT NULL COMMENT '玩家用户ID'," +
                "  game_type VARCHAR(32) NOT NULL DEFAULT 'snake' COMMENT '游戏类型: snake贪吃蛇（预留扩展）'," +
                "  display_name VARCHAR(50) NOT NULL COMMENT '成绩产生时的昵称快照'," +
                "  is_anonymous TINYINT(1) NOT NULL DEFAULT 0 COMMENT '排行榜是否匿名: 0否 1是'," +
                "  anonymous_name VARCHAR(50) DEFAULT NULL COMMENT '匿名昵称快照（仅排行榜展示）'," +
                "  score INT NOT NULL DEFAULT 0 COMMENT '本局得分'," +
                "  level INT NOT NULL DEFAULT 1 COMMENT '本局达到的难度等级'," +
                "  duration_seconds INT NOT NULL DEFAULT 0 COMMENT '本局时长（秒）'," +
                "  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                "  PRIMARY KEY (id)," +
                "  KEY idx_game_type_score (game_type, score, created_at)," +
                "  KEY idx_user (user_id, game_type, created_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小游戏成绩表'");
        // 老库补列（新库建表时已包含，检测到列存在会自动跳过）
        addColumnIfNotExists("game_score", "is_anonymous",
                "TINYINT(1) NOT NULL DEFAULT 0 COMMENT '排行榜是否匿名: 0否 1是'",
                "game_score.is_anonymous");
        addColumnIfNotExists("game_score", "anonymous_name",
                "VARCHAR(50) DEFAULT NULL COMMENT '匿名昵称快照（仅排行榜展示）'",
                "game_score.anonymous_name");
    }

    /**
     * 小游戏玩家匿名代号表：幂等建表
     * 与 user.anonymous_name（篝火/漂流瓶统一匿名昵称）相互独立，
     * 一个用户在每个游戏下可设置一个手动代号，提交成绩时快照到 game_score
     */
    private void createGamePlayerAliasTableIfNotExists() {
        createTableIfNotExists("game_player_alias",
                "CREATE TABLE game_player_alias (" +
                "  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID'," +
                "  user_id BIGINT NOT NULL COMMENT '玩家用户ID'," +
                "  game_type VARCHAR(32) NOT NULL COMMENT '游戏类型: snake贪吃蛇（预留扩展）'," +
                "  alias VARCHAR(20) NOT NULL COMMENT '玩家手动输入的匿名代号（仅本游戏排行榜使用）'," +
                "  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                "  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                "  PRIMARY KEY (id)," +
                "  UNIQUE KEY uk_user_game (user_id, game_type)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小游戏玩家匿名代号表'");
    }

    /**
     * 评论点赞表 / 作者关注表：幂等建表
     */
    private void createArticleReviewTablesIfNotExists() {
        createTableIfNotExists("comment_like",
                "CREATE TABLE comment_like (" +
                "  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '点赞ID'," +
                "  comment_id BIGINT NOT NULL COMMENT '评论ID'," +
                "  user_id BIGINT NOT NULL COMMENT '点赞用户ID'," +
                "  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间'," +
                "  PRIMARY KEY (id)," +
                "  UNIQUE KEY uk_comment_user (comment_id, user_id)," +
                "  KEY idx_user (user_id, created_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交流会评论点赞表'");

        createTableIfNotExists("user_follow",
                "CREATE TABLE user_follow (" +
                "  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '关注ID'," +
                "  follower_id BIGINT NOT NULL COMMENT '关注者用户ID'," +
                "  followee_id BIGINT NOT NULL COMMENT '被关注账户用户ID'," +
                "  pen_name VARCHAR(50) NOT NULL COMMENT '关注的作者笔名（随文章署名）'," +
                "  remark VARCHAR(50) DEFAULT NULL COMMENT '自定义备注（区分不同账户同一笔名）'," +
                "  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间'," +
                "  PRIMARY KEY (id)," +
                "  UNIQUE KEY uk_follower_author_pen (follower_id, followee_id, pen_name)," +
                "  KEY idx_followee (followee_id, created_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交流会作者关注表'");
    }

    /**
     * 萤火交流会文章表：幂等建表
     * pen_name（笔名）跟随文章存储，不写入 user 账户字段
     */
    private void createFireflyArticleTableIfNotExists() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'firefly_article'",
                    Integer.class
            );
            if (count != null && count > 0) {
                log.info("[DB迁移] 表已存在，跳过: firefly_article");
                return;
            }
            jdbcTemplate.execute(
                    "CREATE TABLE firefly_article (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '文章ID'," +
                    "  user_id BIGINT NOT NULL COMMENT '作者用户ID'," +
                    "  pen_name VARCHAR(50) NOT NULL COMMENT '笔名（随文章内容署名，非账户字段）'," +
                    "  channel VARCHAR(16) NOT NULL COMMENT '频道: chat杂谈 / tech技术笔记'," +
                    "  title VARCHAR(100) NOT NULL COMMENT '标题'," +
                    "  content MEDIUMTEXT NOT NULL COMMENT '正文'," +
                    "  tags VARCHAR(255) DEFAULT NULL COMMENT '标签，英文逗号分隔'," +
                    "  is_public TINYINT NOT NULL DEFAULT 1 COMMENT '是否公开: 1公开/0私密'," +
                    "  view_count INT NOT NULL DEFAULT 0 COMMENT '浏览量'," +
                    "  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                    "  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                    "  PRIMARY KEY (id)," +
                    "  KEY idx_channel_public (channel, is_public, created_at)," +
                    "  KEY idx_user (user_id, created_at)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='萤火交流会文章表'"
            );
            log.info("[DB迁移] 成功创建表: firefly_article");
        } catch (Exception e) {
            log.error("[DB迁移] 创建表失败: firefly_article, error={}", e.getMessage());
        }
    }

    /**
     * 交流会互动表：评论 / 点赞 / 收藏文件夹 / 文章收藏（幂等建表）
     */
    private void createArticleInteractionTablesIfNotExists() {
        createTableIfNotExists("article_comment",
                "CREATE TABLE article_comment (" +
                "  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '评论ID'," +
                "  article_id BIGINT NOT NULL COMMENT '文章ID'," +
                "  user_id BIGINT NOT NULL COMMENT '评论用户ID'," +
                "  parent_id BIGINT DEFAULT NULL COMMENT '被回复评论ID（一级评论为NULL）'," +
                "  reply_to_name VARCHAR(50) DEFAULT NULL COMMENT '被回复者展示名快照'," +
                "  display_name VARCHAR(50) NOT NULL COMMENT '评论时展示名（昵称或匿名昵称快照）'," +
                "  is_anonymous TINYINT NOT NULL DEFAULT 0 COMMENT '是否匿名: 1是/0否'," +
                "  content VARCHAR(1000) NOT NULL COMMENT '评论内容'," +
                "  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE正常/DELETED已删除'," +
                "  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                "  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                "  PRIMARY KEY (id)," +
                "  KEY idx_article (article_id, status, created_at)," +
                "  KEY idx_user (user_id, created_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交流会文章评论表'");

        createTableIfNotExists("article_like",
                "CREATE TABLE article_like (" +
                "  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '点赞ID'," +
                "  article_id BIGINT NOT NULL COMMENT '文章ID'," +
                "  user_id BIGINT NOT NULL COMMENT '点赞用户ID'," +
                "  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                "  PRIMARY KEY (id)," +
                "  UNIQUE KEY uk_article_user (article_id, user_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交流会文章点赞表'");

        createTableIfNotExists("collect_folder",
                "CREATE TABLE collect_folder (" +
                "  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件夹ID'," +
                "  user_id BIGINT NOT NULL COMMENT '所属用户ID'," +
                "  name VARCHAR(30) NOT NULL COMMENT '文件夹名称（用户自定义）'," +
                "  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                "  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                "  PRIMARY KEY (id)," +
                "  KEY idx_user (user_id, created_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章收藏文件夹'");

        createTableIfNotExists("article_collect",
                "CREATE TABLE article_collect (" +
                "  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '收藏ID'," +
                "  article_id BIGINT NOT NULL COMMENT '文章ID'," +
                "  user_id BIGINT NOT NULL COMMENT '收藏用户ID'," +
                "  folder_id BIGINT NOT NULL COMMENT '所在收藏文件夹ID'," +
                "  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                "  PRIMARY KEY (id)," +
                "  UNIQUE KEY uk_article_user (article_id, user_id)," +
                "  KEY idx_folder (folder_id, created_at)," +
                "  KEY idx_user (user_id, created_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章收藏关系表'");
    }

    /**
     * 通用幂等建表：information_schema 判断，已存在则跳过
     */
    private void createTableIfNotExists(String tableName, String ddl) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                    Integer.class, tableName
            );
            if (count != null && count > 0) {
                log.info("[DB迁移] 表已存在，跳过: {}", tableName);
                return;
            }
            jdbcTemplate.execute(ddl);
            log.info("[DB迁移] 成功创建表: {}", tableName);
        } catch (Exception e) {
            log.error("[DB迁移] 创建表失败: {}, error={}", tableName, e.getMessage());
        }
    }

    /**
     * 通用幂等加索引：information_schema.STATISTICS 判断，已存在则跳过
     */
    private void addIndexIfNotExists(String table, String indexName, String ddl, String label) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                    Integer.class, table, indexName
            );
            if (count != null && count > 0) {
                log.info("[DB迁移] 索引已存在，跳过: {}", label);
                return;
            }
            jdbcTemplate.execute(ddl);
            log.info("[DB迁移] 成功添加索引: {}", label);
        } catch (Exception e) {
            log.error("[DB迁移] 添加索引失败: {}, error={}", label, e.getMessage());
        }
    }

    private void addColumnIfNotExists(String table, String column, String definition, String label) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Integer.class, table, column
            );
            if (count != null && count > 0) {
                log.info("[DB迁移] 列已存在，跳过: {}", label);
                return;
            }
            String sql = String.format("ALTER TABLE %s ADD COLUMN %s %s", table, column, definition);
            jdbcTemplate.execute(sql);
            log.info("[DB迁移] 成功添加列: {}", label);
        } catch (Exception e) {
            log.error("[DB迁移] 添加列失败: {}, error={}", label, e.getMessage());
        }
    }
}
