zenmind-app-server — 离线部署包

本文件只说明 bundle 解压后的最小操作。仓库里的发布约定、版本规则和打包细节请查看源码仓库文档。

部署步骤
========

1. 复制 .env.example 为 .env，填入真实配置值。
2. 运行 ./start.sh 启动服务。
3. 如需给外部 public key 服务提供 PEM，运行 ./setup-public-key.sh。
4. 浏览器访问 http://127.0.0.1:11950/admin/（实际端口取决于 .env 中的 FRONTEND_PORT）。
5. 运行 ./stop.sh 停止服务。

目录说明
========

.env.example                  — 环境变量模板
compose.release.yml           — 容器编排
start.sh                      — 启动脚本（会按需加载 images/*.tar）
stop.sh                       — 停止脚本
setup-public-key.sh           — 导出 JWK 公私钥与 publicKey.pem
README.txt                    — 本文件
data/                         — 运行时数据目录
images/                       — Docker 镜像 tar 文件

注意事项
========

- 需要 Docker Engine 20+ 和 docker compose v2。
- `setup-public-key.sh` 依赖 `openssl` 和 `sqlite3`；Windows 部署请在 WSL 中执行同一脚本。
- .env 中的 APP_SERVER_VERSION 必须与镜像标签一致；打包产物中的 .env.example 已默认写入当前版本。
- 服务会在首次启动时自动初始化 SQLite 数据库，并在需要时自动创建 JWK。
- `./setup-public-key.sh` 默认会导出 `./data/keys/jwk-public.pem`、`./data/keys/jwk-private.pem` 和 `./data/keys/publicKey.pem`。
- 管理台与网关入口固定为 /admin/。
