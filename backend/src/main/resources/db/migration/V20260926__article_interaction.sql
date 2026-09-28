-- ============================================================
-- 交流会互动（评论/点赞/收藏）+ 匿名昵称 24 小时轮换
-- 1. article_comment 评论：展示名快照，历史评论不随改名变化
-- 2. article_like 点赞：唯一键保证一人一篇只赞一次，可取消
-- 3. collect_folder 自定义收藏文件夹；article_collect 收藏关系
--    唯一键保证一篇文章对同一用户只收藏一次（可移动文件夹/取消）
-- 4. firefly_article 增加冗余计数；user 增加匿名昵称过期时间
-- 说明：
--   - 本项目实际由后端 DatabaseMigration 启动时幂等建表/加列，
--     本文件仅为结构存档；但也保证可重复手动执行（幂等）。
--   - MySQL（含8.0）不支持 ADD COLUMN IF NOT EXISTS（MariaDB 语法），
--     这里通过 information_schema 判断 + PREPARE 动态SQL实现幂等加列。
-- ============================================================

CREATE TABLE IF NOT EXISTS article_comment (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    article_id     BIGINT       NOT NULL COMMENT '文章ID',
    user_id        BIGINT       NOT NULL COMMENT '评论用户ID',
    parent_id      BIGINT       DEFAULT NULL COMMENT '被回复评论ID（一级评论为NULL）',
    reply_to_name  VARCHAR(50)  DEFAULT NULL COMMENT '被回复者展示名快照',
    display_name   VARCHAR(50)  NOT NULL COMMENT '评论时展示名（昵称或匿名昵称快照）',
    is_anonymous   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否匿名: 1是/0否',
    content        VARCHAR(1000) NOT NULL COMMENT '评论内容',
    status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE正常/DELETED已删除',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_article (article_id, status, created_at),
    KEY idx_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交流会文章评论表';

CREATE TABLE IF NOT EXISTS article_like (
    id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '点赞ID',
    article_id  BIGINT   NOT NULL COMMENT '文章ID',
    user_id     BIGINT   NOT NULL COMMENT '点赞用户ID',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_article_user (article_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交流会文章点赞表';

CREATE TABLE IF NOT EXISTS collect_folder (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '文件夹ID',
    user_id     BIGINT      NOT NULL COMMENT '所属用户ID',
    name        VARCHAR(30) NOT NULL COMMENT '文件夹名称（用户自定义）',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章收藏文件夹';

CREATE TABLE IF NOT EXISTS article_collect (
    id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
    article_id  BIGINT   NOT NULL COMMENT '文章ID',
    user_id     BIGINT   NOT NULL COMMENT '收藏用户ID',
    folder_id   BIGINT   NOT NULL COMMENT '所在收藏文件夹ID',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_article_user (article_id, user_id),
    KEY idx_folder (folder_id, created_at),
    KEY idx_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章收藏关系表';

-- ------------------------------------------------------------
-- 幂等加列：列已存在则跳过，不存在才执行 ALTER TABLE
-- （每个列独立判断，避免一条失败导致全部不执行）
-- ------------------------------------------------------------

-- firefly_article.like_count
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'firefly_article' AND COLUMN_NAME = 'like_count'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE firefly_article ADD COLUMN like_count INT NOT NULL DEFAULT 0 COMMENT ''点赞数'' AFTER view_count',
    'SELECT ''firefly_article.like_count 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- firefly_article.comment_count
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'firefly_article' AND COLUMN_NAME = 'comment_count'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE firefly_article ADD COLUMN comment_count INT NOT NULL DEFAULT 0 COMMENT ''评论数'' AFTER like_count',
    'SELECT ''firefly_article.comment_count 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- firefly_article.favorite_count
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'firefly_article' AND COLUMN_NAME = 'favorite_count'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE firefly_article ADD COLUMN favorite_count INT NOT NULL DEFAULT 0 COMMENT ''收藏数'' AFTER comment_count',
    'SELECT ''firefly_article.favorite_count 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- user.anonymous_name_expires_at：NULL 表示下次使用匿名身份时重新生成
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'anonymous_name_expires_at'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE user ADD COLUMN anonymous_name_expires_at DATETIME DEFAULT NULL COMMENT ''匿名昵称过期时间，24小时轮换''',
    'SELECT ''user.anonymous_name_expires_at 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
