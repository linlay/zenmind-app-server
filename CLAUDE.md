# AGW App Server - CLAUDE.md

本文档基于当前项目实现（`backend` + `frontend`）整理，目标是让开发、联调、网关接入时可以快速理解系统结构与 API 约定。

## 1. 技术架构

### 1.1 系统定位
`agw-app-server` 是认证与通知中台，统一提供：
- OAuth2 / OIDC 授权服务（授权码 + 刷新）
- App 轻量认证（主密码 + 设备令牌 + 短期 JWT）
- 消息盒子（Inbox）与 WebSocket 实时推送
- 管理端 API（用户、客户端、会话、消息管理）

### 1.2 技术栈
- Backend
  - Java 21
  - Spring Boot 3.3.7
  - Spring Security + Spring Authorization Server 1.4.1
  - Spring JDBC + SQLite
  - Spring WebSocket
  - Caffeine Cache
- Frontend
  - React + Vite
  - Nginx 反向代理 `admin/api` / `oauth2` / `openid`

### 1.3 运行组件与端口
- Backend: `http://localhost:8080`
- Frontend(Admin): `http://localhost:8081/admin/`

### 1.4 分层结构（Backend）
- `web`：REST 控制器、页面控制器、全局异常处理
- `service`：认证、设备管理、会话、消息盒子、JWK、签名校验
- `security`：App Bearer Filter、Admin Cookie Interceptor、RegisteredClientRepository
- `config`：SecurityFilterChain、WebSocket、缓存、属性绑定
- `websocket`：握手鉴权、会话注册、广播推送
- `resources/schema.sql`：SQLite 表结构与视图初始化

### 1.5 鉴权模型

#### OAuth2/OIDC
- 协议端点：`/oauth2/*`、`/openid/*`
- 登录页：`/openid/login`
- 同意页：`/openid/consent`
- `userinfo` 由 OAuth2 JWT 资源服务器保护

#### App API（`/api/auth/**`、`/api/app/**`）
- 由 `AppApiAuthFilter` 统一处理 Bearer Token
- 免鉴权路径：
  - `/api/auth/login`
  - `/api/auth/refresh`
  - `/api/auth/jwks`
  - `/api/app/ws`
  - `/api/app/internal/chat-events`
- App Access Token
  - 算法：RS256
  - 关键 claim：`sub`、`scope=app`、`device_id`
  - 过期：默认 `auth.app.access-ttl`（`PT10M`），可通过请求体 `accessTtlSeconds` 申请更长有效期（受 `auth.app.max-access-ttl` 限制）
- Device Token
  - 明文仅在签发时返回
  - 数据库存 BCrypt 哈希（`DEVICE_.DEVICE_TOKEN_BCRYPT_`）

#### Admin API（`/admin/api/**`）
- `AdminApiInterceptor` 校验 `ADMIN_SESSION` Cookie
- 公开接口：
  - `/admin/api/session/login`
  - `/admin/api/bcrypt/generate`
- 会话缓存：Caffeine，`expireAfterAccess=8h`

#### 内部回调鉴权
- 接口：`POST /api/app/internal/chat-events`
- 头：
  - `X-AGW-Timestamp`（秒级 Unix 时间戳）
  - `X-AGW-Signature`（hex）
- 签名串：`timestamp + "." + rawBody`
- 算法：HMAC-SHA256
- 时间偏差容忍：300 秒

### 1.6 WebSocket 实时推送
- 端点：`/api/app/ws`
- 握手鉴权：
  - `Authorization: Bearer <accessToken>` 或
  - `?access_token=<accessToken>`
- 消息 envelope：
  - `type`
  - `timestamp`（毫秒）
  - `payload`（对象）
- 事件类型：
  - `system.ping`
  - `inbox.new`
  - `inbox.sync`
  - `chat.new_content`
  - `realtime.event`

### 1.7 数据模型（SQLite）
核心业务表：
- `APP_USER_`：业务用户
- `OAUTH2_CLIENT_`：OAuth 客户端
- `JWK_KEY_`：RSA 密钥
- `DEVICE_`：设备与设备令牌
- `INBOX_MESSAGE_`：消息盒子
- `CHAT_EVENT_DEDUP_`：聊天事件幂等去重（`chatId + runId`）

