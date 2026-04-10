# Go 全栈子服务 Program Bundle 运行时规范

## 1. 设计原则

Program Bundle 是“已经完成前端构建和后端编译、可直接交付宿主加载和运行的独立子服务运行包”。

这份规范只讨论编译完成后的运行时交付物，不讨论源码工程结构、插件机制、宿主源码实现或固定挂载目录规则。

设计原则如下：

- 只从编译后产物视角描述 bundle
- 只保留运行时必需文件和可选运行资源
- 子服务只理解运行时配置，不理解 plugin 概念
- 子服务不硬编码宿主路径规则
- 宿主路径与接入方式通过运行时配置注入
- 每个环境独立打包，一个环境一个 bundle
- bundle 是运行时交付物，不是源码仓库镜像

补充说明：

- Program Bundle 是宿主直接加载和运行的程序包
- Image Bundle 是另一类交付物，用于容器镜像分发；它不是本文重点，本文不展开其规范细节

## 2. 最小可运行 bundle 结构

最小可运行结构只包含运行时必需内容：

```text
<bundle-root>/
  manifest.json
  .env.example
  start.sh
  stop.sh
  deploy.sh
  frontend/
    nginx.conf
    dist/
      index.html
      assets/
  backend/
    app | app.exe
  configs/
    runtime.env.example
```

必须项：

- `manifest.json`
- `.env.example`
- `start.sh`
- `stop.sh`
- `deploy.sh`
- `frontend/nginx.conf`
- `frontend/dist/index.html`
- `frontend/dist/assets/`
- `backend/app` 或 `backend/app.exe`

可选项：

- `start.ps1`、`stop.ps1`、`deploy.ps1`
- `frontend/dist/favicon.ico`
- `frontend/dist` 下其他静态资源
- `configs/runtime.env.example`

职责说明：

- `manifest.json`：宿主识别和加载该 bundle 的描述文件
- `.env.example`：部署注入模板，承载宿主接入信息和运行时参数样例
- `start.sh` / `stop.sh` / `deploy.sh`：标准启动、停止和部署检查入口
- `frontend/nginx.conf`：Program Bundle 使用的 nginx 配置模板
- `frontend/dist/`：前端静态构建产物目录
- `backend/app` / `backend/app.exe`：后端主程序，可直接启动
- `configs/runtime.env.example`：运行时环境变量样例

## 3. 推荐完整运行时 bundle 结构

推荐完整结构仍然只包含运行时内容，但允许携带更多运行依赖：

```text
<bundle-root>/
  manifest.json
  README.txt
  .env.example
  start.sh
  stop.sh
  deploy.sh
  start.ps1
  stop.ps1
  deploy.ps1
  scripts/
    program-common.sh
    program-common.ps1
    setup-public-key.sh
    issue-bridge-access-token.sh
    issue-bridge-runner-token.sh
  frontend/
    nginx.conf
    dist/
      index.html
      assets/
        ...
      favicon.ico
      ...
  backend/
    app | app.exe
  configs/
    runtime.env.example
  data/
  run/
```

结构约束：

- 根目录必须包含 `start.sh`、`stop.sh`、`deploy.sh`
- Windows bundle 额外包含 `start.ps1`、`stop.ps1`、`deploy.ps1`
- 额外运行辅助脚本统一放在根目录 `scripts/`
- Program Bundle 包含 `frontend/nginx.conf`
- 前端始终以 `frontend/dist/` 静态产物存在
- 后端始终以 `backend/app` 或 `backend/app.exe` 存在
- `logs/` 不应预置在 bundle 中，应由宿主或部署系统在运行期创建
- `data/` 和 `run/` 允许随 bundle 一起交付，但只用于运行期状态和初始化数据

推荐目录职责：

- `README.txt`：面向部署和运维的最小交付说明
- `start.sh` / `stop.sh` / `deploy.sh`：macOS / Linux 标准入口
- `start.ps1` / `stop.ps1` / `deploy.ps1`：Windows 标准入口
- `scripts/`：根目录额外运行辅助脚本目录
- `frontend/nginx.conf`：nginx 配置模板，由部署脚本渲染运行时配置
- `frontend/dist/`：完整前端静态资源
- `backend/app` / `backend/app.exe`：后端主程序
- `configs/runtime.env.example`：运行时环境变量样例
- `data/`：SQLite 和初始化数据目录
- `run/`：pid、渲染后的 nginx 配置和运行期日志目录

## 4. 每个目录和关键文件说明

### 根目录

