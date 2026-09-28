#!/bin/bash
# ============================================================
# glimmer 服务器一次性初始化脚本（在服务器上以 ubuntu 用户执行）
#
# 作用：
#   1. 把当前生产 jar 固化为固定文件名 app.jar（后续部署只换这个文件）
#   2. 安装 glimmer-backend systemd 服务（开机自启 + 崩溃自动拉起）
#   3. 停掉旧的 nohup 裸跑进程，切换为 systemd 托管（约 10 秒中断）
#   4. 健康检查，失败会提示看日志
#   5. 清理无限增长的 1.4GB 旧日志（之后日志进 journald，由系统轮转）
#
# 使用前提：
#   - 首次执行前请先备份数据库！
#     mysqldump -u root -p glimmer > ~/glimmer-db-backup-$(date +%Y%m%d).sql
#   - 脚本与 glimmer-backend.service 放在同一目录（如 ~/deploy/）
# ============================================================
set -euo pipefail

APP_DIR=/home/ubuntu/app
SERVICE=glimmer-backend
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "==> [1/6] 固化当前生产 jar 为 app.jar"
cd "$APP_DIR"
if [ ! -f app.jar ]; then
  cp -v backend-1.0.0.jar app.jar
else
  echo "    app.jar 已存在，跳过"
fi

echo "==> [2/6] 安装 systemd 单元文件"
sudo cp "$SCRIPT_DIR/glimmer-backend.service" /etc/systemd/system/glimmer-backend.service
sudo systemctl daemon-reload

echo "==> [3/6] 停掉旧的 nohup 裸跑进程（服务约中断 10 秒）"
# 旧启动命令为：java -jar backend-1.0.0.jar --spring.profiles.active=prod
pkill -f 'java -jar backend-1.0.0.jar' || true
sleep 3
# 兜底：确认 8080 已释放
for _ in $(seq 1 5); do
  if ! ss -ltn | grep -q ':8080'; then break; fi
  sleep 1
done

echo "==> [4/6] 以 systemd 方式启动并设置开机自启"
sudo systemctl enable "$SERVICE"
sudo systemctl restart "$SERVICE"

echo "==> [5/6] 健康检查（最多等待 90 秒）"
HEALTHY=false
for i in $(seq 1 30); do
  if [ "$(systemctl is-active "$SERVICE")" = "active" ] \
     && wget -qO- http://127.0.0.1:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
    HEALTHY=true
    echo "    后端启动成功（第 $((i*3)) 秒）"
    break
  fi
  sleep 3
done

if [ "$HEALTHY" = false ]; then
  echo ""
  echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
  echo "!! 服务未通过健康检查，请立即查看启动日志：               !!"
  echo "!!   sudo journalctl -u glimmer-backend -n 100 --no-pager !!"
  echo "!! 旧进程已停。如日志无法快速修复，可临时回退到旧方式：   !!"
  echo "!!   sudo systemctl stop glimmer-backend                  !!"
  echo "!!   cd /home/ubuntu/app && nohup java -jar backend-1.0.0.jar --spring.profiles.active=prod > app.log 2>&1 &"
  echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
  exit 1
fi

echo "==> [6/6] 清理旧日志和垃圾文件（systemd 之后日志自动轮转）"
# 旧 nohup 进程已退出，app.log 不再被占用，直接截断为 0（保留文件，不删目录里其他东西）
: > "$APP_DIR/app.log"
# 历史误操作产生的垃圾文件（内容是一条拼错的 nohup 命令）
rm -f "$APP_DIR/1"
echo "    app.log 已清空（释放约 1.4GB 磁盘）"

echo ""
echo "============================================================"
echo " 初始化完成！常用运维命令："
echo "   查看实时日志：sudo journalctl -u glimmer-backend -f"
echo "   重启后端：    sudo systemctl restart glimmer-backend"
echo "   查看状态：    sudo systemctl status glimmer-backend"
echo "============================================================"
