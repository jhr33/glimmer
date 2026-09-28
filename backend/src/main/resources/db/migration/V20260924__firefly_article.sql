-- ============================================================
-- 萤火交流会：文章表
-- 1. pen_name（笔名）跟随文章存储，不写入 user 账户字段
-- 2. channel 频道：chat 杂谈 / tech 技术笔记
-- 3. tags 多个标签以英文逗号分隔，配合 MySQL FIND_IN_SET 筛选
-- 4. is_public=0 的文章仅作者本人可见（我的随记）
-- ============================================================
CREATE TABLE IF NOT EXISTS firefly_article (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '文章ID',
    user_id     BIGINT       NOT NULL COMMENT '作者用户ID',
    pen_name    VARCHAR(50)  NOT NULL COMMENT '笔名（随文章内容署名，非账户字段）',
    channel     VARCHAR(16)  NOT NULL COMMENT '频道: chat杂谈 / tech技术笔记',
    title       VARCHAR(100) NOT NULL COMMENT '标题',
    content     MEDIUMTEXT   NOT NULL COMMENT '正文',
    tags        VARCHAR(255) DEFAULT NULL COMMENT '标签，英文逗号分隔，如: Java,面试,随笔',
    is_public   TINYINT      NOT NULL DEFAULT 1 COMMENT '是否公开: 1公开(广场可见) / 0私密(仅自己可见)',
    view_count  INT          NOT NULL DEFAULT 0 COMMENT '浏览量',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    -- 广场列表：按频道 + 公开状态筛选，按发布时间倒序
    KEY idx_channel_public (channel, is_public, created_at),
    -- 我的随记：按作者查自己的全部文章
    KEY idx_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='萤火交流会文章表';
