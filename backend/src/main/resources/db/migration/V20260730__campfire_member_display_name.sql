-- 篝火成员表增加 anonymous_name 字段
-- 存储用户进入篝火时选择的身份名称（匿名模式随机生成，昵称模式按 userId 查询填入）
ALTER TABLE campfire_member ADD COLUMN anonymous_name VARCHAR(100) DEFAULT NULL COMMENT '篝火内身份名称（昵称或随机匿名）';
