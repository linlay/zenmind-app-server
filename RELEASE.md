# Release Guide

## Local Build

```bash
./release-scripts/mac/package.sh
```

Release artifacts are generated in `./release/`.

## Runtime Deploy

1. Copy `release/` to target host.
2. Prepare runtime `.env` with required bcrypt env vars (`AUTH_ADMIN_PASSWORD_BCRYPT`, `AUTH_APP_MASTER_PASSWORD_BCRYPT`).
3. Generate your own bcrypt values before production deploy (do not keep dev default):

macOS / Linux:

```bash
curl -sS -X POST http://localhost:11952/admin/api/bcrypt/generate \
  -H 'Content-Type: application/json' \
  -d '{"password":"MyStrongPassword!123"}'
```

Windows (PowerShell 7+):

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:11952/admin/api/bcrypt/generate" `
  -ContentType "application/json" `
  -Body '{"password":"MyStrongPassword!123"}'
```

4. (Optional) if upgrading existing database, drop legacy inbox table:
```bash
sqlite3 ./data/auth.db < ./backend/drop_inbox.sql
```

5. (Recommended) bootstrap JWK keys:

```bash
./release-scripts/mac/manage-jwk-key.sh --mode bootstrap --db ./data/auth.db --out ./data/keys
```

6. Start services:

```bash
docker compose up -d --build
```

## Image Size Check

Backend and frontend image targets are ~30MB each.

```bash
docker image ls | rg 'app-auth-backend-go|app-auth-frontend-go'
```

For exact bytes:

```bash
docker image inspect app-auth-backend-go --format '{{.Size}}'
docker image inspect app-auth-frontend-go --format '{{.Size}}'
```
