#!/bin/bash
# ============================================================
# glimmer 生产数据库热备份脚本（在服务器执行）
# 特点：
#   - 密码从 application-prod.yml 运行时提取，不写死、不回显
#   - --single-transaction：InnoDB 一致性快照，不锁表、不影响线上
#   - 自动校验备份完整性（CREATE TABLE 数量 + 转储结束标记）
# 用法：bash backup-db.sh
# ============================================================
set -euo pipefail

CONF=/home/ubuntu/app/application-prod.yml
BACKUP=~/glimmer-db-backup-$(date +%Y%m%d-%H%M%S).sql

# 提取第一个 password: 行（datasource 块在 redis 块之前），去掉行内注释和首尾空白/回车
DB_PASS=$(awk -F'password:[[:space:]]*' '
    /password:/ {
        split($2, a, "#")
        gsub(/[[:space:]\r]+$/, "", a[1])
        print a[1]
        exit
    }' "$CONF")

if [ -z "$DB_PASS" ]; then
  echo "BACKUP_FAILED：未能从 $CONF 提取数据库密码"
  exit 1
fi

# 2>/dev/null 仅屏蔽命令行密码的 warning
mysqldump -u root -p"$DB_PASS" \
  --single-transaction --routines --triggers \
  --default-character-set=utf8mb4 \
  glimmer > "$BACKUP" 2>/dev/null

# 完整性校验：文件非空 + 含建表语句 + 以转储结束标记收尾
SIZE=$(stat -c%s "$BACKUP")
TABLES=$(grep -c 'CREATE TABLE' "$BACKUP" || true)
if [ "$SIZE" -lt 10000 ] || [ "$TABLES" -lt 5 ] || ! tail -1 "$BACKUP" | grep -q 'Dump completed'; then
  echo "BACKUP_FAILED：备份文件不完整（size=$SIZE, tables=$TABLES），请勿继续部署"
  rm -f "$BACKUP"
  exit 1
fi

echo "BACKUP_OK"
echo "文件：$BACKUP"
ls -lh "$BACKUP"
echo "包含表数量：$TABLES"
