# zenmind-app-server-go

`zenmind-app-server` 的 Go + React 重写版，目标是保持前端行为不变，并兼容既有后端 API 路径。

## 1. 项目简介

- 后端服务地址：`http://localhost:11952`
- 前端管理台地址：`http://localhost:11950/admin/`
- 兼容 API 前缀：
  - `/admin/api/*`
  - `/api/auth/*`
  - `/api/app/*`
  - `/oauth2/*`
  - `/openid/*`

## 2. 前置依赖

本仓库有两套启动方式，依赖不同：

- Docker 方式：
  - Docker
  - Docker Compose（`docker compose`）
- 源码方式：
  - Go `1.23.x`
  - Node.js `20.x`
  - npm

## 3. 环境变量准备

在项目根目录执行：

```bash
cp .env.example .env
```

`.env.example` 提供了可直接启动的开发默认值：

- Admin 登录：`admin / password`
- App master password：`password`

生产环境必须替换以下变量为你自己的 bcrypt：

- `AUTH_ADMIN_PASSWORD_BCRYPT`
- `AUTH_APP_MASTER_PASSWORD_BCRYPT`

推荐本地开发同步设置：

- `AUTH_ISSUER=http://localhost:11952`

> 说明：默认示例值为 `http://localhost:8080`，若你本地后端跑在 `11952`，建议改成 `11952` 保持 OIDC 元数据一致。

外部配置文件白名单在 `backend/application.yml` 的 `external.editable-files` 中维护；仅白名单内且已存在的文件允许通过管理台修改保存。

### 3.1 生成 bcrypt（macOS / Linux / Windows）

#### 方式 A：服务启动后，通过接口生成（跨平台通用）

macOS / Linux：

```bash
curl -sS -X POST http://localhost:11952/admin/api/bcrypt/generate \
  -H 'Content-Type: application/json' \
  -d '{"password":"MyStrongPassword!123"}'
```

Windows（PowerShell 7+）：

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:11952/admin/api/bcrypt/generate" `
  -ContentType "application/json" `
  -Body '{"password":"MyStrongPassword!123"}'
```

#### 方式 B：离线本地生成（无需启动服务）

macOS（本机 `htpasswd`，无需 Docker）：

```bash
htpasswd -nbBC 10 '' 'MyStrongPassword!123' | tr -d ':\n'; echo
```

```bash
docker run --rm --entrypoint htpasswd httpd:2.4-alpine -nBC 10 '' 'MyStrongPassword!123' | tr -d ':\n'; echo
```

说明：命令输出即 bcrypt 哈希（通常是 `$2y$...`，本项目可直接使用）。若在 Linux/Windows（Git Bash/WSL）也可使用同一条 bash 命令。

将输出的 bcrypt 替换到 `.env`，建议用单引号包裹，避免 `$` 被 shell 展开。

## 4. 快速启动（Docker 一键）

在项目根目录执行：

```bash
docker compose up --build
```

后台启动：

```bash
docker compose up -d --build
```

停止：

```bash
docker compose down
```

启动后访问：

- 管理台：`http://localhost:11950/admin/`
- 后端：`http://localhost:11952`

## 5. 本地开发启动（源码模式）

### 5.1 启动后端

```bash
cd backend
go run ./cmd/server
```

### 5.2 启动前端（另一个终端）

```bash
cd frontend
npm ci
npm run dev
```

默认访问地址：

- 前端管理台：`http://localhost:11950/admin/`
- 后端接口：`http://localhost:11952`

## 6. 打包镜像与部署方案

### 6.1 本地生成发布包

在项目根目录执行：

```bash
./release-scripts/mac/package.sh
```

执行后生成 `release/` 目录，包含：

- `release/backend/`（后端二进制 + Dockerfile）
- `release/frontend/`（前端 dist + gateway 二进制 + Dockerfile）
- `release/docker-compose.yml`
- `release/release-scripts/`（JWK 脚本）
- `release/DEPLOY.md`

### 6.2 在目标环境部署

进入发布目录并准备运行时变量：

```bash
cd release
```

