# App Server

统一认证中心 + App 设备认证 + 消息盒子 + 管理端 API。

## 1. 服务地址

- Backend: `http://localhost:8080`
- Frontend(Admin): `http://localhost:8081/admin/`

## 2. 快速启动

```bash
docker compose up --build
```

建议在项目根目录准备 `.env`（可先用 `.env.example`），`docker-compose.yml`、`package.sh`、前端 Vite 配置都读取根目录 `.env`。

## 3. 默认账号

- Admin: `admin / password`
- OAuth 测试用户: `user / password`
- App 主密码: `password`

## 4. API 前缀

- OAuth2: `/oauth2/*`
- OIDC: `/openid/*`
- App Auth: `/api/auth/*`
- App Inbox: `/api/app/*`
- Admin: `/admin/api/*`

## 5. 关键配置说明

- `AUTH_DB_PATH`
  - SQLite 文件路径（默认 `./auth.db`）。
  - JWK 密钥也在这个库里，表 `JWK_KEY_` 的 `PRIVATE_KEY_` 列就是私钥（Base64 编码的 PKCS#8）。
- `AUTH_ISSUER`（默认 `http://localhost:8080`）
  - 作为 OAuth2/OIDC 的 issuer，写入 `/.well-known` 元数据和令牌相关配置。
  - 也用于 App Access Token 的 `iss` claim 签发与校验，不一致会导致 token 验证失败。
- `AUTH_APP_INTERNAL_WEBHOOK_SECRET`
  - 用于 `/api/app/internal/chat-events` 的 HMAC-SHA256 签名校验。
- `AUTH_TOKEN_ACCESS_TTL` / `AUTH_TOKEN_REFRESH_TTL` / `AUTH_TOKEN_ROTATE_REFRESH_TOKEN`
  - 控制 OAuth2 客户端 access token / refresh token 生命周期与是否轮换 refresh token。

## 6. curl 验证（可直接复制）

> 以下命令默认在 macOS / Linux 下执行。

### 6.1 初始化变量

```bash
BASE_URL="http://localhost:11952"
```

> `BASE_URL` 建议指向后端 `8080`。`8081` 前端容器只代理 `/admin/api`、`/oauth2`、`/openid`，不代理 `/api/auth`、`/api/app`。

### 6.2 基础可用性检查

```bash
curl -sS "$BASE_URL/openid/.well-known/openid-configuration"
curl -sS "$BASE_URL/openid/.well-known/oauth-authorization-server"
```

### 6.3 App 登录（拿 accessToken / deviceToken）

```bash
LOGIN_JSON="$(curl -sS -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "masterPassword":"password",
    "deviceName":"MacBook-Pro",
    "accessTtlSeconds":1800
  }')"

echo "$LOGIN_JSON"

ACCESS_TOKEN="$(printf '%s' "$LOGIN_JSON" | sed -nE 's/.*"accessToken":"([^"]+)".*/\1/p')"
DEVICE_TOKEN="$(printf '%s' "$LOGIN_JSON" | sed -nE 's/.*"deviceToken":"([^"]+)".*/\1/p')"
DEVICE_ID="$(printf '%s' "$LOGIN_JSON" | sed -nE 's/.*"deviceId":"([^"]+)".*/\1/p')"

echo "ACCESS_TOKEN length: ${#ACCESS_TOKEN}"
echo "DEVICE_TOKEN length: ${#DEVICE_TOKEN}"
echo "DEVICE_ID: $DEVICE_ID"
```

### 6.4 验证 App 鉴权接口

```bash
curl -sS "$BASE_URL/api/auth/me" \
  -H "Authorization: Bearer $ACCESS_TOKEN"

curl -sS "$BASE_URL/api/auth/devices" \
  -H "Authorization: Bearer $ACCESS_TOKEN"

curl -sS -X PATCH "$BASE_URL/api/auth/devices/$DEVICE_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"deviceName":"My-Mac"}'
```

### 6.5 刷新 access token

