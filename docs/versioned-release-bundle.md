# 版本化离线打包方案

## 1. 目标与边界

当前仓库新增一套带明确版本号、单目标架构、可离线部署的 release bundle 方案，方便把最终可运行版本上传到 GitHub Release、自建制品库或内网服务器，再由部署端直接解压运行。

这套方案解决的是“如何交付可运行版本”，不是“如何分发源码”：

- 交付物是最终 bundle，而不是源码压缩包。
- bundle 内包含预构建镜像和最小部署资产，部署端不需要源码构建环境。
- 每次构建只产出一个目标架构 bundle，不做多架构合包。

当前仓库的版本单一来源是根目录 `VERSION` 文件，正式版本格式固定为 `vX.Y.Z`。例如版本为 `v0.1.0` 时，最终产物命名规则为：

- `zenmind-app-server-v0.1.0-linux-arm64.tar.gz`
- `zenmind-app-server-v0.1.0-linux-amd64.tar.gz`

## 2. 方案总览

整个发布流程分成四层：

1. 版本层：根目录 `VERSION` 统一管理版本号。
2. 构建层：按目标架构构建 backend 和 frontend release 镜像。
3. 组装层：把镜像 tar、compose 文件、启停脚本、README、`.env.example` 组装成离线目录。
4. 交付层：把离线目录压缩成最终 bundle，输出到 `dist/release/`。

在当前仓库里，上面四层分别落在这些位置：

- 版本来源：`VERSION`
- 构建入口：`make release` / `scripts/release.sh`
- 模板资产：`scripts/release-assets/`
- 最终产物目录：`dist/release/`

## 3. 当前项目如何打包

### 3.1 打包入口

一步式正式发布入口：

```bash
make release
```

`Makefile` 会把 `VERSION` 和 `ARCH` 传给 `scripts/release.sh`：

```bash
VERSION=$(VERSION) ARCH=$(ARCH) bash scripts/release.sh
```

也可以直接执行脚本：

```bash
bash scripts/release.sh
```

常见用法：

```bash
make release VERSION=v1.0.0 ARCH=arm64
make release VERSION=v1.0.0 ARCH=amd64
```

其中：

- `VERSION` 默认读取根目录 `VERSION`
- `ARCH` 未显式传入时，会按 `uname -m` 自动识别为 `amd64` 或 `arm64`
- 脚本内部把 `ARCH` 转成 `linux/<arch>` 作为 Docker buildx 目标平台
- 如需让构建期的 `go mod download`、`npm ci` 等走代理，请在执行 `make release` 的同一 shell 里先 `export http_proxy` / `https_proxy` / `no_proxy`

### 3.2 打包输入

`scripts/release.sh` 的主要输入包括：

- 版本号：`VERSION` 文件或环境变量 `VERSION`
- 目标架构：环境变量 `ARCH` 或当前机器架构
- 打包机环境：可用的 Docker Engine、docker buildx、Go、npm，以及宿主机访问依赖源所需的网络或本地缓存
- 可覆盖构建参数：`GOPROXY`、`NPM_REGISTRY`
- release 容器打包定义：`backend/Dockerfile.release`、`frontend/Dockerfile.release`
- release 模板资产：`scripts/release-assets/compose.release.yml`
- release 模板资产：`scripts/release-assets/start.sh`
- release 模板资产：`scripts/release-assets/stop.sh`
- release 模板资产：`scripts/release-assets/README.txt`
- 配置模板：`.env.example`

脚本还会强校验版本格式：

- 只接受 `vX.Y.Z`
- 不符合时直接失败，不继续构建

### 3.3 构建过程

打包脚本先在宿主机构建 release 产物，再打包两个 release 镜像：

- backend Linux 二进制：`app`
- frontend gateway Linux 二进制：`frontend-gateway`
- frontend 静态资源：`dist/`

之后再构建两个 release 镜像：

- 后端镜像：`app-server-backend:<VERSION>`
- 前端镜像：`app-server-frontend:<VERSION>`

镜像构建阶段只复制宿主机已经生成的产物，不再在 Docker build 中执行 `go mod download`、`go build` 或 `npm ci`。最终仍由 `docker buildx build` 直接导出为镜像 tar：

- `images/app-server-backend.tar`
- `images/app-server-frontend.tar`

宿主机构建阶段会按 `ARCH=amd64|arm64` 生成对应 Linux 目标产物，并默认使用这些依赖源：

- `GOPROXY=https://goproxy.cn,direct`
- `NPM_REGISTRY=https://registry.npmmirror.com`

如果调用时显式传入同名环境变量，则以显式值为准。因此也可以按需切回官方源或私有源。

### 3.4 组装过程

镜像构建完成后，脚本会在临时目录组装一个标准离线目录 `zenmind-app-server/`，然后拷入：

- `images/app-server-backend.tar`
- `images/app-server-frontend.tar`
- `compose.release.yml`
- `start.sh`
- `stop.sh`
- `setup-public-key.sh`
- `README.txt`
- `.env.example`
- 空的 `data/` 目录

同时脚本会把 bundle 内 `.env.example` 的 `APP_SERVER_VERSION` 改成当前构建版本，保证部署端复制 `.env.example` 后，默认镜像标签与 bundle 内镜像一致。

### 3.5 最终输出

最终交付物位于：

```text
dist/release/zenmind-app-server-vX.Y.Z-linux-<arch>.tar.gz
```

这就是对外分发的正式部署包。

## 4. bundle 里有什么

bundle 解压后目录大致如下：

