# 版本化 Release Bundle 方案

## 1. 总览

当前仓库的 release 已统一整理为两条产线：

1. Program Bundle：宿主机程序部署包
2. Image Bundle：容器镜像离线部署包

两类产物统一输出到 `dist/release/`，正式版本统一来自根目录 `VERSION`，格式固定为 `vX.Y.Z`。

入口命令：

```bash
make release
make release-program
make release-image
```

默认行为：

- `make release` 等价于 `make release-program`
- `make release-program` 默认产出：
  - `darwin/arm64`
  - `windows/amd64`
- `make release-image` 默认产出：
  - `linux/<当前宿主机架构>`

## 2. 实现位置

- 版本来源：`VERSION`
- Make 入口：`Makefile`
- 通用逻辑：`scripts/release-common.sh`
- Program 产线：`scripts/release-program.sh`
- Image 产线：`scripts/release-image.sh`
- 兼容入口：`scripts/release.sh`
- Program 资产：`scripts/release-assets/program/`
- Image 资产：`scripts/release-assets/image-bundle/`
- 最终输出目录：`dist/release/`

## 3. Program Bundle

### 3.1 目标解析规则

Program 产线按以下优先级解析目标矩阵：

1. `PROGRAM_TARGET_MATRIX=os/arch,os/arch`
2. `PROGRAM_TARGETS=os[,os...]` + `ARCH=...`
3. 默认矩阵 `darwin/arm64,windows/amd64`

允许值：

- OS：`darwin|windows|linux`
- ARCH：`amd64|arm64`

常见命令：

```bash
make release VERSION=v1.0.0
PROGRAM_TARGET_MATRIX=darwin/arm64,windows/amd64 make release-program VERSION=v1.0.0
PROGRAM_TARGETS=windows ARCH=amd64 make release-program VERSION=v1.0.0
PROGRAM_TARGET_MATRIX=linux/amd64 make release-program VERSION=v1.0.0
```

### 3.2 Program 产物命名

```text
dist/release/zenmind-app-server-program-vX.Y.Z-<os>-<arch>.tar.gz
```

示例：

- `dist/release/zenmind-app-server-program-v1.0.0-darwin-arm64.tar.gz`
- `dist/release/zenmind-app-server-program-v1.0.0-windows-amd64.tar.gz`
- `dist/release/zenmind-app-server-program-v1.0.0-linux-amd64.tar.gz`

### 3.3 Program Bundle 内容

```text
zenmind-app-server/
  .env.example
  README.txt
  backend/
    app | app.exe
    schema.sql
  frontend/
    frontend-gateway | frontend-gateway.exe
    dist/
  config/
    config-files.runtime.yml
  data/
  run/
  start.sh | start.cmd
  stop.sh | stop.cmd
  setup-public-key.sh
  issue-bridge-access-token.sh
  issue-bridge-runner-token.sh
  zenmind-app-server.service   # 仅显式 Linux program bundle
```

约定：

- backend 默认读取 `./data/auth.db` 与 `./backend/schema.sql`
- frontend-gateway 默认代理到 `http://127.0.0.1:8080`
- frontend 静态资源目录固定为 `./frontend/dist`
- `config/` 目录默认只包含空的 runtime registry 占位文件
- Windows bundle 使用 `start.cmd` / `stop.cmd`
- Linux 专属 `systemd` 文件只在显式打 Linux program bundle 时附带
- Windows bundle 的辅助脚本当前仍为 shell 版本，建议在 Git Bash 或 WSL 中执行

## 4. Image Bundle

### 4.1 默认行为

- 入口命令：`make release-image`
- 只构建 Linux 镜像
- 默认使用当前宿主机架构
- 不做多架构合包
- 使用 `docker buildx build --platform linux/<arch>` 构建 release 镜像
- 使用 `docker save` 导出并压缩成单个镜像归档

### 4.2 Image 产物命名

最终 bundle：

```text
dist/release/zenmind-app-server-image-bundle-vX.Y.Z-linux-<arch>.tar.gz
```

bundle 内镜像归档：

```text
images/zenmind-app-server-image-vX.Y.Z-linux-<arch>.tar.gz
```

### 4.3 Image Bundle 内容

```text
zenmind-app-server/
  .env.example
  README.txt
  compose.release.yml
  load-image.sh
  start.sh
  stop.sh
  setup-public-key.sh
  issue-bridge-access-token.sh
  issue-bridge-runner-token.sh
  config/
    config-files.runtime.yml
  data/
  images/
    zenmind-app-server-image-vX.Y.Z-linux-<arch>.tar.gz
```

`start.sh` 会在镜像不存在时自动调用 `load-image.sh`，然后执行 `docker compose -f compose.release.yml up -d`。

## 5. 使用示例

Program Bundle：

```bash
tar -xzf dist/release/zenmind-app-server-program-v1.0.0-darwin-arm64.tar.gz
cd zenmind-app-server
cp .env.example .env
./start.sh
```

Image Bundle：

```bash
tar -xzf dist/release/zenmind-app-server-image-bundle-v1.0.0-linux-arm64.tar.gz
cd zenmind-app-server
cp .env.example .env
./load-image.sh
./start.sh
```

默认浏览器入口：

```text
http://127.0.0.1:${FRONTEND_PORT}/admin/
```

## 6. 常见命令

```bash
make release VERSION=v1.0.0
make release-program VERSION=v1.0.0
PROGRAM_TARGET_MATRIX=darwin/arm64,windows/amd64 make release-program VERSION=v1.0.0
PROGRAM_TARGETS=windows ARCH=amd64 make release-program VERSION=v1.0.0
make release-image VERSION=v1.0.0
RELEASE_DRY_RUN=1 make release-program
RELEASE_DRY_RUN=1 make release-image
```

## 7. 注意事项

- Program Bundle 与 Image Bundle 共用根目录 `VERSION` 作为正式版本源
- `make release` 保留兼容入口，不再直接构建 image bundle
- 当前仓库仍兼容旧代码里的可编辑配置文件能力，但 release bundle 默认只包含最小 `config/` 占位目录
- 辅助脚本默认依赖 `openssl` 和 `sqlite3`
- Program Bundle 构建需要 Go 与 npm；Image Bundle 额外需要 Docker / buildx
