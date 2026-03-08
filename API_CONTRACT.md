# API Contract

## Auth Prefixes

- OAuth2: `/oauth2/*`
- OIDC: `/openid/*`
- App Auth: `/api/auth/*`
- App Event: `/api/app/*`
- Admin: `/admin/api/*`

## App Auth

- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `GET /api/auth/devices`
- `PATCH /api/auth/devices/{deviceId}`
- `DELETE /api/auth/devices/{deviceId}`
- `GET /api/auth/jwks`
- `GET /api/auth/new-device-access`

## App Event

- `GET /api/app/ws` (WebSocket)

## Admin

### Session

- `POST /admin/api/session/login`
- `POST /admin/api/session/logout`
- `GET /admin/api/session/me`

### Security Utility

- `POST /admin/api/bcrypt/generate`
- `GET /admin/api/security/jwks`
- `POST /admin/api/security/public-key/generate`
- `POST /admin/api/security/key-pair/generate`
- `POST /admin/api/security/app-tokens/issue`
- `POST /admin/api/security/app-tokens/refresh`
- `GET /admin/api/security/app-devices`
- `POST /admin/api/security/app-devices/{deviceId}/revoke`
- `GET /admin/api/security/new-device-access`
- `PUT /admin/api/security/new-device-access`
- `GET /admin/api/security/tokens`

### Users

- `GET /admin/api/users`
- `POST /admin/api/users`
- `GET /admin/api/users/{userId}`
- `PUT /admin/api/users/{userId}`
- `PATCH /admin/api/users/{userId}/status`
- `POST /admin/api/users/{userId}/password`

### Clients

- `GET /admin/api/clients`
- `POST /admin/api/clients`
- `GET /admin/api/clients/{clientId}`
- `PUT /admin/api/clients/{clientId}`
- `PATCH /admin/api/clients/{clientId}/status`
- `POST /admin/api/clients/{clientId}/secret/rotate`

### Config Files

- `GET /admin/api/config-files`
- `GET /admin/api/config-files/content?id={configFileId}` (`path` remains supported during migration)
- `PUT /admin/api/config-files/content` with JSON body `{ "id": "...", "content": "..." }` (`path` remains supported during migration)

## OAuth2 / OIDC

- `GET /oauth2/authorize`
- `POST /oauth2/token`
- `POST /oauth2/revoke`
- `POST /oauth2/introspect`
- `GET /openid/.well-known/openid-configuration`
- `GET /openid/.well-known/oauth-authorization-server`
- `GET /openid/jwks`
- `GET /openid/userinfo`
- `POST /openid/userinfo`
- `GET /openid/login`
- `POST /openid/login`
- `GET /openid/consent`
- `POST /openid/consent`

## Error Format

All API errors return JSON:

```json
{"error":"..."}
```
