# CLAUDE.md

## 1. 项目概览

`zenmind-app-server` 是一套认证与管理服务，提供 OAuth2 / OIDC、管理后台、应用侧令牌管理、设备管理，以及托管配置文件查看与编辑能力。

仓库采用前后端分离但统一编排的 fullstack 结构：

- `backend/`：Go 后端，负责认证、授权、管理 API、配置文件管理和 SQLite 持久化
- `frontend/`：React 管理台和 Go 反向代理网关
- 根目录：维护 compose、Makefile、环境变量契约和仓库级文档

## 2. 技术栈

- Backend：Go 1.23
- HTTP 路由：`github.com/go-chi/chi/v5`
- 前端：React 18 + React Router 6 + Vite 5
- 前端网关：Go
- 数据库：SQLite
- 配置格式：`.env` + YAML runtime registry
- 容器化：Docker / `docker compose`

## 3. 架构设计

系统默认由两个容器组成：

- `backend`
  - 入口：`backend/cmd/server/main.go`
  - 路由与业务入口：`backend/internal/app/server.go`
  - 配置加载：`backend/internal/config/config.go`
  - 配置注册表同步：`backend/internal/managedconfigsync/sync.go`
  - 数据访问：`backend/internal/store/store.go`
- `frontend`
  - React 管理台构建产物：`frontend/dist`
  - Go 网关入口：`frontend/proxy/main.go`
  - 对 `/admin/api/*`、`/oauth2/*`、`/openid/*` 等路径做代理转发

关键调用关系：

- 浏览器访问 `frontend` 容器的 `/admin/`
- 前端 UI 调用 `/admin/api/*`
- 网关将 API 请求转发给 `backend`
- `backend` 通过 SQLite 持久化用户、客户端、令牌审计和授权数据
- “Config Files” 页面读取 backend 挂载到 `/app/config` 下的受管文件

## 4. 目录结构

仓库级主要目录与文件职责：

- `README.md`：使用、部署、运维手册
- `CLAUDE.md`：项目事实、结构与设计说明
- `.env.example`：环境变量契约
- `docker-compose.yml`：本地编排、端口和卷挂载
- `Makefile`：仓库级常用命令入口
- `configs/config-files.yml`：托管可编辑配置文件注册表源文件
- `backend/`
  - `cmd/server/`：后端服务入口
  - `cmd/managedconfigsync/`：受管配置注册表同步入口
  - `internal/app/`：HTTP 处理与业务流程
  - `internal/config/`：环境变量与运行时配置装载
  - `internal/managedconfigregistry/`：配置注册表读写模型
  - `internal/managedconfigsync/`：从源注册表生成 runtime registry 并更新 compose
  - `internal/store/`：SQLite 持久化实现
  - `schema.sql`：数据库初始化结构
- `frontend/`
  - `src/`：管理台页面与共享 UI
  - `proxy/`：网关进程
  - `Dockerfile` / `nginx.conf`：前端构建与发布配套文件
- `release-scripts/`：JWK 初始化和公钥导出脚本

## 5. 数据结构

核心领域模型位于 `backend/internal/model/types.go`，包括：

- `AppPrincipal`：应用访问主体
- `AdminSession`：管理后台会话
- `OAuthUserSession`：OAuth 登录会话
- `OAuthCode`：授权码数据
- `OAuthTokens`：OAuth token 对数据

核心持久化对象位于 `backend/internal/store/store.go`，包括：

- `User`
- `OAuthClient`
- `Device`
- `TokenAudit`
- `OAuthAuthorization`

主要数据库对象定义位于 `backend/schema.sql`，包括：

- `APP_USER_`
- `OAUTH2_CLIENT_`
- `JWK_KEY_`
- `DEVICE_`
- `TOKEN_AUDIT_`
- `oauth2_authorization`
- `oauth2_authorization_consent`

## 6. API 定义

认证与管理接口按以下前缀划分：

- OAuth2：`/oauth2/*`
- OIDC：`/openid/*`
- App Auth：`/api/auth/*`
- App Event：`/api/app/*`
- Admin：`/admin/api/*`

主要接口如下。

App Auth：

- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `GET /api/auth/devices`
- `PATCH /api/auth/devices/{deviceId}`
- `DELETE /api/auth/devices/{deviceId}`
- `GET /api/auth/jwks`
- `GET /api/auth/new-device-access`

App Event：

- `GET /api/app/ws`

Admin Session：

- `POST /admin/api/session/login`
- `POST /admin/api/session/logout`
- `GET /admin/api/session/me`

Admin Security：

- `POST /admin/api/bcrypt/generate`
- `GET /admin/api/security/jwks`
- `POST /admin/api/security/public-key/generate`
- `POST /admin/api/security/key-pair/generate`
- `POST /admin/api/security/app-tokens/issue`
- `POST /admin/api/security/app-tokens/refresh`
- `GET /admin/api/security/app-devices`
- `POST /admin/api/security/app-devices/{deviceId}/revoke`
- `GET /admin/api/security/new-device-access`
- `PUT /admin/api/security/new-device-access`
- `GET /admin/api/security/tokens`

Admin Users：

- `GET /admin/api/users`
- `POST /admin/api/users`
- `GET /admin/api/users/{userId}`
- `PUT /admin/api/users/{userId}`
- `PATCH /admin/api/users/{userId}/status`
- `POST /admin/api/users/{userId}/password`

Admin Clients：

- `GET /admin/api/clients`
- `POST /admin/api/clients`
- `GET /admin/api/clients/{clientId}`
- `PUT /admin/api/clients/{clientId}`
- `PATCH /admin/api/clients/{clientId}/status`
- `POST /admin/api/clients/{clientId}/secret/rotate`

Admin Config Files：

- `GET /admin/api/config-files`
- `GET /admin/api/config-files/content?id={configFileId}`
- `PUT /admin/api/config-files/content`

OAuth2 / OIDC：

- `GET /oauth2/authorize`
- `POST /oauth2/token`
- `POST /oauth2/revoke`
- `POST /oauth2/introspect`
- `GET /openid/.well-known/openid-configuration`
- `GET /openid/.well-known/oauth-authorization-server`
- `GET /openid/jwks`
- `GET /openid/userinfo`
- `POST /openid/userinfo`
- `GET /openid/login`
- `POST /openid/login`
- `GET /openid/consent`
- `POST /openid/consent`

错误响应统一为：

```json
{"error":"..."}
```

## 7. 开发要点

- 配置加载优先级以代码默认值和环境变量为主，`.env.example` 只维护契约，不维护运行事实。
- `backend/internal/config/config.go` 会加载 `.env`，并验证 bcrypt、TTL、cleanup 等关键配置。
- 托管可编辑配置文件源定义在 `configs/config-files.yml`，运行时文件生成到 `configs/config-files.runtime.yml`。
- `docker-compose.yml` 中托管配置卷块由 `make config-sync` 自动更新，手工改动生成块会被覆盖。
- `Dockerfile` 只负责镜像构建，不应承载真实密钥或部署默认值。
- API 调整应同步更新本文件第 6 章以及相关 handler、前端调用和测试。

## 8. 开发流程

本地开发常用流程：

1. 从 `.env.example` 复制生成 `.env`
2. 执行 `make config-sync`
3. 使用 `docker compose up -d --build` 启动双端
4. 后端改动后执行 `make backend-test`
5. 前端改动后执行 `make frontend-build`

发布相关流程：

- 后端镜像由 `backend/Dockerfile` 构建
- 前端镜像由 `frontend/Dockerfile` 构建
- JWK 初始化与导出使用 `release-scripts/` 中脚本

## 9. 已知约束与注意事项

- 当前本地编排依赖外部 Docker network `zenmind-network`，使用前必须确保该网络存在。
- backend 使用 SQLite，本地默认数据目录为 `./data` 的 bind mount。
- `configs/config-files.runtime.yml` 属于生成产物，不应提交。
- 托管配置文件能力依赖宿主机路径存在；源文件不存在时，管理台只能显示不可编辑状态。
- 兼容路径参数的旧版 `Config Files` API 仍保留迁移兼容语义，但前端和注册表已以 `id` 为主。
