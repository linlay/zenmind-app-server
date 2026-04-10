# CLAUDE.md

## 1. 项目概览

`zenmind-app-server` 是一套认证与管理服务，提供 OAuth2 / OIDC、管理后台、App 访问令牌和设备管理。

仓库采用双容器 fullstack 结构：

- `backend/`：Go API，容器内固定监听 `8080`
- `frontend/`：React 管理台与前端网关，对外暴露 `/admin/`

### Program Mode

backend 支持以单进程模式运行，同时提供 API 和前端静态文件服务。当设置 `FRONTEND_DIST_DIR` 环境变量时，backend 从该目录提供管理前端。此模式支持桌面集成，无需 Docker。

## 2. 技术栈

- Backend：Go 1.23
- Frontend：React 18 + Vite
- HTTP 路由：`chi`
- 数据库：SQLite
- 配置：`.env`
- 部署：Docker / `docker compose`

## 3. 架构设计

- 浏览器访问 `/admin/`
- 前端网关代理 `/admin/api/*`、`/oauth2/*`、`/openid/*` 到 backend
- backend 负责认证、授权、管理 API 和 SQLite 持久化
- 当前版本不再把外部可编辑配置文件作为默认部署能力
- `backend/internal/app/program_handler.go` 使 backend 可在单进程中同时提供 API 和前端静态文件。路由分发：`/admin/api/*`、`/oauth2/*`、`/openid/*`、`/api/*` 走 API handler；`/admin/*` 提供 SPA 前端；`/` 重定向到 `/admin/`

## 4. 目录结构

- `.env.example`：部署环境变量契约
- `compose.yml`：双容器本地编排
- `backend/`：Go 服务
- `frontend/`：管理台和前端网关
- `data/`：SQLite 持久化目录
- `scripts/release-program.sh`：Program 模式发布脚本
- `scripts/release-program-assets/`：Program 发布所需资源文件

## 5. 数据结构

核心模型仍位于：

- `backend/internal/model/types.go`
- `backend/internal/store/store.go`
- `backend/schema.sql`

## 6. API 定义

- Admin：`/admin/api/*`
- App Auth：`/api/auth/*`
- App Event：`/api/app/*`
- OAuth2：`/oauth2/*`
- OIDC：`/openid/*`
- `GET /admin/api/security/key-pair/export`：导出存储的 JWK 密钥对为 PEM 格式（需 admin 认证）

## 7. 开发要点

- `.env.example` 只维护部署必要字段
- backend 宿主机端口映射已从部署契约移除
- `BACKEND_PORT` 不再是有效部署变量；backend 容器内固定监听 `8080`
- `AUTH_ISSUER`、两个 bcrypt、前端 base path 仍是关键输入
- 当前仓库仍兼容旧代码里的可编辑配置文件能力，但它不再是默认部署模型

## 8. 开发流程

1. `cp .env.example .env`
2. `docker compose up -d --build`
3. 后端改动后执行 `make backend-test`
4. 前端改动后执行 `make frontend-build`
5. `make release-program`：构建单二进制 program 发布包，用于桌面集成（类似 agent-container-hub 模式）。包含 Go 二进制、frontend dist、schema.sql、启停脚本和 .env.example

## 9. 已知约束与注意事项

- backend 仅容器网络访问
- frontend 是唯一默认对外入口
- 公开 issuer 必须与真实部署入口一致
- 已支持 program mode（单二进制）用于桌面集成，与 Docker-first 部署模型并存
