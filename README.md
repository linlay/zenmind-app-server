# zenmind-app-server

## 1. 项目简介

`zenmind-app-server` 是认证与管理服务，提供 OAuth2 / OIDC、管理后台、App 访问令牌和设备管理。

当前部署契约已经收敛为 Docker-first：

- backend 固定只在容器网络内监听 `8080`
- frontend 对外暴露 `/admin/`
- 根目录 `.env.example` 只保留部署必要项
- 外部“受管配置文件”不再作为默认部署契约

## 2. 快速开始

```bash
cp .env.example .env
docker compose up -d --build
```

默认入口：

- 管理台：`http://127.0.0.1:11950/admin/`

如需通过外层总网关接入，请保持：

- 管理台前缀：`/admin/`
- API 前缀：`/admin/api`
- OAuth2 / OIDC：`/oauth2`、`/openid`

## 3. 配置说明

- 环境变量契约以根目录 `.env.example` 为准
- 部署层只保留 `FRONTEND_PORT`；backend 不再暴露宿主机端口
- `BACKEND_PORT` 不再是有效部署变量；backend 容器内固定监听 `8080`
- `AUTH_ISSUER` 仍然必需，因为服务会用它生成 OIDC / OAuth2 metadata
- 两个 bcrypt 仍然必填，推荐在写入 `.env` 时保留单引号
- 数据默认挂载到 `./data`

## 4. 部署

- `compose.yml` 只负责双容器编排
- backend 容器网络端口固定为 `8080`
- frontend 容器负责静态资源和反向代理
- 若由总网关接入，不要再单独公开 backend 端口

## 5. 运维

- 查看日志：`docker compose logs -f app-server-backend app-server-frontend`
- OIDC metadata：`curl -i http://127.0.0.1:11950/openid/.well-known/openid-configuration`
- bcrypt 生成接口：`POST /admin/api/bcrypt/generate`

## 6. 本地前端开发

- Vite 开发代理默认回落到 `http://localhost:8080`
- 如需改代理目标，只使用 `VITE_API_PROXY_TARGET`
- Docker 部署下仍只通过 frontend 网关访问 backend