```bash
REFRESH_JSON="$(curl -sS -X POST "$BASE_URL/api/auth/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"deviceToken\":\"$DEVICE_TOKEN\",\"accessTtlSeconds\":1200}")"

echo "$REFRESH_JSON"

NEW_ACCESS_TOKEN="$(printf '%s' "$REFRESH_JSON" | sed -nE 's/.*"accessToken":"([^"]+)".*/\1/p')"
NEW_DEVICE_TOKEN="$(printf '%s' "$REFRESH_JSON" | sed -nE 's/.*"deviceToken":"([^"]+)".*/\1/p')"

echo "NEW_ACCESS_TOKEN length: ${#NEW_ACCESS_TOKEN}"
echo "NEW_DEVICE_TOKEN length: ${#NEW_DEVICE_TOKEN}"
```

> `accessTtlSeconds` 为可选字段，不传时使用服务端默认值 `AUTH_APP_ACCESS_TTL`（默认 `PT10M`）。可申请的最大值受 `AUTH_APP_MAX_ACCESS_TTL` 限制（默认 `PT12H`）。

### 6.6 管理员登录（拿 Cookie）

```bash
ADMIN_COOKIE_JAR="$(mktemp)"

curl -sS -X POST "$BASE_URL/admin/api/session/login" \
  -c "$ADMIN_COOKIE_JAR" \
  -H "Content-Type: application/json" \
  -d '{
    "username":"admin",
    "password":"password"
  }'

curl -sS "$BASE_URL/admin/api/session/me" \
  -b "$ADMIN_COOKIE_JAR"
```

### 6.7 管理员发送消息 -> App 侧查看

```bash
SEND_JSON="$(curl -sS -X POST "$BASE_URL/admin/api/inbox/send" \
  -b "$ADMIN_COOKIE_JAR" \
  -H "Content-Type: application/json" \
  -d '{
    "title":"系统通知",
    "content":"发布成功",
    "type":"INFO",
    "payload":{"source":"admin-console"}
  }')"

echo "$SEND_JSON"

MESSAGE_ID="$(printf '%s' "$SEND_JSON" | sed -nE 's/.*"messageId":"([^"]+)".*/\1/p')"
echo "MESSAGE_ID: $MESSAGE_ID"

curl -sS "$BASE_URL/api/app/inbox/unread-count" \
  -H "Authorization: Bearer $ACCESS_TOKEN"

curl -sS "$BASE_URL/api/app/inbox?limit=20" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

### 6.8 App 侧标记已读

```bash
curl -i -X POST "$BASE_URL/api/app/inbox/read" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"messageIds\":[\"$MESSAGE_ID\"]}"

curl -i -X POST "$BASE_URL/api/app/inbox/read-all" \
  -H "Authorization: Bearer $ACCESS_TOKEN"

curl -sS "$BASE_URL/api/app/inbox/unread-count" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

### 6.9 管理端用户/客户端 API 验证

```bash
# 用户列表
curl -sS "$BASE_URL/admin/api/users" -b "$ADMIN_COOKIE_JAR"

# 创建用户
curl -sS -X POST "$BASE_URL/admin/api/users" \
  -b "$ADMIN_COOKIE_JAR" \
  -H "Content-Type: application/json" \
  -d '{
    "username":"u_demo",
    "password":"pass_demo",
    "displayName":"Demo User",
    "status":"ACTIVE"
  }'

# 客户端列表
curl -sS "$BASE_URL/admin/api/clients" -b "$ADMIN_COOKIE_JAR"

# 创建客户端
curl -sS -X POST "$BASE_URL/admin/api/clients" \
  -b "$ADMIN_COOKIE_JAR" \
  -H "Content-Type: application/json" \
  -d '{
    "clientId":"demo-client",
    "clientName":"Demo Client",
    "clientSecret":"",
    "grantTypes":["authorization_code","refresh_token"],
    "redirectUris":["myapp://oauthredirect"],
    "scopes":["openid","profile"],
    "requirePkce":true,
    "status":"ACTIVE"
  }'
```

### 6.10 内部回调签名验证（HMAC）

> 依赖 `openssl`。