| 路径 | 运行时必需 | 建议入包 | 前端读取 | 后端读取 | 宿主读取 | 可按环境变化 | 建议外置或注入 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `manifest.json` | 是 | 是 | 否 | 否 | 是 | 是 | 否 | 宿主识别和加载 bundle 的描述文件 |
| `README.txt` | 否 | 是 | 否 | 否 | 可选 | 是 | 否 | 交付说明、启动说明、已知约束 |
| `.env.example` | 是 | 是 | 间接 | 间接 | 是 | 是 | 宿主注入 `.env` | 部署注入模板，是运行时配置主载体之一 |
| `start.sh` | 是 | 是 | 否 | 否 | 可选 | 可选 | 否 | 启动脚本，属于标准交付内容 |
| `stop.sh` | 是 | 是 | 否 | 否 | 可选 | 可选 | 否 | 停止脚本，属于标准交付内容 |
| `deploy.sh` | 是 | 是 | 否 | 否 | 可选 | 可选 | 否 | 部署前校验与初始化脚本，属于标准交付内容 |
| `start.ps1` / `stop.ps1` / `deploy.ps1` | 否 | Windows 建议 | 否 | 否 | 可选 | 可选 | 否 | Windows 平台的标准入口 |
| `scripts/` | 否 | 是 | 否 | 可选 | 可选 | 可选 | 否 | 根目录额外运行辅助脚本目录 |

### `frontend/`

| 路径 | 运行时必需 | 建议入包 | 前端读取 | 后端读取 | 宿主读取 | 可按环境变化 | 建议外置或注入 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `frontend/nginx.conf` | 是 | 是 | 否 | 否 | 是 | 是 | 可由部署脚本渲染 | Program Bundle 使用的 nginx 配置模板 |
| `frontend/dist/index.html` | 是 | 是 | 是 | 否 | 是 | 可选 | 否 | 前端入口页面 |
| `frontend/dist/assets/` | 是 | 是 | 是 | 否 | 是 | 可选 | 否 | JS、CSS 及其他构建产物目录 |
| `frontend/dist/favicon.ico` | 否 | 建议 | 是 | 否 | 是 | 可选 | 否 | 站点图标 |
| `frontend/dist/runtime-config.json` | 否 | 不作为主方案 | 可选 | 否 | 可选 | 是 | 可由宿主生成 | 可选运行时配置文件，不是本规范推荐主载体 |
| `frontend/dist` 下图片/字体/图标等资源 | 按需 | 建议 | 是 | 否 | 是 | 可选 | 否 | 页面运行所需静态资源 |

### `backend/` 与运行时目录

| 路径 | 运行时必需 | 建议入包 | 前端读取 | 后端读取 | 宿主读取 | 可按环境变化 | 建议外置或注入 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `backend/app` / `backend/app.exe` | 是 | 是 | 否 | 否 | 是 | 可按平台变化 | 否 | 后端主可执行文件 |
| `configs/runtime.env.example` | 否 | 是 | 否 | 间接 | 可选 | 是 | 否 | 运行时环境变量样例文件 |
| `data/` | 否 | 按需 | 否 | 是 | 可选 | 是 | 业务数据更建议外置 | SQLite 和初始化数据目录 |
| `run/` | 否 | 按需 | 否 | 否 | 可选 | 是 | 应由部署脚本创建 | pid、渲染后的 nginx 配置和日志目录 |
| `logs/` | 否 | 不建议单独预置 | 否 | 否 | 可选 | 是 | 应由宿主创建 | 日志属于运行期状态目录，不建议单独预置在 bundle 中 |

## 5. manifest.json 规范

`manifest.json` 是宿主识别和加载该 bundle 的描述文件，不是子服务的业务配置文件。

最小示例：

```json
{
  "id": "app1",
  "name": "App-One",
  "version": "0.1.0",
  "frontend": {
    "dist": "frontend/dist",
    "index": "index.html",
    "spa": true
  },
  "api": {
    "enabled": true
  },
  "backend": {
    "entry": "backend/app"
  }
}
```

Windows 示例：

```json
{
  "id": "app1",
  "name": "App-One",
  "version": "0.1.0",
  "frontend": {
    "dist": "frontend/dist",
    "index": "index.html",
    "spa": true
  },
  "api": {
    "enabled": true
  },
  "backend": {
    "entry": "backend/app.exe"
  }
}
```

强制约束：

- manifest 中所有路径均相对于 bundle 根目录
- `backend.entry` 指向编译后的二进制，而不是源码入口
- `manifest.json` 只用于宿主接入，不承载复杂业务配置
- `id` 是宿主识别 bundle 的标识，不要求子服务理解其业务语义
- `frontend.spa=true` 表示宿主可对前端页面启用 history fallback
- `api.enabled=true` 表示宿主应为该 bundle 启用 API 路由能力

