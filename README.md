# glimmer（萤光）

> 一个以"温暖、治愈、匿名陪伴"为核心主题的社交网站。在快节奏的现代生活中，提供一个无需暴露真实身份即可倾诉、交流、获得情感反馈的空间。

## 项目简介

glimmer 通过漂流瓶、信件、篝火群聊、AI 树洞对话、萤火花园等多个互动场景，构建一种"非加好友、非真实身份、轻量互动"的社交体验。包含完整的匿名昵称、感谢激励、签到代币、举报治理、申诉复议等机制。
临时访问链接：https://district-motivated-cookie-worlds.trycloudflare.com
## 核心特性

- **匿名社交**：漂流瓶、信件、篝火场景使用系统生成的匿名昵称，保护真实身份
- **AI 树洞对话**：基于 DeepSeek API 的流式（SSE）逐字返回，带上下文摘要与 token 计费
- **篝火实时群聊**：STOMP over WebSocket，握手期 JWT 鉴权，消息 24 小自动清理
- **萤火花园养成**：累计萤火值决定花园亮度（0-5 级）与萤火虫粒子数量，双轨经济模型
- **举报治理闭环**：举报 → 分组审核 → 处罚（禁言）→ 申诉复议，状态机驱动
- **并发安全**：Redis 分布式锁（Lua CAS 释放）+ @Version 乐观锁双层保障代币/萤火扣减

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 后端 | Spring Boot 3.2.5 + MyBatis-Plus 3.5.7 + Spring Security + JWT + MySQL 8.0 + Redis |
| 前端 | Vue 3.4 + Vite 5 + Element Plus + Pinia + Vue Router + @stomp/stompjs |
| 实时通信 | Spring WebSocket（STOMP，篝火群聊）+ SseEmitter（AI 流式对话） |
| AI | DeepSeek API（deepseek-v4-flash，流式 + include_usage 计费） |
| 部署 | Docker + Docker Compose + Nginx |

## 目录结构

```
glimmer/
├── backend/                # 后端 Spring Boot 项目
│   ├── src/main/java/com/glimmer/
│   │   ├── controller/     # 控制器（api / admin / ws）
│   │   ├── service/        # 服务层（接口 + impl）
│   │   │   └── impl/       # 13 个 ServiceImpl（AI/篝火/漂流瓶/信件/举报...）
│   │   ├── entity/         # 实体类
│   │   ├── mapper/         # MyBatis-Plus Mapper
│   │   ├── config/         # 配置类（security / websocket / mybatis / ai）
│   │   ├── task/           # 定时任务（处罚过期 / 篝火消息清理）
│   │   └── common/         # 通用工具（DistributedLock / TokenBalanceHelper / JwtUtils）
│   ├── src/test/           # 单元测试（Mockito，不依赖数据库）
│   ├── src/main/resources/application.yml
│   ├── Dockerfile
│   └── pom.xml
├── frontend/               # 前端 Vue 3 项目
│   ├── src/
│   │   ├── api/            # 接口封装（12 个模块）
│   │   ├── views/          # 页面（ai / campfire / garden / driftBottle / letter / admin ...）
│   │   ├── router/         # 路由
│   │   ├── stores/         # Pinia 状态
│   │   ├── components/     # 通用组件
│   │   └── utils/          # 工具（request / stomp / websocket）
│   ├── Dockerfile
│   ├── nginx.conf
│   └── package.json
├── sql/
│   └── init.sql            # 数据库初始化脚本（18 张表 + 默认数据，可重复执行）
├── docker-compose.yml      # 容器编排（mysql + backend + frontend）
├── .env.example            # 环境变量示例
└── 开发文档.md              # 详细设计文档      
```

## 开发环境启动

### 前置要求
- JDK 17+
- Maven 3.9+
- Node.js 20+
- MySQL 8.0+、Redis 7+

### 1. 初始化数据库
```bash
# 使用 sql/init.sql 初始化数据库（含 18 张表 + 默认数据）
mysql -u root -p < sql/init.sql
```

### 2. 启动 Redis
```bash
redis-server
```

### 3. 启动后端
```bash
cd backend
# 配置数据库连接：编辑 application.yml 或通过环境变量注入
# 必需环境变量：DB_PASSWORD、JWT_SECRET、DEEPSEEK_API_KEY
mvn spring-boot:run
# 后端启动在 http://localhost:8080
# Swagger 文档：http://localhost:8080/swagger-ui.html
```

### 4. 启动前端
```bash
cd frontend
npm install
npm run dev
# 前端启动在 http://localhost:5173（已配置 Vite 代理转发到后端 8080）
```

## 生产环境部署（Docker Compose）

### 1. 配置环境变量
```bash
cp .env.example .env
# 编辑 .env，填入真实的数据库密码、JWT 密钥、DeepSeek API Key
```

### 2. 一键启动
```bash
docker-compose up -d --build
```

启动后：
- 前端：http://localhost
- 后端 API：http://localhost:8080
- MySQL：localhost:3306

### 3. 查看日志 / 停止
```bash
# 查看日志
docker-compose logs -f backend
# 停止
docker-compose down
# 停止并清除数据卷（慎用，会删除数据库数据）
docker-compose down -v
```

## 默认体验账号

| 用户名 | 密码 | 角色 |
| -- | --- | --- |
| test | 123456 | user |

> 说明：默认管理员密码通过 BCrypt 加密存储于 `sql/init.sql`。若登录失败（哈希不匹配），可启动后端后通过注册接口创建用户，再直接在数据库中将其 `role` 字段更新为 `admin`。

## 关键配置说明

### 环境变量（.env）

| 变量 | 说明 |
| --- | --- |
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 |
| `MYSQL_USER` | 业务数据库用户名（默认 glimmer） |
| `MYSQL_PASSWORD` | 业务数据库密码 |
| `JWT_SECRET` | JWT 签名密钥（生产环境必须修改，至少 32 字符） |
| `DEEPSEEK_API_KEY` | DeepSeek API Key（AI 对话功能所需） |
| `UPLOAD_PATH` | 上传文件存储路径（默认 /data/glimmer/uploads） |

### Docker 服务端口

| 服务 | 端口 |
| --- | --- |
| MySQL | 3306 |
| 后端 | 8080 |
| 前端（Nginx） | 80 |

### Nginx 代理规则

- `/api/` → 后端 8080（REST API）
- `/ws-campfire` → 后端 8080（WebSocket 篝火聊天）
- `/uploads/` → 后端 8080（上传文件）
- 其他路径 → 前端 SPA（`try_files ... /index.html`）

## 测试

后端单元测试基于 Mockito，不依赖数据库，聚焦核心业务规则：

```bash
cd backend
mvn test
```

测试覆盖：
- **AuthServiceImplTest**：注册成功（生成匿名昵称）、注册失败（用户名已存在）、登录成功、登录失败（密码错误/用户不存在/已封禁）
- **TokenServiceImplTest**：签到成功（前7天 +3）、重复签到失败、第8天签到（+1）
- **DriftBottleServiceImplTest**：扔瓶子成功、捡瓶子不会捡到自己的、重复捡同一瓶子被拒绝、回复瓶子成功（每人一次）