```bash
WEBHOOK_SECRET="change-me"
TIMESTAMP="$(date +%s)"
BODY='{"chatId":"123e4567-e89b-12d3-a456-426614174111","runId":"123e4567-e89b-12d3-a456-426614174112","chatName":"Demo Chat","updatedAt":1739870000000}'

SIGNATURE="$(printf '%s' "$TIMESTAMP.$BODY" | openssl dgst -sha256 -hmac "$WEBHOOK_SECRET" | sed 's/^.* //')"

echo "TIMESTAMP=$TIMESTAMP"
echo "SIGNATURE=$SIGNATURE"

curl -sS -X POST "$BASE_URL/api/app/internal/chat-events" \
  -H "Content-Type: application/json" \
  -H "X-App-Timestamp: $TIMESTAMP" \
  -H "X-App-Signature: $SIGNATURE" \
  -d "$BODY"
```

### 6.11 注销与清理

```bash
curl -i -X POST "$BASE_URL/api/auth/logout" \
  -H "Authorization: Bearer $ACCESS_TOKEN"

curl -i -X POST "$BASE_URL/admin/api/session/logout" \
  -b "$ADMIN_COOKIE_JAR"

rm -f "$ADMIN_COOKIE_JAR"
```

## 7. API 定义总览

### 7.1 App Auth

- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `GET /api/auth/devices`
- `PATCH /api/auth/devices/{deviceId}`
- `DELETE /api/auth/devices/{deviceId}`
- `GET /api/auth/jwks`

### 7.2 App Inbox

- `GET /api/app/inbox`
- `GET /api/app/inbox/unread-count`
- `POST /api/app/inbox/read`
- `POST /api/app/inbox/read-all`
- `POST /api/app/internal/chat-events`
- `WS /api/app/ws`

### 7.3 Admin

- Session
  - `POST /admin/api/session/login`
  - `POST /admin/api/session/logout`
  - `GET /admin/api/session/me`
- Security Utility
  - `POST /admin/api/bcrypt/generate`
  - `GET /admin/api/security/jwks`
  - `POST /admin/api/security/public-key/generate`
  - `POST /admin/api/security/key-pair/generate`
  - `POST /admin/api/security/app-tokens/issue`
  - `POST /admin/api/security/app-tokens/refresh`
  - `GET /admin/api/security/app-devices`
  - `POST /admin/api/security/app-devices/{deviceId}/revoke`
  - `GET /admin/api/security/tokens`
- Users
  - `GET /admin/api/users`
  - `POST /admin/api/users`
  - `GET /admin/api/users/{userId}`
  - `PUT /admin/api/users/{userId}`
  - `PATCH /admin/api/users/{userId}/status`
  - `POST /admin/api/users/{userId}/password`
- Clients
  - `GET /admin/api/clients`
  - `POST /admin/api/clients`
  - `GET /admin/api/clients/{clientId}`
  - `PUT /admin/api/clients/{clientId}`
  - `PATCH /admin/api/clients/{clientId}/status`
  - `POST /admin/api/clients/{clientId}/secret/rotate`
- Inbox
  - `GET /admin/api/inbox`
  - `GET /admin/api/inbox/unread-count`
  - `POST /admin/api/inbox/send`
  - `POST /admin/api/inbox/read`
  - `POST /admin/api/inbox/read-all`
  - `POST /admin/api/inbox/realtime`

### 7.4 OAuth2 / OIDC

- `GET /oauth2/authorize`
- `POST /oauth2/token`
- `POST /oauth2/revoke`
- `POST /oauth2/introspect`
- `GET /openid/jwks`
- `GET /openid/.well-known/openid-configuration`
- `GET /openid/.well-known/oauth-authorization-server`
- `GET /openid/userinfo`
- `POST /openid/userinfo`
- `GET /openid/login`
- `GET /openid/consent`
- `POST /openid/consent`

## 8. 常见错误

- `400`：参数不合法（例如字段为空、格式错误）
- `401`：鉴权失败（Bearer/Cookie/HMAC）
- `409`：资源冲突（如用户名或客户端重复）

错误响应统一格式：

```json
{"error":"..."}
```
# zenmind-app-server
