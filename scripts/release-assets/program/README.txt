zenmind-app-server — Program Bundle

本部署包用于宿主机双进程部署，不依赖 Docker。解压后会直接运行 backend 与 frontend-gateway。

部署步骤
========

1. 复制 .env.example 为 .env，填入真实配置值。
2. macOS / Linux 运行 ./start.sh；Windows 运行 start.cmd。
3. 浏览器访问 http://127.0.0.1:11950/admin/（实际端口取决于 .env 中的 FRONTEND_PORT）。
4. 停止服务时，macOS / Linux 运行 ./stop.sh；Windows 运行 stop.cmd。

目录说明
========

.env.example                  — 环境变量模板
README.txt                    — 本文件
backend/                      — backend 程序与 schema.sql
frontend/                     — frontend-gateway 与静态资源
config/config-files.runtime.yml — 运行时配置目录占位文件
data/                         — SQLite 与运行时数据目录
run/                          — 进程 pid 与日志目录

辅助脚本
========

setup-public-key.sh           — 导出 JWK 公私钥与 publicKey.pem
issue-bridge-access-token.sh  — 生成供 bridge 调用的 app access token
issue-bridge-runner-token.sh  — 生成供内部 bridge 调 runner 使用的带 exp app access token

注意事项
========

- backend 默认读取 ./data/auth.db 与 ./backend/schema.sql。
- frontend-gateway 默认代理到 http://127.0.0.1:8080，并从 ./frontend/dist 提供 /admin/ 静态资源。
- `config/` 目录默认只包含一个空的 runtime registry，占位供后续扩展。
- Windows bundle 提供原生 `start.cmd` / `stop.cmd`。
- 辅助脚本当前为 shell 版本；Windows 上请在 Git Bash 或 WSL 中执行。
- 显式生成 Linux program bundle 时，会额外附带 `zenmind-app-server.service` 示例文件。
