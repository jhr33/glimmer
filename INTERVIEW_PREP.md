# Glimmer（萤火树洞）项目面试准备文档

> 面向：初级全栈开发二面（一面已问算法，本轮偏理论与项目深挖）
> 目标：从零到一讲清"这个项目怎么搭起来、核心难点怎么实现、常用技术怎么理解"

---

## 一、项目概述

**Glimmer（萤火树洞）** 是一个匿名情感陪伴社区 Web 应用，前后端分离架构。核心功能模块：

| 模块 | 说明 |
|------|------|
| 用户与认证 | 注册/登录/JWT 鉴权、匿名昵称、游客浏览模式 |
| 漂流瓶 | 扔瓶/捞瓶（公海私海）/回复/感谢/沉底，AI「回音」自动回复 |
| 篝火聊天室 | 实时群聊（WebSocket/STOMP）、引用回复、AI「回音」动态介入 |
| AI 树洞对话 | 与 AI「微光」流式对话、每日免费 token 额度 + 代币解锁 |
| 信件 | 私密长文书信、来源可追溯漂流瓶/回复 |
| 萤火花园 | 签到得萤火、种花/浇水/兑换（游戏化留存） |
| 代币系统 | 签到/任务产出代币，消耗于 AI 额度/对话开启 |
| 举报/处罚/申诉 | 用户举报 → 管理员审核 → 创建处罚 → 用户申诉 |
| 管理后台 | 举报/反馈/公告/用户管理 |

**技术定位**：Spring Boot 单体后端 + Vue 3 SPA 前端 + MySQL + Redis + DeepSeek AI。

---

## 二、技术栈总览

### 后端（backend/）
- **Spring Boot 3.2.5** / **Java 17**（parent POM 统一版本管理）
- **Spring Security**：认证鉴权、JWT 过滤器、接口白名单
- **Spring WebSocket（STOMP）**：篝火实时聊天
- **Spring Data Redis**：缓存、分布式锁
- **MyBatis-Plus 3.5.7**：ORM（基于 MyBatis 增强，CRUD/分页/条件构造器）
- **JJWT 0.12.5**：JWT 生成与解析（HS256）
- **Caffeine**：本地缓存（如封禁状态二级缓存）
- **Lombok**：样板代码消除（@Data/@Slf4j）
- **springdoc OpenAPI 3**：接口文档（Swagger UI）
- **spring-boot-devtools**：开发热重载
- **MySQL 8**（驱动 mysql-connector-j 8.0.33）

### 前端（frontend/）
- **Vue 3.4**（Composition API + `<script setup>`）
- **Vite 5**（构建/开发服务器/HMR）
- **Pinia 2**（状态管理，Vue 官方推荐）
- **Vue Router 4**（history 模式 + 路由守卫）
- **Element Plus 2.7**（UI 组件库）
- **Axios 1.7**（HTTP 客户端 + 拦截器）
- **@stomp/stompjs 7**（STOMP over WebSocket 客户端）

### 中间件/外部服务
- **MySQL**：主存储
- **Redis**：缓存、分布式锁、AI 对话上下文缓存
- **DeepSeek API**：大模型对话（同步 + 流式 SSE）

---

## 三、项目结构

### 后端目录（单模块，分层架构）
```
backend/src/main/java/com/glimmer/
├── GlimmerApplication.java          # 启动类（@SpringBootApplication）
├── common/                          # 通用基础设施
│   ├── enums/        (UserRole, UserStatus)
│   ├── exception/    (BusinessException, ErrorCode)        # 业务异常 + 错误码枚举
│   ├── response/     (Result, PageResult)                   # 统一响应/分页
│   └── util/         (JwtUtils, DistributedLock, CryptoUtil, RedisUtils, SecurityUtils, TokenBalanceHelper...)
├── config/                          # 配置类
│   ├── ai/           (DeepSeekProperties, EchoInitializer, RestTemplateConfig)
│   ├── exception/    (GlobalExceptionHandler)               # 全局异常处理
│   ├── jwt/          (JwtConfig, JwtProperties)             # JWT 配置化
│   ├── mybatis/      (MybatisPlusConfig, MybatisMetaObjectHandler)  # 自动填充
│   ├── security/     (SecurityConfig, JwtAuthenticationFilter)     # 安全配置
│   ├── websocket/    (WebSocketConfig, JwtHandshakeInterceptor)     # STOMP 配置
│   ├── CryptoConfig.java, EncryptedFieldTypeHandler.java           # 加密 TypeHandler
│   └── RedisConfig.java, DatabaseMigration.java
├── controller/
│   ├── admin/        (Admin*Controller)                     # 管理后台接口（需 ADMIN 角色）
│   ├── api/          (AuthController, AiController, CampfireController, DriftBottleController,
│   │                  EchoController, LetterController, UserController, ReportController...)
│   └── ws/           (ChatController)                       # @MessageMapping 篝火消息入口
├── entity/                          # 数据库实体（与表一一对应）
├── mapper/                          # MyBatis-Plus Mapper 接口（BaseMapper）
├── service/
│   ├── ai/           (DeepSeekClient + 请求/响应 DTO)       # AI 客户端封装
│   ├── dto/          (各种 VO/Request/Response)              # 数据传输对象
│   ├── impl/         (各 ServiceImpl)                        # 业务实现
│   └── (各 Service 接口)
├── task/             (BottleEchoTask, CampfireEchoTask, CampfireScheduler, PunishmentExpireTask)  # 定时任务
└── util/             (DataEncryptionMigrationRunner)        # 加密迁移
```