在 `release/.env` 手工写入运行配置（可参考仓库根目录 `.env.example`），至少包含：

- `AUTH_ADMIN_PASSWORD_BCRYPT`
- `AUTH_APP_MASTER_PASSWORD_BCRYPT`
- `AUTH_ISSUER`

如果是从旧版本升级并需要清理站内信遗留表，可先执行：

```bash
sqlite3 ./data/auth.db < ./backend/drop_inbox.sql
```

初始化（或导出）JWK 密钥：

```bash
./release-scripts/mac/setup-jwk-public-key.sh \
  --mode bootstrap \
  --db ./data/auth.db \
  --out ./data/keys \
  --public-out ./data/keys/publicKey.pem
```

启动服务：

```bash
docker compose up -d --build
```

### 6.3 Windows 关键命令（JWK）

PowerShell 7+：

```powershell
.\release-scripts\windows\setup-jwk-public-key.ps1 `
  -Mode bootstrap `
  -DbPath .\data\auth.db `
  -OutDir .\data\keys `
  -PublicOut .\data\keys\publicKey.pem
```

## 7. 测试与验证

### 7.1 后端测试

```bash
cd backend
go test ./...
```

### 7.2 前端构建验证

```bash
cd frontend
npm ci
npm run build
```

### 7.3 接口 Smoke 测试（服务启动后）

OIDC 元数据：

```bash
curl -i http://localhost:11952/openid/.well-known/openid-configuration
```

JWK 公钥集：

```bash
curl -i http://localhost:11952/openid/jwks
```

未登录管理会话检查（预期 `401`）：

```bash
curl -i http://localhost:11952/admin/api/session/me
```

## 8. 常用命令速查

项目根目录 `Makefile`：

```bash
make backend-build    # 构建后端 linux/amd64 二进制
make backend-test     # 后端测试
make frontend-build   # 前端构建
make up               # docker compose up --build
make down             # docker compose down
make size-check       # 查看镜像体积
```

镜像大小（字节）：

```bash
docker image inspect app-auth-backend-go --format '{{.Size}}'
docker image inspect app-auth-frontend-go --format '{{.Size}}'
```

## 9. 常见问题与排查

`zsh: command not found: go`

- 原因：未安装 Go，或未加入 PATH。
- 处理：安装 Go 1.23.x，并确认 `go version` 可执行。

`vite: command not found` 或 `npm run dev` 失败

- 原因：`node_modules` 未安装。
- 处理：在 `frontend/` 执行 `npm ci` 后重试。

`AUTH_ADMIN_PASSWORD_BCRYPT must be a valid bcrypt hash`

- 原因：`.env` 中 bcrypt 不合法，或 `$` 被错误转义。
- 处理：参考 `.env.example` 的引号写法，确保是完整 bcrypt 字符串。

`invalid admin credentials`

- 原因：用户名不匹配，或输入明文与 `AUTH_ADMIN_PASSWORD_BCRYPT` 不匹配。
- 处理：
  - 检查用户名是否为 `AUTH_ADMIN_USERNAME`（默认 `admin`）。
  - 确认容器实际生效的环境变量（例如 `docker compose exec backend env | rg 'AUTH_ADMIN_USERNAME|AUTH_ADMIN_PASSWORD_BCRYPT'`）。
  - 用 `POST /admin/api/bcrypt/generate` 重新生成 bcrypt 并替换 `.env`。
  - 更新 `.env` 后重启服务（`docker compose down && docker compose up -d --build`）。

端口冲突（`11950` / `11952`）

- 原因：已有进程占用。
- 处理：修改 `.env` 中 `FRONTEND_PORT` / `BACKEND_PORT`，或释放占用端口。

## 10. 仓库结构

- `backend/`：Go 后端源码
- `frontend/`：React 前端与网关源码
- `data/`：SQLite 数据目录（默认 `auth.db`）
- `release-scripts/`：发布与 JWK 管理脚本
- `docker-compose.yml`：本地容器编排
- `API_CONTRACT.md`：接口清单
- `RELEASE.md`：发布说明（简版）
# zenmind-app-server-go
