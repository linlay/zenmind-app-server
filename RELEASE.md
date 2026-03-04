# Release Guide

## Local Build

```bash
./release-scripts/mac/package.sh
```

Release artifacts are generated in `./release/`.

## Runtime Deploy

1. Copy `release/` to target host.
2. Prepare runtime `.env` with required bcrypt env vars.
3. (Optional) if upgrading existing database, drop legacy inbox table:

```bash
sqlite3 ./data/auth.db < ./backend/drop_inbox.sql
```

4. (Recommended) bootstrap JWK keys:

```bash
./release-scripts/mac/manage-jwk-key.sh --mode bootstrap --db ./data/auth.db --out ./data/keys
```

5. Start services:

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