**分层职责**：`Controller`（HTTP/WS 入口，参数校验）→ `Service`（业务逻辑、事务）→ `Mapper`（数据访问）→ `Entity`（表映射）。`DTO/VO` 隔离内外数据结构。

### 前端目录
```
frontend/src/
├── api/              (auth.js, driftBottle.js, letter.js...)  # 接口封装（调用 request.js）
├── assets/           (styles/main.css, 图片)
├── components/       (ReportDialog.vue 等通用组件)
├── router/           (index.js)                                # 路由 + 全局守卫
├── stores/           (user.js)                                 # Pinia 用户状态
├── utils/            (request.js)                              # axios 实例 + 拦截器
├── views/            (auth/, driftBottle/, campfire/, ai/, letter/, garden/, admin/...)
├── App.vue           # 根组件（导航 + 布局）
└── main.js           # 应用入口（挂载 Pinia/Router/ElementPlus）
```

---

## 四、后端基础搭建

### 4.1 工程初始化
- `pom.xml` 继承 `spring-boot-starter-parent:3.2.5`，统一 Spring 全家桶版本。
- 关键依赖：`starter-web`（MVC）、`starter-security`、`starter-websocket`、`starter-data-redis`、`starter-validation`（参数校验）、`starter-webflux`（注：保留但实际流式用 HttpURLConnection）、`mybatis-plus-spring-boot3-starter`、`jjwt`、`caffeine`、`lombok`、`springdoc`。
- 打包：`spring-boot-maven-plugin`，`mvn package` 产出可执行 jar。

### 4.2 配置管理（application.yml）
配置分两类：
- **环境无关**：端口、时区（`Asia/Shanghai`）、Jackson 日期格式、MyBatis-Plus、Redis 连接池、devtools。
- **环境相关（环境变量注入）**：
  - `DB_PASSWORD`：MySQL 密码
  - `REDIS_HOST/PORT/PASSWORD/DB`
  - `JWT_SECRET`：JWT 密钥（带默认值兜底，生产必须覆盖）
  - `DEEPSEEK_API_KEY`：AI Key
  - `DATA_ENCRYPTION_KEY`：数据加密密钥（32 字节 base64）

> 设计原则：敏感信息绝不硬编码，全部通过 `${VAR:default}` 占位符从环境变量读取。

### 4.3 数据访问层（MyBatis-Plus）
- 实体类用 `@TableName` 映射表，`@TableId(type = IdType.AUTO)` 主键自增。
- Mapper 继承 `BaseMapper<T>`，开箱即用 `insert/updateById/selectById/selectList` 等 CRUD。
- 复杂查询用 `LambdaQueryWrapper`（类型安全，避免硬编码字段名字符串）：
  ```java
  userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
  ```
- `MybatisMetaObjectHandler`：自动填充 createTime/updateTime。
- **TypeHandler 扩展**：`EncryptedFieldTypeHandler` 实现敏感字段（信件内容、AI 消息）的自动加解密。
- 实体开启 `@TableName(autoResultMap = true)` 以让 TypeHandler 在查询结果映射时生效。

### 4.4 统一响应（Result）
所有接口统一返回 `{ code, message, data }`：
```java
public class Result<T> {
    private int code;        // 200 成功
    private String message;
    private T data;
    // success()/error(ErrorCode)/error(code, message) 静态工厂
}
```
`@JsonInclude(NON_NULL)` 保证 null 字段不序列化，减小响应体积。

### 4.5 全局异常处理（GlobalExceptionHandler）
`@RestControllerAdvice` + `@ExceptionHandler` 统一兜底，避免异常栈直接暴露给前端：
- `BusinessException` → 业务错误码 + 消息
- `DuplicateKeyException` → 根据唯一约束名翻译（如 `uk_username` → "用户名已存在"）
- `MethodArgumentNotValidException` / `ConstraintViolationException` → 参数校验错误信息
- `Exception`（兜底）→ "服务器内部错误"，并 log.error 记录堆栈

> 好处：Controller/Service 只需 `throw new BusinessException(ErrorCode.XXX)`，无需 try-catch 包装。

### 4.6 业务异常与错误码
`ErrorCode` 枚举集中管理所有业务错误码（code + message），如 `USERNAME_OR_PASSWORD_ERROR(4001)`、`USER_BANNED(4015)`。一处定义、全局复用。

---

## 五、核心技术深度解析

### 5.1 JWT 认证 + Spring Security 鉴权

**整体方案**：无状态（STATELESS）+ JWT + 过滤器。