## 6. manifest 字段说明表格

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 是 | 宿主用于标识该 bundle 的唯一标识 |
| `name` | string | 是 | 人类可读的服务名称 |
| `version` | string | 是 | 当前 bundle 的版本号 |
| `frontend.dist` | string | 是 | 前端静态目录，相对 bundle 根目录 |
| `frontend.index` | string | 是 | 前端入口文件名，相对 `frontend.dist` |
| `frontend.spa` | boolean | 是 | 是否按 SPA 处理，决定宿主是否可以启用 history fallback |
| `api.enabled` | boolean | 是 | 是否需要宿主为该服务提供 API 路由能力 |
| `backend.entry` | string | 是 | 后端可执行文件路径，相对 bundle 根目录，如 `backend/app` 或 `backend/app.exe` |

## 7. `basePath` / `apiBasePath` / `baseUrl` 说明

对子服务本身来说，它不需要理解宿主是不是 plugin，也不需要理解宿主内部扫描规则。它只需要理解以下三个抽象运行时概念。

### `basePath`

`basePath` 表示前端页面和静态资源对外暴露时的挂载基路径。

示例：

- `/app/`
- `/console/app-a/`
- `/embedded/service1/`

作用：

- 作为前端静态资源引用的基路径
- 作为前端 Router 的 basename 或 base
- 作为页面内跳转和相对资源路径的基准

### `apiBasePath`

`apiBasePath` 表示前端访问后端 API 时使用的统一前缀。

示例：

- `/app/api/`
- `/console/app-a/api/`

作用：

- 作为前端发起 API 请求时的统一入口
- 由宿主将该路径转发到子服务后端

### `baseUrl`

`baseUrl` 表示对外完整访问地址，仅在需要生成绝对链接时使用。

示例：

- `http://127.0.0.1:18400/app/`
- `http://localhost:3000/console/app-a/`

作用：

- 生成绝对链接
- 生成回调地址
- 生成分享地址
- 生成邮件或通知中的完整跳转地址

规范要求：

- 子服务只关心 `basePath`、`apiBasePath`、`baseUrl`
- 子服务不需要理解 plugin 概念
- 子服务不需要理解宿主内部扫描规则
- 子服务不应该硬编码具体挂载路径
- 这些值应由环境配置、运行时配置或宿主注入

本规范中的主配置载体只有两类：

- 根目录 `.env` / `.env.example`
- `configs/runtime.env.example` 及其生成出的实际运行配置

`frontend/dist/runtime-config.json` 可以作为可选实现手段，但不属于本规范推荐主方案。

## 8. 前端运行时产物要求

前端在 bundle 中只保留编译后的静态构建产物，不保留任何开发期目录或源码结构。

规范要求：

- 前端入口通常是 `frontend/dist/index.html`
- 所有静态资源都应位于 `frontend/dist/` 内
- 前端必须支持通过运行时配置使用动态 `basePath`
- 前端 Router 必须支持基于 `basePath` 运行
- 前端调用 API 时必须通过 `apiBasePath`
- 前端页面路由不能与 API 前缀冲突
- 当前端为 SPA 时，宿主可根据 `manifest.json` 中的 `frontend.spa` 决定是否回退到 `index.html`
- Program Bundle 应同时交付 `frontend/nginx.conf` 作为静态资源和 API 转发的配置模板

文件说明：

- `frontend/nginx.conf`：Program Bundle 使用的 nginx 配置模板
- `index.html`：页面入口，负责加载前端资源并接收运行时注入信息
- `assets/*.js`：前端逻辑代码构建产物
- `assets/*.css`：前端样式构建产物
- `favicon.ico`：页面图标，可选
- `runtime-config.json`：可选运行时配置文件，可承载 `basePath`、`apiBasePath`、`baseUrl` 等值，但本规范不把它作为主方案
- 图片、字体、图标等资源文件：页面展示和交互所需静态资源

前端接收运行时配置的常见方式：

- 宿主在页面加载前注入全局变量
- 部署阶段模板化生成前端配置
- 后端在启动阶段生成前端可读取的配置内容
- 使用 `runtime-config.json` 作为可选落地形式

## 9. 后端运行时产物要求

后端在 bundle 中只包含编译后的可执行文件及其运行依赖，不包含开发期源码结构。

规范要求：

- `backend/` 目录中应只包含编译后的可执行文件及其运行依赖
- 后端主程序应是可直接启动的二进制
- 后端默认只监听 `127.0.0.1`
- 后端端口应由宿主、部署系统或运行配置决定
- 后端不负责托管前端页面
- 后端只负责业务 API 和服务逻辑
- 后端通常不需要理解宿主外部挂载路径
- 如果后端需要生成对外 URL，则应通过运行时配置读取 `baseUrl` 或相关参数，而不是硬编码
- 后端自己的内部路由可以是纯业务路由，不必内置宿主前缀概念

