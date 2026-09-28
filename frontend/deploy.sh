#!/bin/bash
# glimmer 前端部署脚本

echo "📦 开始构建..."
npm run build

echo "🚀 删除服务器旧文件..."
ssh -i /d/.ssh/glimmer.pem ubuntu@43.129.213.98 "sudo rm -rf /var/www/glimmer/*"

echo "📤 上传新文件..."
scp -i /d/.ssh/glimmer.pem -r /d/ideaspace/glimmer/frontend/dist/* ubuntu@43.129.213.98:/var/www/glimmer/

echo "✅ 部署完成！"