**配置（SecurityConfig）核心点**：
1. `csrf.disable()`：前后端分离用 JWT，不需要 CSRF Token。
2. `sessionCreationPolicy(STATELESS)`：不创建 HttpSession，每次请求靠 JWT 鉴权。
3. 接口分级放行：
   - 白名单：`/api/auth/**`、公告、公共只读 GET 接口（游客可浏览）、`/ws-campfire/**`、Swagger。
   - `DispatcherType.ASYNC.permitAll()`：**关键**——SSE 异步分派放行（详见 5.3）。
   - `/error` 放行：Tomcat 异常 forward 到 `/error`，不放行会报 "response already committed"。
   - `/api/admin/**` 需 `ROLE_ADMIN`。
   - 其余 `authenticated()`。
4. `addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`：在 Spring Security 默认认证过滤器前插入 JWT 过滤器。
5. `authenticationEntryPoint` / `accessDeniedHandler`：未登录返回 401 JSON、无权限返回 403 JSON（而非默认登录页）。
6. CORS：`allowedOriginPatterns("*")` + `allowCredentials(true)`，允许跨域携带凭证。
7. `BCryptPasswordEncoder` Bean：密码加密。

**JWT 过滤器（JwtAuthenticationFilter，继承 OncePerRequestFilter）流程**：
1. 从 `Authorization: Bearer {token}` 头解析 token。
2. `jwtUtils.isValid(token)` 校验签名 + 过期。
3. 解析出 `userId`、`role`。
4. **封禁检查**：`punishmentService.isUserPermanentlyBanned(userId)`，永久封禁直接返回 4015（区分永久封禁 vs 禁言）。
5. 构造 `UsernamePasswordAuthenticationToken`（authorities = `ROLE_角色`），写入 `SecurityContextHolder`。
6. `filterChain.doFilter()` 放行，后续 Controller 通过 `SecurityUtils.getCurrentUserId()` 获取当前用户。

**JWT 工具（JwtUtils）**：
- HS256 签名，密钥 ≥32 字节（不足则补齐），过期时间 24 小时。
- token 携带 `userId`、`username`、`role` 三个 claim。
- `parseToken` 失败返回 null（不抛异常，由调用方判断）。
- 密钥从 `JWT_SECRET` 环境变量读取，带默认值兜底（保证重启密钥一致，避免已签发 token 全部失效）。

> **面试要点**：为什么用 OncePerRequestFilter？保证每个请求只执行一次鉴权逻辑，避免内部 forward/include 重复触发。为什么 STATELESS？JWT 无状态，服务端不存 session，天然支持水平扩展。

### 5.2 WebSocket / STOMP 实时通信（篝火聊天）

**为什么用 STOMP 而非裸 WebSocket？**
裸 WebSocket 只能收发字节/文本，没有"消息路由/订阅"语义。STOMP 是 WebSocket 之上的子协议，提供 `SUBSCRIBE`/`SEND`/`MESSAGE` 帧格式，让客户端能按"主题"订阅消息，服务端按目的地路由广播，适合群聊/点对点场景。

**配置（WebSocketConfig）**：
- 端点：`/ws-campfire`，注册 `JwtHandshakeInterceptor`（握手时校验 JWT）。
- `setHandshakeHandler` 重写 `determineUser`：把握手时解析出的 userId 设为 `Principal`，后续消息能拿到发送者身份。
- `setAllowedOriginPatterns("*")`：允许跨域连接。
- 应用前缀：`/app`——客户端 `SEND` 到 `/app/xxx` 由 `@MessageMapping` 处理。
- 简单 Broker：`/topic`（广播）、`/queue`（点对点）。

**握手鉴权（JwtHandshakeInterceptor）**：WebSocket 握手是 HTTP 升级请求，无法走 Spring Security 过滤链，所以在拦截器里手动解析 query 参数/header 里的 JWT，校验通过才允许升级，并把 userId 存入 attributes。

**消息流程**：
1. 前端用 `@stomp/stompjs` 建立连接，订阅 `/topic/campfire.{id}`。
2. 发消息：`stompClient.publish({ destination: '/app/campfire.message', body: JSON })`。
3. 后端 `@MessageMapping("campfire.message")` 接收 → 存库 → `messagingTemplate.convertAndSend("/topic/campfire." + id, messageVO)` 广播给所有订阅者。
4. 所有在线成员实时收到消息。

### 5.3 AI 流式对话（SSE + DeepSeek）

**为什么 AI 回复用 SSE 而不是 WebSocket？**
AI 回复是"服务端单向推送"场景（用户问一句，AI 边生成边推 token）。SSE（Server-Sent Events）基于 HTTP，单向、轻量、浏览器原生支持、自动重连，最适合这种"一对一流式输出"。WebSocket 双向但更重，适合篝火那种双向实时聊天。

**后端实现**：
- Controller 返回 `SseEmitter`（Spring MVC 对 SSE 的抽象）。
- `SseEmitter` 超时设 2 分钟，`onTimeout/onError/onCompletion` 必须调 `complete()` 释放浏览器连接（浏览器对同域 HTTP 连接数有限制，约 6 个，不释放会耗尽）。
- 用**固定线程池（大小 4）**异步执行流式调用，避免 `new Thread()` 无限增长。
- `SecurityConfig` 必须 `dispatcherTypeMatchers(ASYNC).permitAll()`：**关键踩坑**——`OncePerRequestFilter` 默认不处理 ASYNC 分派，SSE 异步完成时 `SecurityContext` 为空 → `AccessDeniedException`，且异常处理器也跳过 ASYNC 分派 → 异常穿透到容器。放行 ASYNC 解决。
- `AiConversationServiceImpl.sendMessage/unlockQuota` **不加 `@Transactional`**：避免长 DeepSeek 调用（可达 2 分钟）长期持有数据库连接，耗尽 HikariCP 连接池（max 10）。