```text
zenmind-app-server/
  .env.example
  compose.release.yml
  start.sh
  stop.sh
  setup-public-key.sh
  README.txt
  images/
    app-server-backend.tar
    app-server-frontend.tar
  data/
```

和开发态仓库不同，release bundle 不默认携带：

- `configs/config-files.yml`
- 外部可编辑配置文件挂载能力

这些能力仍保留在源码仓库里，但不属于首版默认离线部署模型。

## 5. 部署端如何消费这些包

标准部署步骤：

```bash
tar -xzf zenmind-app-server-v1.0.0-linux-amd64.tar.gz
cd zenmind-app-server
cp .env.example .env
./start.sh
```

如需给外部 public key 服务提供 PEM，可直接执行：

```bash
./setup-public-key.sh
```

默认会导出：

- `./data/keys/jwk-public.pem`
- `./data/keys/jwk-private.pem`
- `./data/keys/publicKey.pem`

`start.sh` 会按顺序完成：

1. 校验 `.env` 是否存在。
2. 校验宿主机上有 Docker Engine 和 docker compose v2。
3. 从 `.env` 读取 `APP_SERVER_VERSION` 和 `FRONTEND_PORT`。
4. 如果本机没有 `app-server-backend:$APP_SERVER_VERSION` 或 `app-server-frontend:$APP_SERVER_VERSION`，就从 `images/*.tar` 自动执行 `docker load`。
5. 创建 `data/` 目录。
6. 执行 `docker compose -f compose.release.yml up -d`。

启动完成后，默认浏览器入口为：

```text
http://127.0.0.1:${FRONTEND_PORT}/admin/
```

### 5.1 `compose.release.yml` 的角色

release compose 和开发 compose 的思路不同：

- 它只引用预构建镜像
- 它不在部署端执行 `build`
- 它不依赖外部 `zenmind-network`
- 它只挂载运行所需的 `./data:/data`

其中：

- backend 使用 `app-server-backend:${APP_SERVER_VERSION:-latest}`
- frontend 使用 `app-server-frontend:${APP_SERVER_VERSION:-latest}`

### 5.2 `stop.sh` 做了什么

`stop.sh` 使用同一份 compose 文件执行：

```bash
docker compose -f compose.release.yml down --remove-orphans
```

它的职责很单纯：停止由 bundle 启动的 release 容器。

### 5.3 `setup-public-key.sh` 做了什么

`setup-public-key.sh` 是 release bundle 自带的 JWK 导出脚本，适用于 macOS、Linux 和 WSL。它会：

1. 确保 SQLite 里的 `JWK_KEY_` 存在可用 key。
2. 在默认目录 `./data/keys/` 导出 `jwk-public.pem` 和 `jwk-private.pem`。
3. 额外复制一份 `publicKey.pem` 供外部服务直接消费。

默认用法：

```bash
./setup-public-key.sh
```

如需轮换 key：

```bash
./setup-public-key.sh --mode rotate
```

依赖要求：

- `openssl`
- `sqlite3`

注意：

- Windows 部署统一通过 WSL 执行该脚本，不再提供 PowerShell 版本。
- 轮换 key 会使之前签发的 app access token 失效，执行后应重启 backend。

## 6. 升级、回滚与交付建议

升级时，建议下载新版本 bundle，解压到新目录后复用旧目录里的：

- `.env`
- `data/`

然后执行新的 `./start.sh`。这样既切换了镜像版本，也保留了配置和数据库。

回滚时，停止当前版本后，切回上一版 bundle 目录，再执行上一版的 `./start.sh` 即可。

这个模型成立的前提是：

- 每个版本都有独立 bundle
- `APP_SERVER_VERSION` 和镜像标签保持一致
- 数据目录可以在相邻版本间复用

## 7. 宿主机构建说明

默认情况下，release 会直接使用 `goproxy.cn` 和 `registry.npmmirror.com` 作为宿主机构建依赖源。如果构建机访问外网仍需要代理，请配置宿主机上的 Go / npm 访问环境，再执行 `make release`。例如：

```bash
export http_proxy=http://127.0.0.1:8001
export https_proxy=http://127.0.0.1:8001
export no_proxy=127.0.0.1,localhost
make release
```

也可以按需覆盖宿主机使用的依赖源，例如切回官方源：

```bash
make release \
  GOPROXY=https://proxy.golang.org,direct \
  NPM_REGISTRY=https://registry.npmjs.org
```

或者切到自定义私有源：

```bash
make release \
  GOPROXY=https://your.goproxy,direct \
  NPM_REGISTRY=https://your.npm.registry
```

若只想检查当前 release 会使用哪些关键参数，可执行：

```bash
RELEASE_DRY_RUN=1 make release
```

## 8. 复用到其他项目时建议保留的骨架

如果要把这套模式迁移到其他仓库，建议至少保留：

- `VERSION`
- `scripts/release.sh`
- `scripts/release-assets/`
- `dist/release/`
- `.env.example`

以及这些流程约束：

- 版本号来自单一来源
- 每次只产出一个目标架构包
- 必须先构建 release 镜像，再导出为 tar
- 必须把镜像 tar 和部署资产一起打进 bundle
- 部署端只依赖 Docker / docker compose，不依赖源码构建工具链

## 9. 当前项目关键文件索引

- `VERSION`
- `Makefile`
- `scripts/release.sh`
- `scripts/release-assets/compose.release.yml`
- `scripts/release-assets/start.sh`
- `scripts/release-assets/stop.sh`
- `scripts/release-assets/README.txt`
- `.env.example`

如果后续这套方案继续演进，优先更新这些文件和本文档，避免 README、脚本和实际交付行为出现偏差。
