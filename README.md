# zenmind-app-server

`zenmind-app-server` 是一个基于 Go + React 的认证与管理服务，采用容器化部署。
本手册只包含部署与日常操作。

## 1. 前置条件

- Docker
- Docker Compose (`docker compose`)

## 2. 端口与访问地址

默认端口（可通过 `.env` 覆盖）：

- Backend: `11952`（容器内 `8080`）
- Frontend: `11950`（容器内 `80`）

默认访问：

- 管理台：`http://localhost:11950/admin/`
- 后端基址：`http://localhost:11952`

## 3. 环境变量准备

在项目根目录生成本地运行配置：

```bash
cp .env.example .env
```

必须在 `.env` 中确认以下关键项：

- `AUTH_ADMIN_PASSWORD_BCRYPT`
- `AUTH_APP_MASTER_PASSWORD_BCRYPT`

如需覆盖默认行为，可在 `.env` 中设置：

- `AUTH_ISSUER`（建议改为实际后端地址，如 `http://localhost:11952`）
- `AUTH_APP_ACCESS_TTL=PT10M`
- `AUTH_APP_MAX_ACCESS_TTL=P30D`（30 天）

说明：示例 bcrypt 仅用于本地开发，生产必须替换。

说明：

- 根目录 `.env` 同时承担两件事：
  - 作为 Docker Compose 的变量输入文件
  - 通过 `env_file` 注入 backend 容器
- 非敏感默认值直接内置在后端源码中。
- 管理台 “Config Files” 页面展示的配置名称和宿主机路径，不再从 Compose 里手写，而是来自 `release/config-files.yml`。

## 3.1 Managed Config Registry

可管理配置文件统一定义在 `release/config-files.yml`：

- `id`: API 和前端使用的稳定标识
- `name`: 管理台展示名称
- `type`: 展示用途的类型标签
- `sourcePath`: 宿主机文件路径
- `containerPath`: 挂载到 backend 容器后的路径

修改该文件后执行：

```bash
make config-sync
```

它会自动：

- 生成本机运行用的 `release/config-files.runtime.yml`
- 更新 `docker-compose.yml` 中的配置文件挂载块

## 4. 生成 bcrypt

### 4.1 服务启动后通过接口生成

```bash
curl -sS -X POST http://localhost:11952/admin/api/bcrypt/generate \
  -H 'Content-Type: application/json' \
  -d '{"password":"MyStrongPassword!123"}'
```

PowerShell 7+：

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:11952/admin/api/bcrypt/generate" `
  -ContentType "application/json" `
  -Body '{"password":"MyStrongPassword!123"}'
```

### 4.2 离线生成（macOS / Linux）

```bash
htpasswd -nbBC 10 '' 'MyStrongPassword!123' | tr -d ':\n'; echo
```

或：

```bash
docker run --rm --entrypoint htpasswd httpd:2.4-alpine -nBC 10 '' 'MyStrongPassword!123' | tr -d ':\n'; echo
```

将输出替换到 `.env`，建议使用单引号包裹，避免 `$` 被 shell 展开。

## 5. 启动与停止

项目根目录：

```bash
make config-sync
docker compose up -d --build
```

查看状态：

```bash
docker compose ps
```

停止并移除容器：

```bash
docker compose down
```

查看日志：

```bash
docker compose logs -f backend
docker compose logs -f frontend
```

## 6. 常用运维操作

### 6.1 镜像与容器信息

```bash
docker image ls | rg 'zenmind-app-backend|zenmind-app-frontend'
docker image inspect app-auth-backend --format '{{.Size}}'
docker image inspect app-auth-frontend --format '{{.Size}}'
```

### 6.2 健康与接口检查

```bash
curl -i http://localhost:11952/openid/.well-known/openid-configuration
curl -i http://localhost:11952/openid/jwks
curl -i http://localhost:11952/admin/api/session/me
```

说明：未登录时 `/admin/api/session/me` 预期返回 `401`。

### 6.3 数据库文件位置

默认数据库在挂载目录 `./data/auth.db`（由 `docker-compose.yml` 的 `./data:/data` 提供）。

### 6.4 Config Files 挂载与编辑

`backend` 容器统一使用 `/app/config` 作为可编辑配置目录。具体有哪些文件、显示什么名称、对应哪个宿主机路径，都由 `release/config-files.yml` 决定，并通过 `make config-sync` 生成到 Compose 与运行时注册表。

当前默认注册表包含以下 4 个文件：

- `/app/config/zenmind-root.env`
- `/app/config/mcp-server-mock.env`
- `/app/config/term-webclient-release.env`
- `/app/config/term-webclient-release.application.yml`

对应宿主机挂载来源：

- `./.env` -> `/app/config/zenmind-root.env`
- `../mcp-server-mock/.env` -> `/app/config/mcp-server-mock.env`
- `../term-webclient/release/.env` -> `/app/config/term-webclient-release.env`
- `../term-webclient/release/application.yml` -> `/app/config/term-webclient-release.application.yml`

说明：

- 以上挂载均为读写（rw），可在管理台 `Config Files` 页面直接修改并回写宿主机文件。
- 页面会显示注册表中的 `name` 与宿主机完整路径。
- 若某源文件不存在，`Config Files` 页面会显示 `Exists = NO`，且不可保存。

### 6.5 配置分层规则

- 后端源码：内置非敏感默认值，例如 issuer 默认值、用户名默认值、TTL、cleanup 参数。
- `.env`：敏感值和环境覆盖值，例如两个 bcrypt，以及需要覆盖默认值时的 `AUTH_*`。
- `release/config-files.yml`：定义管理台可编辑的外部配置文件及其展示名称。
- `docker-compose.yml`：只负责 `env_file`、端口、volume 与容器编排，不再逐项展开 backend 的全部环境变量。

## 7. JWK 管理

脚本位于 `release-scripts/`。

macOS / Linux：

```bash
./release-scripts/mac/manage-jwk-key.sh --mode bootstrap --db ./data/auth.db --out ./data/keys
./release-scripts/mac/setup-jwk-public-key.sh --mode bootstrap --db ./data/auth.db --out ./data/keys --public-out ./data/keys/publicKey.pem
```

Windows PowerShell：

```powershell
.\release-scripts\windows\setup-jwk-public-key.ps1 `
  -Mode bootstrap `
  -DbPath .\data\auth.db `
  -OutDir .\data\keys `
  -PublicOut .\data\keys\publicKey.pem
```

## 8. 故障排查

### 8.1 提示 `requested access ttl exceeds limit`

- 原因：请求的 `accessTtlSeconds` 超过 `AUTH_APP_MAX_ACCESS_TTL`。
- 处理：确认 `.env` 中 `AUTH_APP_MAX_ACCESS_TTL=P30D`，重启服务后重试。

### 8.2 Admin 无法登录

- 检查 `.env` 的 `AUTH_ADMIN_PASSWORD_BCRYPT` 是否为完整 bcrypt。
- 避免 bcrypt 中 `$` 被 shell 展开，建议用单引号。
- 更新后执行：

```bash
docker compose down
docker compose up -d --build
```

### 8.3 端口冲突

- 调整 `.env` 中 `BACKEND_PORT` / `FRONTEND_PORT`。
- 或释放占用端口后重启容器。

## 9. 目录速览

- `backend/`: 后端服务（Go）
- `frontend/`: 前端与网关（React + Go gateway）
- `docker-compose.yml`: 容器编排
- `.env.example`: 环境变量模板
- `release-scripts/`: JWK 脚本