**DeepSeekClient 流式调用**：
- 用 `HttpURLConnection`（非 RestTemplate，因为 RestTemplate 不支持流式逐行读取 SSE）。
- 请求带 `stream: true` + `stream_options.include_usage: true`（让最后一个 chunk 携带 token 用量，用于计费）。
- `readTimeout = 120000ms`（2 分钟），防止 API 卡住时线程永久阻塞。
- 逐行读 `data:` 行，解析 `choices[0].delta.content`，回调 `onDelta` 推给前端。
- **注意**：不能在 `finish_reason` 出现时提前 break——`usage` 在 `finish_reason` 之后的最后一个 chunk 返回，提前 break 会丢失 token 计费数据。
- token 用量可能为 null，直接拆箱会 NPE，需判空默认 0。

**前端流式请求**：
- 用原生 `fetch` + `ReadableStream` 读取响应体（非 axios，因为 axios 不支持流式读取）。
- `AbortController` 支持取消，组件卸载时 `abort()` 释放浏览器连接。
- `reader.releaseLock()` 放 finally 块。
- 防并发：`if (sending.value) return`，避免重复请求耗尽连接。

**计费方式**：累加 DeepSeek 返回的 `usage.totalTokens`（真实消耗），而非"每条 +1"。

### 5.4 Redis 分布式锁（代币扣减并发控制）

**问题**：代币扣减是典型的 read-modify-write，并发下会超扣（两个请求同时读到余额 10，各扣 5，都判断成功，实际扣到 -0 但写回都是 5，丢失更新）。

**方案（DistributedLock）**：
1. **加锁**：`SET key token NX EX ttl`（原子操作）。
   - `NX`：key 不存在才设置（保证互斥）。
   - `EX ttl`：设置过期（防持锁进程崩溃死锁）。
   - `token`：UUID（标识锁的持有者，释放时校验）。
   - 不用 `set + expire` 两步——两步之间崩溃会死锁。
2. **释放**：Lua 脚本 `if get(key)==token then del(key) end`（CAS 校验）。
   - 为什么用 Lua？保证"判断 + 删除"原子，避免误删别人的锁（A 持锁过期后 B 拿到锁，A 此时执行 del 会删掉 B 的锁）。
3. **自旋等待**：获取失败时 `Thread.sleep(50)` 重试，直到 `waitTime` 超时。
4. **降级**：Redis 不可用或竞争超时 → 降级直跑，由业务层 `@Version` 乐观锁兜底正确性。**可用性优先**。
5. **释放时机**：action 执行完立即释放（非延迟到事务提交后）。
   - **踩坑**：曾尝试延迟到 `afterCompletion` 串行化事务，但 `afterCompletion` 在 DB 连接释放前执行，持锁线程的 Redis 操作 + 等待者的自旋都占用 DB 连接，小连接池（max 10）下耗尽连接池全局超时。立即释放使锁仅持有毫秒级临界区。

**最终一致性保障**：分布式锁是性能优化（减少并发冲突），`@Version` 乐观锁是正确性兜底（即便锁失效，版本号不匹配会更新失败）。

### 5.5 定时任务（task 包）

Spring `@Scheduled` 定时任务，统一放在 `CampfireScheduler` 等类，构造器注入依赖：
- **CampfireEchoTask**：篝火冷场救场——超过 15 分钟无发言时，AI「回音」提新话题。
- **BottleEchoTask**：漂流瓶 AI 回复——扫描未被回音回复且总回复数 < 2 的瓶子，自动回复。
- **PunishmentExpireTask**：处罚到期自动解除。

**AI 回应频率动态调整**（篝火成员数）：0-1 人 3 秒、2-3 人 10 秒、4-10 人 20 秒、>10 人 30 秒。

### 5.6 数据加密（AES-256-GCM）

**需求**：AI 消息内容、信件内容是敏感隐私数据，需应用层加密存储，即便数据库泄露也无法读取。

**方案**：
- `CryptoUtil`：AES-256-GCM 对称加密（提供机密性 + 完整性）。
- 密钥：`DATA_ENCRYPTION_KEY` 环境变量（32 字节 base64），绝不入库/入代码。
- 存储格式：`ENC:base64(IV||ciphertext||tag)`——`ENC:` 前缀标识已加密。
- `EncryptedFieldTypeHandler extends BaseTypeHandler<String>`：MyBatis TypeHandler，写入时加密、读取时解密，**业务代码无感**。
- 实体字段标注 `@TableField(typeHandler = EncryptedFieldTypeHandler.class)`，类标注 `@TableName(autoResultMap = true)`。
- **幂等性**：加密前判断是否已 `ENC:` 开头，已加密则原样返回（避免重复加密）。
- **向后兼容**：解密时若非 `ENC:` 格式，视为明文直接返回（兼容历史数据）。
- `DataEncryptionMigrationRunner`：迁移脚本，启动时加 `-Dglimmer.migrate-encrypt=true` 批量加密历史明文。

