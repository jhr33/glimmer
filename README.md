# glimmer（萤光）

> 一个以"温暖、治愈、匿名陪伴"为核心主题的社交网站。在快节奏的现代生活中，提供一个无需暴露真实身份即可倾诉、交流、获得情感反馈的空间。

## 项目简介

glimmer 通过漂流瓶、信件、篝火群聊、AI 树洞对话、萤火花园，萤火交流会等多个互动场景，构建一种"非加好友、非真实身份、轻量互动"的社交体验。包含完整的匿名昵称、感谢激励、签到代币、举报治理、申诉复议等机制。

项目最初只是想做一个简单的漂流瓶模式，或许在现实中，有一些情绪我们无法坦然的和身边人述说，向真实建立连接的人寻求安慰倾诉心声往往因为顾虑后续伴随着潜在的恐惧和担忧。在完全匿名的环境中，那些无法袒露的脆弱，迷茫，痛苦，都可以短暂的被寄托。但仅仅是漂流瓶的交流过于短暂和随机，因而引入了信件系统，参考我曾经使用过的一个类似app，添加了一来一回的书信模式与篝火聊天室，增强了社交连接与及时社交的属性。收到漂流瓶回复的用户可以选择是否给对方写信，只有收到信才可以写回信，双方在是否继续交流上用于完全的自主权。我希望大家玩的开心而不只是作为一个伤心地，篝火因汇聚而燃起，随离去而消散，只看因缘际会。考虑到用户活跃缺失问题，为情绪兜底引入了ai对话，24小时及时响应全天待命。为了鼓励互动，引入了代币奖励机制与花园养成系统，虽然作者只有艺术细菌暂时ui都很丑陋><，不过还是可以用来开ai使用额度的嘛，正在思考引入桌宠会不会更好玩一点，敬请期待。为了维护社区环境，违禁词与举报审核也是有必要被引入的，顺带一提恶意举报和恶意发言一样会被封号哦，平台暂无客服，被增加工作量我就这样全部封掉。因为作者正在学习中需要记录学习笔记，所以新增功能交流随记，可以拿来写日记哦（正经人谁写日记（bushi))，欢迎来讲冷笑话，虽然作者是美术白痴但是说不准是个冷笑话天才。以及考录到随记性质最好便捷使用，决定发布apk版本，但如果考虑推广，安全性也要纳入考虑，因而加入了手机号注册绑定和图片认证防脚本，以及为随记图片发布功能引入了oss，支出upup><

微弱的萤火也会散发光芒，在照亮他人的同时，也被他人照亮。(其实作者在思考冷笑话但是暂无灵感，所以煽情一下好了)
网站网址：https://glimmer.wang
## 核心特性

- **匿名社交**：漂流瓶、信件、篝火场景使用系统生成的匿名昵称，保护真实身份
- **AI 树洞对话**：基于 DeepSeek API 的流式（SSE）逐字返回，带上下文摘要与 token 计费
- **篝火实时群聊**：STOMP over WebSocket，握手期 JWT 鉴权，消息 24 小自动清理
- **萤火花园养成**：累计萤火值决定花园亮度（0-5 级）与萤火虫粒子数量，双轨经济模型
- **举报治理闭环**：举报 → 分组审核 → 处罚（禁言）→ 申诉复议，状态机驱动
- **并发安全**：Redis 分布式锁（Lua CAS 释放）+ @Version 乐观锁双层保障代币/萤火扣减
- **随记交流论坛**：可选匿名与否的文章发布，收藏，点赞，记录，无论是随心一记还是学习笔记，自定义标签发布管理，公开与否取决个人
- - **短信验证与oss图片存储引入**：加入手机号注册绑定与验证码图片认证，防止恶意脚本攻击，引入oss图片存储允许用户发布图片
## 技术栈

| 层级   | 技术                                                                                 |
|------|------------------------------------------------------------------------------------|
| 后端   | Spring Boot 3.2.5 + MyBatis-Plus 3.5.7 + Spring Security + JWT + MySQL 8.0 + Redis |
| 前端   | Vue 3.4 + Vite 5 + Element Plus + Pinia + Vue Router + @stomp/stompjs              |
| 实时通信 | Spring WebSocket（STOMP，篝火群聊）+ SseEmitter（AI 流式对话）                                  |
| AI   | DeepSeek API（deepseek-v4-flash，流式 + include_usage 计费）                              |


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

> 说明：默认密码通过 BCrypt 加密存储于 `sql/init.sql`。若登录失败（哈希不匹配），可启动后端后通过注册接口创建用户，再直接在数据库中将其 `role` 字段更新为 `admin`。

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


