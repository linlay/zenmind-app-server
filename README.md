# zenmind-app-server

## 1. 项目简介

`zenmind-app-server` 是一套基于 Go + React 的认证与管理服务，包含后端 API、管理台前端，以及一个负责静态资源和反向代理的前端网关。

仓库默认以 `docker compose` 作为本地启动和容器化部署入口。使用说明、部署步骤和运维操作以本文档为准；项目结构、接口边界和内部设计以 `CLAUDE.md` 为准。

## 2. 快速开始

### 前置要求

- Docker
- Docker Compose（`docker compose`）
- Node.js 20+（仅在本地单独构建前端时需要）
- Go 1.23+（仅在本地单独运行后端或执行后端测试时需要）

### 本地启动

```bash
cp .env.example .env
make config-sync
docker compose up -d --build
```

默认访问入口：

- 管理台：`http://localhost:11950/admin/`
- 后端服务：`http://localhost:11952`

查看状态与停止：

```bash
docker compose ps
docker compose down
```

### 常用本地命令

```bash
make backend-test
make frontend-build
make docker-build
make docker-up
make docker-down
```

### 密码 bcrypt 生成

服务启动后可通过接口生成：

```bash
curl -sS -X POST http://localhost:11952/admin/api/bcrypt/generate \
  -H 'Content-Type: application/json' \
  -d '{"password":"MyStrongPassword!123"}'
```

离线生成（macOS / Linux）：

```bash
htpasswd -nbBC 10 '' 'MyStrongPassword!123' | tr -d ':\n'; echo
```

将结果写入 `.env` 时建议使用单引号，避免 `$` 被 shell 展开。

## 3. 配置说明

- 环境变量契约以根目录 [`.env.example`](/Users/linlay-macmini/Project/zenmind-app-server/.env.example) 为准。
- 本地真实配置写入 `.env`，该文件不提交，且已由 [`.gitignore`](/Users/linlay-macmini/Project/zenmind-app-server/.gitignore) 忽略。
- 后端默认值内置于代码，不在 `README.md` 或 `CLAUDE.md` 重复维护。
- 托管可编辑配置文件注册表以 [`configs/config-files.yml`](/Users/linlay-macmini/Project/zenmind-app-server/configs/config-files.yml) 为准。
- 运行时注册表由 `make config-sync` 生成到 `configs/config-files.runtime.yml`，并同步更新 [`docker-compose.yml`](/Users/linlay-macmini/Project/zenmind-app-server/docker-compose.yml) 中的挂载块。

配置优先级：

- 无外部 yml：代码默认值 < 环境变量
- 托管外部文件场景：代码默认值 < 托管文件内容 < 环境变量（当同名配置同时存在时）

托管配置文件注册表字段：

- `id`：稳定标识，供 API 和前端页面使用
- `name`：管理台展示名称
- `type`：文件类型标签
- `sourcePath`：宿主机文件路径
- `containerPath`：挂载到 backend 容器后的路径

修改注册表后执行：

```bash
make config-sync
```

## 4. 部署

### 容器化部署

仓库默认部署方式为 `docker compose`：

```bash
cp .env.example .env
make config-sync
docker compose up -d --build
```

部署时注意：

- 敏感信息只通过 `.env` 或外部 Secret 注入，不写入仓库文档与镜像构建文件。
- [`docker-compose.yml`](/Users/linlay-macmini/Project/zenmind-app-server/docker-compose.yml) 只负责本地编排、端口和卷挂载，不维护业务默认值。
- JWK 相关初始化与导出脚本位于 [`release-scripts/README.md`](/Users/linlay-macmini/Project/zenmind-app-server/release-scripts/README.md)。

### 单独构建

```bash
make backend-build
make frontend-build
```

## 5. 运维

### 查看日志

```bash
docker compose logs -f backend
docker compose logs -f frontend
```

### 健康与接口检查

```bash
curl -i http://localhost:11952/openid/.well-known/openid-configuration
curl -i http://localhost:11952/openid/jwks
curl -i http://localhost:11952/admin/api/session/me
```

未登录时 `/admin/api/session/me` 返回 `401` 属于预期行为。

### 数据与配置文件位置

- SQLite 数据库默认挂载到 `./data/auth.db`
- backend 容器中的可编辑配置目录固定为 `/app/config`
- `configs/config-files.yml` 决定哪些宿主机文件会出现在 “Config Files” 页面中

### JWK 管理

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

### 常见排查

- 登录失败时，先检查 `.env` 中两个 bcrypt 是否为完整合法值。
- 若管理台看不到配置文件，检查 `configs/config-files.yml` 是否已更新并重新执行 `make config-sync`。
- 若访问 TTL 校验失败，检查请求值是否超过服务端允许上限，并确认 `.env` 是否覆盖了相关环境变量。
- 若后端无法启动，优先检查端口占用、数据库挂载目录和 `.env` 是否完整。
