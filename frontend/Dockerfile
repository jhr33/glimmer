# ============================================================
# glimmer 前端 Dockerfile（多阶段构建）
# 阶段1：Node 构建静态资源
# 阶段2：Nginx 提供静态服务 + 反向代理
# ============================================================

# ---------- 阶段1：builder ----------
FROM node:20-alpine AS builder

WORKDIR /build

# 先复制 package*.json，利用 Docker 缓存加速依赖安装
COPY package*.json ./
RUN npm ci

# 复制源码并构建
COPY . .
RUN npm run build

# ---------- 阶段2：runtime ----------
FROM nginx:alpine AS runtime

# 时区设置
ENV TZ=Asia/Shanghai

# 复制构建产物到 Nginx 静态目录
COPY --from=builder /build/dist /usr/share/nginx/html

# 复制自定义 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 暴露 80 端口
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
