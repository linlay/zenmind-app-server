zenmind-app-server — Program Bundle

本部署包用于宿主机程序部署，不依赖 Docker。
前端静态资源位于 `frontend/dist`，反向代理配置位于 `frontend/nginx.conf`，宿主机器需要预装 nginx。

部署步骤
========

1. 复制 .env.example 为 .env，填入真实配置值。
2. 运行 `./deploy.sh`，确认 nginx、frontend/dist、backend/app 等运行条件已就绪。
3. macOS / Linux 运行 `./start.sh`；Windows 运行 `./start.ps1`。
4. 浏览器访问 `http://127.0.0.1:11950/admin/`（实际端口取决于 `.env` 中的 `FRONTEND_PORT`）。
5. 停止服务时，macOS / Linux 运行 `./stop.sh`；Windows 运行 `./stop.ps1`。

目录说明
========

.env.example                  — 环境变量模板
manifest.json                 — bundle 清单文件
README.txt                    — 本文件
start.sh / stop.sh / deploy.sh
start.ps1 / stop.ps1 / deploy.ps1
backend/                      — backend 程序
frontend/                     — nginx 配置模板与静态资源
configs/                      — 运行时配置样例
scripts/                      — 辅助脚本与公共脚本
data/                         — SQLite 与运行时数据目录
run/                          — 运行期 pid、日志与 nginx prefix 目录

辅助脚本
========

scripts/setup-public-key.sh           — 导出 JWK 公私钥与 publicKey.pem
scripts/issue-bridge-access-token.sh  — 生成供 bridge 调用的 app access token
scripts/issue-bridge-runner-token.sh  — 生成供内部 bridge 调 runner 使用的带 exp app access token

注意事项
========

- backend 默认读取 `./data/auth.db`，数据库 schema 已内嵌到二进制。
- nginx 默认代理到 `http://127.0.0.1:${SERVER_PORT}`，并从 `frontend/dist` 提供 `/admin/` 静态资源。
- Program Bundle 不内置 nginx，需要宿主机器预装 nginx，并保证 `nginx` 命令可用；如不在 PATH，可在 `.env` 中设置 `NGINX_BIN`。
- 根目录 `start.sh` / `stop.sh` / `deploy.sh` 为标准入口；Windows 对应入口为 `start.ps1` / `stop.ps1` / `deploy.ps1`。
- `scripts/` 下辅助脚本依赖 `openssl` 和 `sqlite3`。