授权服务表：
- `oauth2_authorization`
- `oauth2_authorization_consent`

兼容视图：
- `OAUTH2_AUTHORIZATION_`
- `OAUTH2_CONSENT_`

### 1.8 关键配置
- `AUTH_ISSUER`
- `AUTH_DB_PATH`
- `AUTH_ADMIN_USERNAME`
- `AUTH_ADMIN_PASSWORD_BCRYPT`
- `AUTH_BOOTSTRAP_USER`
- `AUTH_BOOTSTRAP_PASSWORD_BCRYPT`
- `AUTH_BOOTSTRAP_DISPLAY`
- `AUTH_APP_USERNAME`
- `AUTH_APP_MASTER_PASSWORD_BCRYPT`
- `AUTH_APP_ACCESS_TTL`
- `AUTH_APP_MAX_ACCESS_TTL`
- `AUTH_APP_ROTATE_DEVICE_TOKEN`
- `AUTH_APP_INTERNAL_WEBHOOK_SECRET`

## 2. API 定义

### 2.1 API 前缀
- OAuth2：`/oauth2/*`
- OpenID/OIDC：`/openid/*`
- App Auth：`/api/auth/*`
- App Inbox：`/api/app/*`
- Admin：`/admin/api/*`

### 2.2 App Auth API

| Method | Path | 鉴权 | 请求体 | 响应（核心字段） |
|---|---|---|---|---|
| POST | `/api/auth/login` | 无 | `masterPassword`, `deviceName`, `accessTtlSeconds?` | `username`, `deviceId`, `deviceName`, `accessToken`, `accessTokenExpireAt`, `deviceToken` |
| POST | `/api/auth/refresh` | 无 | `deviceToken`, `accessTtlSeconds?` | `deviceId`, `accessToken`, `accessTokenExpireAt`, `deviceToken` |
| POST | `/api/auth/logout` | Bearer | - | 204 |
| GET | `/api/auth/me` | Bearer | - | `username`, `deviceId`, `issuedAt` |
| GET | `/api/auth/devices` | Bearer | - | `AppDeviceResponse[]` |
| PATCH | `/api/auth/devices/{deviceId}` | Bearer | `deviceName` | `AppDeviceResponse` |
| DELETE | `/api/auth/devices/{deviceId}` | Bearer | - | 204 |
| GET | `/api/auth/jwks` | 无 | - | `{"keys":[...]}` |

### 2.3 App Inbox API

| Method | Path | 鉴权 | 请求/参数 | 响应 |
|---|---|---|---|---|
| GET | `/api/app/inbox` | Bearer | `unreadOnly`(默认 false), `limit`(默认 50, 最大 200) | `InboxMessageResponse[]` |
| GET | `/api/app/inbox/unread-count` | Bearer | - | `{"unreadCount": number}` |
| POST | `/api/app/inbox/read` | Bearer | `{"messageIds":[UUID,...]}` | 204 |
| POST | `/api/app/inbox/read-all` | Bearer | - | 204 |
| POST | `/api/app/internal/chat-events` | HMAC | 头：`X-AGW-Timestamp`、`X-AGW-Signature`，体：`chatId`、`runId`、`updatedAt?`、`chatName?` | `{"accepted":true,"duplicate":boolean}` |
| WS | `/api/app/ws` | Bearer | Header 或 query token | 实时事件流 |

### 2.4 Admin Session / Security API

| Method | Path | 鉴权 | 请求体 | 响应 |
|---|---|---|---|---|
| POST | `/admin/api/session/login` | 无 | `username`, `password` | `Set-Cookie: ADMIN_SESSION=...`; body: `username`, `issuedAt` |
| POST | `/admin/api/session/logout` | Cookie | - | 204（同时清理 Cookie） |
| GET | `/admin/api/session/me` | Cookie | - | `username`, `issuedAt` |
| POST | `/admin/api/bcrypt/generate` | 无 | `password` | `{"bcrypt":"$2a$..."}` |