运行时目录说明：

- `app` / `app.exe`：后端主程序，必须项
- `configs/runtime.env.example`：运行时环境变量样例，建议进入 bundle
- `data/`：用于 SQLite 或初始化数据，不建议承载与 bundle 生命周期无关的业务状态数据
- `run/`：用于 pid、渲染后的 nginx 配置和日志
- `logs/`：不建议单独进入 bundle，应由宿主或部署系统在运行期创建

## 10. 按环境打包规范

运行时 bundle 必须按环境独立打包，每个环境一个 bundle。

示例：

- `app1-dev-bundle.zip`
- `app1-test-bundle.zip`
- `app1-staging-bundle.zip`
- `app1-prod-bundle.zip`
- `app1-dev-windows-x64.zip`
- `app1-prod-linux-x64.tar.gz`

按环境拆分的原因：

- 前端运行时配置可能不同
- 后端运行时配置可能不同
- 开关项、日志级别、回调地址可能不同
- 证书、初始化数据、外部依赖地址可能不同
- 独立 bundle 更利于审计、回滚、追踪和发布管理

建议：

- 一个 bundle 只服务一个环境
- 不建议把多套环境配置全部塞进同一个 bundle 再靠启动参数切换
- 环境差异应尽量在打包阶段固化

## 11. bundle 命名建议

推荐命名方式至少包含以下维度：

- 服务名
- 环境
- 版本号
- 平台
- 架构

示例：

- `app1-prod-v0.1.0-windows-x64.zip`
- `app1-prod-v0.1.0-linux-x64.tar.gz`

字段含义：

- 服务名：标识当前 bundle 属于哪个独立子服务
- 环境：标识 bundle 对应的部署环境
- 版本号：标识当前交付版本
- 平台：标识目标操作系统
- 架构：标识目标 CPU 架构
- 压缩格式：标识交付包格式

## 12. 不应进入 bundle 的内容

以下内容不应进入最终运行时 bundle：

- 前端源码
- Go 源码
- 测试文件
- mock 数据
- `node_modules`
- 构建缓存
- IDE 配置
- Git 元数据
- 本地调试脚本
- 临时文件
- 与运行时无关的文档
- `frontend-gateway`
- 外置 `schema.sql`
- bundle 自身的 `VERSION` 文件

强调：

- bundle 是运行时交付物，不是源码仓库镜像
- bundle 中不应混入开发期目录、构建中间产物或与运行无关的内容

## 13. 宿主如何消费该 bundle

宿主消费 Program Bundle 的高层步骤如下：

1. 读取 bundle 根目录下的 `manifest.json`
2. 根据 manifest 找到前端静态目录
3. 根据 manifest 找到前端入口文件
4. 根据 manifest 找到后端可执行文件
5. 为该服务分配 `basePath`
6. 为 API 分配 `apiBasePath`
7. 把这些运行参数注入给前端和后端
8. 托管前端静态资源，并按 `frontend/nginx.conf` 或等价方式配置静态资源和 API 转发
9. 把 API 请求转发给后端服务

这一过程只说明 bundle 如何被宿主消费，不要求子服务理解宿主内部实现细节。

## 14. 最终 checklist

- `manifest.json` 位于 bundle 根目录
- manifest 中路径均相对于 bundle 根目录
- 前端产物位于 `frontend/dist/`
- bundle 包含 `frontend/nginx.conf`
- 后端主程序位于 `backend/app` 或 `backend/app.exe`
- 根目录包含 `.env.example`
- 根目录包含 `start.sh`、`stop.sh`、`deploy.sh`
- Windows bundle 包含 `start.ps1`、`stop.ps1`、`deploy.ps1`
- 根目录包含 `scripts/`
- 包含 `configs/runtime.env.example`
- 运行时主配置载体只有根目录 `.env` 系列和运行时环境配置
- `runtime-config.json` 只作为可选手段说明，不作为推荐主方案
- `logs/` 不单独预置到 bundle 中
- bundle 只包含运行时必需文件和可选运行资源
- bundle 不包含源码、测试、缓存、依赖目录和本地开发残留
- bundle 不包含 `VERSION`、`frontend-gateway` 和外置 `backend/schema.sql`
- 一个环境一个 bundle
- 子服务只理解 `basePath`、`apiBasePath`、`baseUrl`
- 子服务不硬编码宿主路径规则
