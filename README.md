# zenmind-app-server

## 1. 项目简介

`zenmind-app-server` 是认证与管理服务，提供 OAuth2 / OIDC、管理后台、App 访问令牌和设备管理。

项目支持两种部署模式：Docker 容器编排（默认）和单二进制 Program Mode（用于桌面集成）。

Docker-first 部署契约：

- backend 固定只在容器网络内监听 `8080`
- frontend 对外暴露 `/admin/`
- release bundle 支持按版本离线部署
- 根目录 `.env.example` 只保留部署必要项
- 外部“受管配置文件”不再作为默认部署契约

## 2. 快速开始

```bash
cp .env.example .env
docker compose up -d --build
```

版本化离线发布包：

```bash
make release
```

说明：

- 统一使用 `make release`。
- `make release` 会先在宿主机完成 Go / npm 构建，再由 Docker 只把现成产物打进 release 镜像。
- `make release` 默认使用 `https://goproxy.cn,direct` 和 `https://registry.npmmirror.com` 作为宿主机构建依赖源。
- 若构建机需要代理，请配置宿主机上的 `go` / `npm` 访问环境；代理只是可选辅助，不再是 release 默认成功路径的前提。
- 离线的是部署端，不是构建端。首次执行发布时，打包机仍需要可用的 Docker / buildx、Go 和 npm，以及宿主机可访问所需依赖源或已有本地缓存。
- 如需官方源、私有源或其它自定义源，仍可显式传入 `GOPROXY`、`NPM_REGISTRY` 覆盖默认值。

默认入口：

- 管理台：`http://127.0.0.1:11950/admin/`

如需通过外层总网关接入，请保持：

- 管理台前缀：`/admin/`
- API 前缀：`/admin/api`
- OAuth2 / OIDC：`/oauth2`、`/openid`

## 3. 配置说明

- 环境变量契约以根目录 `.env.example` 为准
- `APP_SERVER_VERSION` 用于 release bundle 选择镜像标签
- 部署层只保留 `FRONTEND_PORT`；backend 不再暴露宿主机端口
- `BACKEND_PORT` 不再是有效部署变量；backend 容器内固定监听 `8080`
- `AUTH_ISSUER` 仍然必需，因为服务会用它生成 OIDC / OAuth2 metadata
- 两个 bcrypt 仍然必填，推荐在写入 `.env` 时保留单引号
- 数据默认挂载到 `./data`

## 4. 部署

- `compose.yml` 只负责双容器编排
- `make release` 会生成带版本号的离线 bundle 到 `dist/release/`
- backend 容器网络端口固定为 `8080`
- frontend 容器负责静态资源和反向代理
- 若由总网关接入，不要再单独公开 backend 端口
- 版本化打包说明见 `docs/versioned-release-bundle.md`

## 5. 运维

- 查看日志：`docker compose logs -f app-server-backend app-server-frontend`
- OIDC metadata：`curl -i http://127.0.0.1:11950/openid/.well-known/openid-configuration`
- bcrypt 生成接口：`POST /admin/api/bcrypt/generate`

## 6. 本地前端开发

- Vite 开发代理默认回落到 `http://localhost:8080`
- 如需改代理目标，只使用 `VITE_API_PROXY_TARGET`
- Docker 部署下仍只通过 frontend 网关访问 backend

## 7. 桌面集成（Program Mode）

```bash
make release-program
```

构建产物为单二进制文件，无需 Docker 即可运行，同时提供 API 服务和管理前端静态资源。

关键环境变量：

| 变量 | 说明 |
|---|---|
| `SERVER_PORT` | 服务监听端口 |
| `FRONTEND_DIST_DIR` | 管理前端静态资源目录 |
| `AUTH_DB_PATH` | 认证数据库文件路径 |

该 bundle 设计用于在 zenmind-desktop 中注册为内置服务（builtin service）。