### 5.7 权限与封禁体系（Punishment）

**双方法设计**（易混淆，面试高频）：
- `isUserPermanentlyBanned(userId)`：仅查 `BAN` 类型处罚。用于**登录拦截**（永久封禁不能登录）。
- `isUserBanned(userId)`：查 `BAN + MUTE` 类型处罚。用于**写操作拦截**（禁言用户能登录但不能发言）。
- 两者独立 Redis 缓存（避免互相污染 TTL）。

**封禁分级**：
- 永久封禁（BAN）：无法登录（login 接口 + JwtAuthenticationFilter 双重拦截）。
- 禁言（MUTE_24H / MUTE_7D）：可登录，但 Service 层 `checkUserNotMuted` 拦截写操作。
- 处罚全部结束：login 时自动恢复 `user.status = active`。

**业务规则**：
- 举报审核通过 → 创建 punishment 记录（不直接改 user 表）→ 回填 report。
- 申诉通过 → 仅将对应 punishment 置 REVOKED，不影响其他处罚。
- 同一罚单申诉 ≤ 3 次；存在 pending 申诉不可重复提交；REVOKED/EXPIRED 罚单不可申诉。

### 5.8 游客模式

**后端**：SecurityConfig 放行公共 GET 接口（漂流瓶列表/详情、篝火列表/消息、花园、用户公开资料），所有写操作（POST）仍需登录。

**前端**：
- 路由守卫：除管理员路由外，所有页面允许游客进入浏览；管理员路由未登录跳登录页。
- 按钮级别：各页面用 `isLoggedIn` 禁用写操作按钮（投瓶/发言/感谢/举报等），点击提示"请先登录"。
- 401 拦截器：游客（无 token）静默拒绝，不跳登录页；已登录用户 token 失效才跳转。

---

## 六、前端基础搭建

### 6.1 工程初始化（Vite + Vue 3）
- `npm create vite@latest frontend -- --template vue` 生成脚手架。
- `package.json` `type: "module"`（ESM）。
- `vite.config.js`：
  - `@` 别名指向 `./src`。
  - `server.proxy`：`/api` → `http://localhost:8080`，`/ws-campfire` → `ws://localhost:8080`，`ws: true` 启用 WebSocket 代理。
  - 代理解决开发环境跨域（前端 5173，后端 8080），生产用 nginx 反代。

### 6.2 应用入口（main.js）
```js
const app = createApp(App)
app.use(createPinia())      // 状态管理
app.use(router)             // 路由
app.use(ElementPlus)        // UI 库
app.mount('#app')
```
Element Plus 全量引入（简单；可按需引入优化体积）。

### 6.3 状态管理（Pinia，stores/user.js）
- Composition API 风格 `defineStore('user', () => {...})`。
- token 存 `sessionStorage`（标签页关闭即失效，比 localStorage 更安全；不存 localStorage 避免持久化风险）。
- `isLoggedIn` / `isAdmin` 用 `computed` 派生。
- `login/register/logout/fetchUserInfo` 封装异步操作并同步本地存储。

> **面试点**：Pinia vs Vuex？Pinia 是 Vue 官方新一代推荐，去掉了 Vuex 的 mutations（直接改 state），TS 支持更好，模块化更轻（无需 modules 嵌套），体积更小。

### 6.4 路由与守卫（router/index.js）
- `createWebHistory()`（history 模式，URL 无 #）。
- 路由懒加载：`() => import('@/views/...')`，按需加载减小首屏体积。
- 路由 meta：`requiresAuth`、`requiresAdmin`、`public`、`title`。
- 全局前置守卫 `beforeEach`：
  - `public` 路由直接放行。
  - `requiresAuth === false`（登录/注册页）：已登录用户跳首页。
  - `requiresAdmin`：未登录跳登录页（带 redirect query），已登录非管理员提示无权限。
  - 其他：游客可浏览（写操作按钮禁用兜底）。

### 6.5 HTTP 封装（utils/request.js）
- `axios.create({ baseURL: '/api', timeout: 15000 })`。
- **请求拦截器**：从 sessionStorage 取 token，注入 `Authorization: Bearer {token}`。
- **响应拦截器**：
  - `code === 200` 返回 data。
  - `code === 401`：游客（无 token）静默拒绝；已登录清登录态跳登录页。
  - 其他业务错误：`ElMessage.error` 提示并 reject（透传 code 供调用方特殊处理）。
  - 网络错误：统一提示。

### 6.6 STOMP 客户端
- `@stomp/stompjs` 的 `Client`，注意 `brokerURL` 直连后端会绕过 Vite 代理，用 `webSocketFactory` + 相对路径 `/ws-campfire` 走 Vite 代理。
- 不用 SockJS（现代浏览器 "global is not defined" 兼容性问题），用原生 WebSocket。

---

## 七、核心业务流程（时序梳理）

