zenmind-app-server — 离线部署包

本文件只说明 bundle 解压后的最小操作。仓库里的发布约定、版本规则和打包细节请查看源码仓库文档。

部署步骤
========

1. 复制 .env.example 为 .env，填入真实配置值。
2. 运行 ./start.sh 启动服务。
3. 如需给外部 public key 服务提供 PEM，运行 ./setup-public-key.sh。
4. 如需给外部 bridge 调用方生成 app access token，运行 ./issue-bridge-access-token.sh。
5. 如需给内部 bridge 调 runner 签发带 exp 的长效 token，运行 ./issue-bridge-runner-token.sh。
6. 浏览器访问 http://127.0.0.1:11950/admin/（实际端口取决于 .env 中的 FRONTEND_PORT）。
7. 运行 ./stop.sh 停止服务。

目录说明
========

.env.example                  — 环境变量模板
compose.release.yml           — 容器编排
start.sh                      — 启动脚本（会按需加载 images/*.tar）
stop.sh                       — 停止脚本
setup-public-key.sh           — 导出 JWK 公私钥与 publicKey.pem
issue-bridge-access-token.sh  — 生成供 bridge 调用的 app access token
issue-bridge-runner-token.sh  — 生成供内部 bridge 调 runner 使用的带 exp app access token
README.txt                    — 本文件
data/                         — 运行时数据目录
images/                       — Docker 镜像 tar 文件

注意事项
========

- 需要 Docker Engine 20+ 和 docker compose v2。
- `setup-public-key.sh` 依赖 `openssl` 和 `sqlite3`；Windows 部署请在 WSL 中执行同一脚本。
- `issue-bridge-access-token.sh` 依赖 `openssl` 和 `sqlite3`，成功时只会向 stdout 输出 access token 本体。
- `issue-bridge-runner-token.sh` 依赖 `openssl` 和 `sqlite3`，成功时只会向 stdout 输出 `RUNNER_BEARER_TOKEN=...` 与 `RUNNER_BEARER_EXPIRES_AT=...`。
- .env 中的 APP_SERVER_VERSION 必须与镜像标签一致；打包产物中的 .env.example 已默认写入当前版本。
- 服务会在首次启动时自动初始化 SQLite 数据库，并在需要时自动创建 JWK。
- `./setup-public-key.sh` 默认会导出 `./data/keys/jwk-public.pem`、`./data/keys/jwk-private.pem` 和 `./data/keys/publicKey.pem`。
- `./issue-bridge-access-token.sh` 默认复用名为 `WeChat Bridge` 的 ACTIVE 设备，并生成一个不带 `exp` 的 app access token。
- `./issue-bridge-runner-token.sh` 默认复用名为 `WeChat Bridge` 的 ACTIVE 设备，并生成一个默认有效期为 10 年的带 `exp` app access token，同时写入对应过期时间的 token 审计记录。
- 管理台与网关入口固定为 /admin/。
