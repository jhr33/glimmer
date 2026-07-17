# ============================================================
# glimmer 后端 Dockerfile（多阶段构建）
# 阶段1：Maven 构建可执行 jar
# 阶段2：JRE 运行时镜像
# ============================================================

# ---------- 阶段1：builder ----------
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 先复制 pom.xml，利用 Docker 缓存加速依赖下载
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源码并打包（跳过测试）
COPY src ./src
RUN mvn clean package -DskipTests -B

# ---------- 阶段2：runtime ----------
FROM eclipse-temurin:17-jre AS runtime

# 时区设置
ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# 安装 curl 用于健康检查（actuator health endpoint）
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 创建上传目录并设置权限
RUN mkdir -p /data/glimmer/uploads && chmod 755 /data/glimmer/uploads

WORKDIR /app

# 从 builder 阶段复制构建产物
COPY --from=builder /build/target/*.jar app.jar

# 上传目录挂载点
VOLUME /data/glimmer/uploads

# 暴露后端端口
EXPOSE 8080

# 健康检查（依赖 spring-boot-starter-actuator）
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