### 7.1 注册登录
```
注册：前端 POST /api/auth/register {username, password}
  → AuthServiceImpl.register
  → BCrypt 加密密码 → insert user（uk_username 唯一约束）
  → 根据生成的 id 生成匿名昵称 → updateById
  → 返回 userId

登录：前端 POST /api/auth/login {username, password}
  → 查用户 → BCrypt.matches 校验密码
  → 校验状态：banned 则查 punishment（永久封禁拒登；禁言允许；处罚结束自动恢复 active）
  → JwtUtils.generateToken（24h, HS256）
  → 返回 { token, user }
前端：userStore.login → setToken(sessionStorage) → setUserInfo → 跳转
```

### 7.2 篝火消息发送 + 广播 + AI 介入
```
用户发言：stompClient.publish → /app/campfire.message
  → @MessageMapping("campfire.message") ChatController
  → CampfireService.sendMessage
    → 存库（更新成员 lastActiveAt）
    → 构造 VO（设置 isFromBot = false）
    → convertAndSend("/topic/campfire.{id}", VO)  // 广播给所有订阅者
  → 异步判断：若是系统默认篝火(type=default)
    → 按成员数计算延迟（calcResponseDelay）
    → 延迟后调用 EchoService 生成回音回复
    → 存库（isFromBot=true）→ 广播
冷场救场：CampfireEchoTask 定时扫描
  → 最后发言 > 15 分钟 且 最后发言者非回音
  → 回音提新话题（topic 模式）→ 广播
```

### 7.3 AI 流式对话
```
前端 fetch POST /api/ai/conversations/{id}/messages/stream {content}
  → AiController.sendMessageStream 返回 SseEmitter（2min 超时）
  → 线程池提交任务 → AiConversationService.sendMessageStream
    → 检查额度（免费 token / 代币解锁额度）
    → buildContextWithSummary（Redis 缓存上下文，排除新插入的用户消息防重复）
    → DeepSeekClient.chatCompletionStream（HttpURLConnection 流式）
      → 逐 chunk onDelta → emitter.send(SseEmitter.event().data(delta))
      → 最后 chunk 携带 usage → 累计 totalTokens 计费
    → 写回 Redis 缓存
    → emitter.complete()
前端：ReadableStream 逐块读取 → 拼接渲染；AbortController 支持取消
```

### 7.4 举报 → 处罚 → 申诉
```
举报：POST /api/reports {targetType, targetId, reason}
  → 校验目标非 bot（AI 账号不可被举报）
  → uk_reporter_target 唯一约束（不可重复举报）
管理员审核通过：POST /api/admin/reports/{id}/review
  → 创建 Punishment 记录 → 回填 report.punishmentId
申诉：POST /api/feedback {punishmentId, content}
  → 校验：REVOKED/EXPIRED 不可申诉；pending 不可重复；≤ 3 次
管理员处理申诉：POST /api/admin/feedbacks/{id}/appeal
  → 通过则仅将对应 punishment 置 REVOKED（不影响其他处罚）
  → 通知用户（含被举报内容描述）
```

---

## 八、常见面试题与回答

### A. 通用理论题

**Q1：JWT 是什么？相比 Session 有什么优劣？**
> JWT（JSON Web Token）是一种无状态的令牌格式，由 Header、Payload、Signature 三部分组成，服务端用密钥签名，客户端每次请求携带，服务端验签即可识别身份。
> 相比 Session：JWT 无状态，服务端不存会话，天然支持水平扩展、跨域；缺点是无法主动失效（只能等过期或换密钥）、token 较大、续签麻烦。本项目用 JWT 因为前后端分离 + 无状态扩展需求，密钥配置在环境变量保证一致性。

**Q2：BCrypt 密码加密为什么不用 MD5/SHA？**
> MD5/SHA 是哈希速度快，容易被彩虹表/暴力破解。BCrypt 是自适应哈希，有 cost 因子（本项目 cost=10）可调节计算开销，且每次加密同密码结果不同（内置盐），抗彩虹表。Spring Security 的 `BCryptPasswordEncoder.matches(明文, 密文)` 校验。

**Q3：HTTP、WebSocket、SSE 三者区别？什么场景用哪个？**
> - HTTP：请求-响应，单向，适合 CRUD。
> - WebSocket：全双工长连接，双向实时，适合聊天室/协同编辑。
> - SSE（Server-Sent Events）：HTTP 长连接，单向（服务端→客户端），轻量，适合 AI 流式输出/消息推送。
> 本项目：篝火群聊用 WebSocket（双向实时），AI 对话用 SSE（单向流式），普通接口用 HTTP。

**Q4：Redis 分布式锁怎么实现？有哪些坑？**
> 用 `SET key value NX EX ttl` 原子加锁（NX 互斥、EX 防死锁），释放用 Lua 脚本 `if get==value then del`（CAS 防误删）。
> 坑：① 不能 set + expire 两步（中间崩溃死锁）；② 释放必须校验持有者（误删）；③ 锁过期但业务未完成（可用 Redisson 看门狗续期，或本项目用 @Version 乐观锁兜底）；④ 主从切换丢锁（AP 系统固有问题，强一致需 Redlock）。

