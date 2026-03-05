# CLAUDE.md

## 1. 项目概述

`zenmind-app-server` 是一套认证与管理服务，包含：

- Go 后端（OAuth2 / OIDC / Admin API / App API）
- React 管理台
- Go 前端网关（负责 `/admin/*` 静态资源与 API 反向代理）

默认容器化部署，核心目标是提供统一的用户、客户端、令牌与设备管理能力。

## 2. 架构

整体由两个容器组成：

- `backend`：
  - 入口：`backend/cmd/server/main.go`
  - 路由：`backend/internal/app/server.go`
  - 配置：`backend/internal/config/config.go`
  - 存储：SQLite（`./data/auth.db`）
- `frontend`：
  - 静态站点：`frontend/dist`
  - 网关：`frontend/proxy/main.go`
  - 将 `/admin/api/*`、`/oauth2/*`、`/openid/*` 转发到后端

数据层通过 `backend/schema.sql` 初始化；JWK 密钥持久化在数据库 `JWK_KEY_` 表中。

## 3. API 定义

API 分组以 `API_CONTRACT.md` 与 `server.go` 为准：

- OAuth2：`/oauth2/*`
  - `POST /oauth2/token`
  - `POST /oauth2/revoke`
  - `POST /oauth2/introspect`
- OIDC：`/openid/*`
  - `GET /openid/.well-known/openid-configuration`
  - `GET /openid/jwks`
  - `GET/POST /openid/userinfo`
  - `GET/POST /openid/login`
  - `GET/POST /openid/consent`
- App Auth：`/api/auth/*`
  - 登录、刷新、登出、设备管理、JWK 查询
- App Event：`/api/app/ws`
  - WebSocket 通道
- Admin：`/admin/api/*`
  - Session、用户管理、客户端管理、配置文件管理、安全工具

错误响应统一格式：

```json
{"error":"..."}
```

## 4. 数据结构

主要 Go 领域模型（`backend/internal/model/types.go`）：

- `AppPrincipal`：应用访问主体（用户名、设备 ID、签发时间）
- `AdminSession`：管理台会话
- `OAuthUserSession`：OAuth 登录会话
- `OAuthCode`：授权码信息
- `OAuthTokens`：OAuth token 对信息

主要持久化模型（`backend/internal/store/store.go`）：

- `User`
- `OAuthClient`
- `Device`
- `TokenAudit`
- `OAuthAuthorization`

主要数据库对象（`backend/schema.sql`）：

- `APP_USER_`
- `OAUTH2_CLIENT_`
- `JWK_KEY_`
- `DEVICE_`
- `TOKEN_AUDIT_`
- `oauth2_authorization`
- `oauth2_authorization_consent`

## 5. 开发要点

- 本地开发使用 `.env`，建议从 `.env.example` 复制。
- `AUTH_APP_MAX_ACCESS_TTL` 默认 `P30D`，与前端安全页的最大 TTL 限制一致。
- bcrypt 变量必须是合法 bcrypt 串，且建议在 `.env` 中用单引号包裹。
- `external.editable-files` 白名单来源于 `backend/application.yml`，仅白名单内且已存在文件可被管理台保存。
- 任何 API 变更应同步 `API_CONTRACT.md` 与本文档第 3 节。

## 6. 代码与配置约定

- Go module：`zenmind-app-server/backend`
- 容器镜像：`zenmind-app-backend`、`zenmind-app-frontend`
- 容器名：`app-auth-backend`、`app-auth-frontend`
- 统一通过 `docker-compose.yml` 管理本地部署。
- 不提交本地敏感配置：`.env` 已在 `.gitignore` 中忽略。
- 文档职责分工：
  - `README.md`：部署与操作手册
  - `CLAUDE.md`：项目知识与开发约束
  - `API_CONTRACT.md`：接口目录与路径清单