### 2.5 Admin User API

| Method | Path | 鉴权 | 请求体 | 响应 |
|---|---|---|---|---|
| GET | `/admin/api/users` | Cookie | - | `UserResponse[]` |
| POST | `/admin/api/users` | Cookie | `username`, `password`, `displayName`, `status(ACTIVE/DISABLED)` | `UserResponse`（201） |
| GET | `/admin/api/users/{userId}` | Cookie | - | `UserResponse` |
| PUT | `/admin/api/users/{userId}` | Cookie | `displayName`, `status` | `UserResponse` |
| PATCH | `/admin/api/users/{userId}/status` | Cookie | `status` | `UserResponse` |
| POST | `/admin/api/users/{userId}/password` | Cookie | `password` | 204 |

### 2.6 Admin Client API

| Method | Path | 鉴权 | 请求体 | 响应 |
|---|---|---|---|---|
| GET | `/admin/api/clients` | Cookie | - | `ClientResponse[]` |
| POST | `/admin/api/clients` | Cookie | `clientId`, `clientName`, `clientSecret?`, `grantTypes[]`, `redirectUris[]`, `scopes[]`, `requirePkce`, `status` | `ClientResponse`（201） |
| GET | `/admin/api/clients/{clientId}` | Cookie | - | `ClientResponse` |
| PUT | `/admin/api/clients/{clientId}` | Cookie | `clientName`, `grantTypes?`, `redirectUris?`, `scopes`, `requirePkce?`, `status?` | `ClientResponse` |
| PATCH | `/admin/api/clients/{clientId}/status` | Cookie | `status` | `ClientResponse` |
| POST | `/admin/api/clients/{clientId}/secret/rotate` | Cookie | - | `{"clientId":"...","newClientSecret":"..."}` |

### 2.7 Admin Inbox API

| Method | Path | 鉴权 | 请求/参数 | 响应 |
|---|---|---|---|---|
| GET | `/admin/api/inbox` | Cookie | `unreadOnly`(默认 false), `limit`(默认 100, 最大 200) | `InboxMessageResponse[]` |
| GET | `/admin/api/inbox/unread-count` | Cookie | - | `{"unreadCount": number}` |
| POST | `/admin/api/inbox/send` | Cookie | `title`, `content`, `type?`, `payload?` | `InboxMessageResponse`（201） |
| POST | `/admin/api/inbox/read` | Cookie | `{"messageIds":[UUID,...]}` | 204 |
| POST | `/admin/api/inbox/read-all` | Cookie | - | 204 |
| POST | `/admin/api/inbox/realtime` | Cookie | 任意 JSON（可空） | 204（仅推送，不落库） |

### 2.8 OAuth2 / OIDC API

| Method | Path | 说明 |
|---|---|---|
| GET | `/oauth2/authorize` | OAuth2 授权入口（支持 PKCE） |
| POST | `/oauth2/token` | 令牌签发（含 refresh token） |
| POST | `/oauth2/revoke` | 令牌撤销 |
| POST | `/oauth2/introspect` | 令牌自省 |
| GET | `/openid/jwks` | OIDC 公钥集 |
| GET | `/openid/.well-known/openid-configuration` | OIDC 发现文档 |
| GET | `/openid/.well-known/oauth-authorization-server` | OAuth AS 元数据 |
| GET/POST | `/openid/userinfo` | 需 OAuth Bearer Token |
| GET | `/openid/login` | 登录页面 |
| GET/POST | `/openid/consent` | 授权同意页面 |

## 3. 错误与状态码约定

- 错误体统一形态：`{"error":"..."}`
- 常见状态码：
  - `400` 参数错误 / 业务校验失败
  - `401` 未认证（Bearer / Cookie / HMAC 校验失败）
  - `409` 资源冲突（如唯一键冲突）

## 4. 默认开发账号

- Admin：`admin / password`
- OAuth 测试用户：`user / password`
- App 主密码：`password`