**Q5：MySQL 事务隔离级别？项目用了哪个？**
> 四级：读未提交、读已提交、可重复读（MySQL InnoDB 默认）、串行化。InnoDB 通过 MVCC + 间隙锁解决幻读。项目用默认可重复读，代币扣减的并发靠 Redis 锁 + 乐观锁而非提高隔离级别（高隔离级别锁多性能差）。

**Q6：乐观锁 vs 悲观锁？**
> 乐观锁：假设冲突少，提交时校验版本（`@Version`，UPDATE ... WHERE version=?），失败重试，适合读多写少。悲观锁：先加锁再操作（`SELECT ... FOR UPDATE`），冲突多时用。项目代币扣减用乐观锁 + Redis 分布式锁（锁减少冲突次数，乐观锁兜底正确性）。

**Q7：Vue 3 响应式原理？**
> Vue 3 用 `Proxy` 代理对象（Vue 2 用 `Object.defineProperty` 只能劫持已声明属性）。`reactive` 包装对象为 Proxy，`ref` 包装值为 `{value}` 的响应式对象。依赖收集在 getter，触发更新在 setter。相比 Vue 2：能监听新增/删除属性、数组索引修改、性能更好（惰性响应，按需收集）。

**Q8：Pinia 和 Vuex 区别？**
> Pinia 去掉 mutations（可直接改 state）、TS 支持好、无 modules 嵌套（每个 store 独立）、体积小、Composition API 友好。Vue 3 官方推荐。

**Q9：跨域是什么？怎么解决？**
> 浏览器同源策略：协议/域名/端口任一不同即跨域，AJAX 默认受限。解决：① CORS（服务端设 `Access-Control-Allow-Origin` 等，项目后端配置）；② 开发代理（Vite proxy 把 `/api` 转发到后端，浏览器看到的同源）；③ Nginx 反代（生产）。项目开发用 Vite proxy + 后端 CORS，生产用 Nginx。

**Q10：Spring Bean 生命周期？**
> 实例化 → 属性注入 → Aware 回调 → BeanPostProcessor 前置 → 初始化（@PostConstruct/InitializingBean/init-method）→ BeanPostProcessor 后置 → 使用 → 销毁（@PreDestroy）。AOP 代理在 BeanPostProcessor 后置生成。

**Q11：MyBatis-Plus 相比 MyBatis 优势？**
> 内置 CRUD（BaseMapper）、条件构造器（LambdaQueryWrapper 类型安全）、分页插件、代码生成、自动填充、逻辑删除、乐观锁插件。减少样板代码。本项目实体配 `@TableName(autoResultMap=true)` 让 TypeHandler 生效。

### B. 项目深挖题

**Q12：你的 JWT 鉴权流程是怎样的？封禁用户怎么处理？**
> JwtAuthenticationFilter 继承 OncePerRequestFilter，从 Authorization 头解析 Bearer token，验签后取 userId/role，检查 `isUserPermanentlyBanned`（仅 BAN 类型），写入 SecurityContext。封禁分级：永久封禁拒登（login + 过滤器双重），禁言可登但 Service 层 `checkUserNotMuted` 拦写操作，处罚结束 login 时自动恢复 active。

**Q13：篝火消息怎么实时广播的？AI 怎么介入？**
> STOMP over WebSocket。前端订阅 `/topic/campfire.{id}`，发消息到 `/app/campfire.message`，后端 `@MessageMapping` 接收存库后 `convertAndSend` 广播。AI 回音两种模式：用户发言后按成员数动态延迟回应（reply，带上下文）；冷场超 15 分钟提新话题（topic）。成员数决定延迟（3s~30s）防刷屏。

**Q14：AI 流式回复怎么实现的？为什么用 SSE 而非 WebSocket？**
> 后端返回 SseEmitter，固定线程池异步调 DeepSeekClient（HttpURLConnection 流式，include_usage 取 token 用量），逐 chunk `emitter.send`。SSE 单向推送正合适，比 WebSocket 轻。关键坑：ASYNC 分派要 permitAll（否则 SecurityContext 空）；不能在 finish_reason 提前 break（会丢 usage）；不加 @Transactional（长调用耗尽连接池）；前端必须 complete/abort 释放连接。

**Q15：代币扣减并发怎么保证不超扣？**
> Redis 分布式锁（SET NX EX + Lua 释放）包住 read-modify-write 临界区，锁获取失败降级直跑。最终正确性由 `@Version` 乐观锁兜底（UPDATE WHERE version=? 失败则重试）。锁在 action 完成后立即释放（非事务提交后），避免小连接池耗尽。

**Q16：数据库敏感数据怎么加密的？**
> AES-256-GCM，密钥存环境变量。`EncryptedFieldTypeHandler` 实现 MyBatis TypeHandler 自动加解密，业务无感。存储格式 `ENC:base64(IV||ciphertext||tag)`，幂等（已加密不再加密）+ 向后兼容（明文当明文返回）。迁移用 `-Dglimmer.migrate-encrypt=true`。

**Q17：游客模式怎么做的？**
> 后端 SecurityConfig 放行公共 GET 接口，写操作需登录。前端路由守卫允许游客进浏览页，按钮用 `isLoggedIn` 禁用 + 点击提示登录，401 拦截器对游客静默拒绝不跳转。

**Q18：两个封禁方法 `isUserPermanentlyBanned` 和 `isUserBanned` 为什么要分开？**
> `isUserPermanentlyBanned`（仅 BAN）用于登录拦截——禁言用户必须能登录，否则体验差；`isUserBanned`（BAN+MUTE）用于写操作拦截——禁言用户不能发言但能浏览。两者缓存独立避免 TTL 污染。如果登录用 `isUserBanned` 会导致禁言用户无法登录，是 bug。

**Q19：定时任务做了什么？为什么 AI 回应要有延迟？**
> 篝火冷场救场、漂流瓶 AI 回复、处罚过期。延迟按成员数动态调整（3s~30s）：人少快速响应避免冷场，人多慢响应避免刷屏打扰真人交流。回音不回应自己消息防循环。

**Q20：你的项目遇到过哪些比较难的 bug？怎么排查的？**
（见第九节踩坑清单，选 2-3 个讲）

---

## 九、踩坑与复盘（精选，面试可讲故事）

| 问题 | 根因 | 解决 |
|------|------|------|
| AI 对话页面游客模式空白 | AiChatView.vue 未导入 `computed`，`isLoggedIn` 声明报 ReferenceError | 补 import |
| 登录后提示"登录已失效" | JWT 密钥未配置，每次重启密钥随机变化，旧 token 全失效 | application.yml 配默认密钥 + JwtUtils 空值兜底 |
| SSE 接口 403/异常穿透 | OncePerRequestFilter 默认跳过 ASYNC 分派，SecurityContext 空 | `dispatcherTypeMatchers(ASYNC).permitAll()` |
| 浏览器偶发 API 超时 | SSE 连接不释放，耗尽浏览器 6 连接限制 | SseEmitter 设超时 + onTimeout/onError complete；前端 AbortController + finally releaseLock |
| 代币扣减连接池耗尽 | 锁释放延迟到事务 afterCompletion，持锁期间占用 DB 连接 | 立即释放锁，@Version 乐观锁兜底 |
| 漂流瓶 AI 重复回复 | 只检查回音是否回复过，没查总回复数 | 同时校验 总回复数<2 且 回音未回复过 |
| 篝火 AI 不回应用户发言 | 只在冷场提新话题 | 区分 reply/topic 两种模式 |
| 篝火成员统计不准 | 未实现自动退出 | lastActiveAt，20 分钟无活动自动退出 |
| 机器人消息实时显示举报按钮 | 广播时未设 isFromBot，列表查询才补 | 广播前 `vo.setIsFromBot(true)` |
| 漂流瓶页面缩小按钮消失 | beach 固定 height:22% + ocean-scene overflow:hidden 裁切 | beach 改 height:auto 跟随内容 |

---

## 十、可能被追问的方向 & 准备清单

**架构/设计**
- 为什么单体而不是微服务？（项目规模、团队、复杂度，单体够用且运维简单）
- 如果用户量上去，瓶颈在哪？（DB 写、AI 调用、WebSocket 连接数；可分库分表、AI 限流、WebSocket 集群用 Redis pub/sub）
- 消息表数据量大怎么优化？（按时间分区、冷热分离、3 天前只查不展示）

**数据库**
- 索引怎么建的？（username 唯一、uk_reporter_target、status、create_time 等）
- 慢查询怎么排查？（慢查询日志 + EXPLAIN）
- 分页深翻性能？（覆盖索引、游标分页）

**并发**
- 还有哪些并发场景？（签到、捞瓶、感谢、点赞）
- 分布式锁的 Redlock 了解吗？（讲清取舍）

**前端**
- 首屏优化？（路由懒加载、按需引入组件、gzip）
- 组件通信方式？（props/emit、Pinia、provide/inject）
- 为什么 token 存 sessionStorage 不存 localStorage？（安全：标签页关闭即失效）

**安全**
- XSS/CSRF？（CSP、转义、JWT 无需 CSRF Token）
- 越权访问？（接口级 + 数据级校验，如只能查自己的会话）

**复盘**
- 如果重做会改什么？（AI 调用抽离、连接池调优、单元测试覆盖、日志规范化）

---

## 十一、面试前的自检

- [ ] 能用 1 分钟讲清项目"是什么、技术栈、我做了什么"
- [ ] 能画出 JWT 鉴权流程图
- [ ] 能讲清 SSE 流式的 3 个关键坑（ASYNC permitAll / 不提前 break / 不加事务）
- [ ] 能讲清分布式锁的 SET NX EX + Lua + 降级 + 立即释放
- [ ] 能讲清两个封禁方法的区别和各自用途
- [ ] 能说出 3 个真实踩坑故事（根因 + 解决）
- [ ] 能对比 HTTP/WebSocket/SSE 场景
- [ ] 能讲清 Vue3 响应式（Proxy）和 Pinia 选型理由

---

> 使用建议：先通读理解"搭建流程"和"核心技术"章节建立全局观，再重点背"项目深挖问答"和"踩坑故事"，最后过一遍"追问清单"查漏补缺。讲项目时多用"问题→方案→取舍→踩坑"的结构，比平铺直叙更有说服力。
