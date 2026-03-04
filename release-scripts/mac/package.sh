#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
RELEASE_DIR="$ROOT_DIR/release"
COMPOSE_FILE="$ROOT_DIR/docker-compose.yml"
ROOT_ENV_FILE="$ROOT_DIR/.env"
ROOT_ENV_EXAMPLE_FILE="$ROOT_DIR/.env.example"
MANAGE_JWK_SCRIPT="$ROOT_DIR/release-scripts/mac/manage-jwk-key.sh"
SETUP_JWK_SCRIPT="$ROOT_DIR/release-scripts/mac/setup-jwk-public-key.sh"
SETUP_JWK_WINDOWS_SCRIPT="$ROOT_DIR/release-scripts/windows/setup-jwk-public-key.ps1"
DROP_INBOX_SQL="$BACKEND_DIR/migrations/drop_inbox.sql"
BUILD_ENV_FILE=""

log() {
  printf '[package] %s\n' "$*"
}

validate_env_file() {
  local env_file="$1"
  local key line value
  for key in AUTH_ADMIN_PASSWORD_BCRYPT AUTH_APP_MASTER_PASSWORD_BCRYPT; do
    line="$(grep -E "^${key}=" "$env_file" | tail -n 1 || true)"
    if [ -z "$line" ]; then
      continue
    fi
    value="${line#*=}"
    case "$value" in
      \$2*)
        printf '[package] invalid %s in %s\n' "$key" "$env_file" >&2
        printf '[package] bcrypt values containing `$` must be quoted or escaped in shell env files.\n' >&2
        printf "[package] fix example: %s='\\$2a\\$10\\$...'\n" "$key" >&2
        exit 1
        ;;
    esac
  done
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf '[package] missing required command: %s\n' "$1" >&2
    exit 1
  fi
}

require_cmd go
require_cmd npm

if [ ! -f "$BACKEND_DIR/go.mod" ]; then
  printf '[package] backend/go.mod not found\n' >&2
  exit 1
fi

if [ ! -f "$FRONTEND_DIR/package.json" ]; then
  printf '[package] frontend/package.json not found\n' >&2
  exit 1
fi

if [ ! -f "$COMPOSE_FILE" ]; then
  printf '[package] docker-compose.yml not found in project root\n' >&2
  exit 1
fi

if [ ! -f "$ROOT_ENV_EXAMPLE_FILE" ]; then
  printf '[package] .env.example not found in project root\n' >&2
  exit 1
fi

if [ ! -f "$MANAGE_JWK_SCRIPT" ]; then
  printf '[package] release-scripts/mac/manage-jwk-key.sh not found in project root\n' >&2
  exit 1
fi

if [ ! -f "$SETUP_JWK_SCRIPT" ]; then
  printf '[package] release-scripts/mac/setup-jwk-public-key.sh not found in project root\n' >&2
  exit 1
fi

if [ ! -f "$SETUP_JWK_WINDOWS_SCRIPT" ]; then
  printf '[package] release-scripts/windows/setup-jwk-public-key.ps1 not found in project root\n' >&2
  exit 1
fi

if [ ! -f "$DROP_INBOX_SQL" ]; then
  printf '[package] backend/migrations/drop_inbox.sql not found\n' >&2
  exit 1
fi

if [ -f "$ROOT_ENV_FILE" ]; then
  BUILD_ENV_FILE="$ROOT_ENV_FILE"
else
  BUILD_ENV_FILE="$ROOT_ENV_EXAMPLE_FILE"
fi

log "load build environment from $BUILD_ENV_FILE"
validate_env_file "$BUILD_ENV_FILE"
set -a
# shellcheck disable=SC1090
. "$BUILD_ENV_FILE"
set +a

log "clean release directory: $RELEASE_DIR"
rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR/backend" "$RELEASE_DIR/frontend" "$RELEASE_DIR/release-scripts/mac" "$RELEASE_DIR/release-scripts/windows"

log "build backend binary"
(
  cd "$BACKEND_DIR"
  go mod tidy
  CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -ldflags='-s -w -buildid=' -o app ./cmd/server
)

if [ ! -f "$BACKEND_DIR/app" ]; then
  printf '[package] backend binary not found\n' >&2
  exit 1
fi

cp "$BACKEND_DIR/app" "$RELEASE_DIR/backend/app"
cp "$BACKEND_DIR/schema.sql" "$RELEASE_DIR/backend/schema.sql"
cp "$BACKEND_DIR/application.yml" "$RELEASE_DIR/backend/application.yml"
cp "$DROP_INBOX_SQL" "$RELEASE_DIR/backend/drop_inbox.sql"

cat >"$RELEASE_DIR/backend/Dockerfile" <<'DOCKER_BACKEND_EOF'
FROM scratch
WORKDIR /app
COPY app /app/app
COPY schema.sql /app/schema.sql
COPY application.yml /app/application.yml
EXPOSE 8080
ENV AUTH_DB_PATH=/data/auth.db
ENV AUTH_SCHEMA_PATH=/app/schema.sql
ENV AUTH_APPLICATION_YML_PATH=/app/application.yml
ENTRYPOINT ["/app/app"]
DOCKER_BACKEND_EOF

log "build frontend dist"
(
  cd "$FRONTEND_DIR"
  if [ ! -d node_modules ]; then
    npm ci
  fi
  npm run build
  CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -ldflags='-s -w -buildid=' -o frontend-gateway ./proxy/main.go
)

if [ ! -d "$FRONTEND_DIR/dist" ]; then
  printf '[package] frontend dist directory not found\n' >&2
  exit 1
fi

cp -R "$FRONTEND_DIR/dist" "$RELEASE_DIR/frontend/dist"
cp "$FRONTEND_DIR/frontend-gateway" "$RELEASE_DIR/frontend/frontend-gateway"

cat >"$RELEASE_DIR/frontend/Dockerfile" <<'DOCKER_FRONTEND_EOF'
FROM scratch
WORKDIR /app
COPY frontend-gateway /app/frontend-gateway
COPY dist /app/dist
EXPOSE 80
ENV BACKEND_TARGET=http://backend:8080
ENV STATIC_DIR=/app/dist
ENTRYPOINT ["/app/frontend-gateway"]
DOCKER_FRONTEND_EOF

cp "$COMPOSE_FILE" "$RELEASE_DIR/docker-compose.yml"
cp "$MANAGE_JWK_SCRIPT" "$RELEASE_DIR/release-scripts/mac/manage-jwk-key.sh"
chmod +x "$RELEASE_DIR/release-scripts/mac/manage-jwk-key.sh"
cp "$SETUP_JWK_SCRIPT" "$RELEASE_DIR/release-scripts/mac/setup-jwk-public-key.sh"
chmod +x "$RELEASE_DIR/release-scripts/mac/setup-jwk-public-key.sh"
cp "$SETUP_JWK_WINDOWS_SCRIPT" "$RELEASE_DIR/release-scripts/windows/setup-jwk-public-key.ps1"

cat >"$RELEASE_DIR/DEPLOY.md" <<'DEPLOY_EOF'
# Release Deployment

1. Copy this `release` directory to the target host.
2. Prepare a runtime `.env` in the release root (`./.env`) with production values.
3. (Optional) if upgrading an existing DB, remove inbox legacy table:

   sqlite3 ./data/auth.db < ./backend/drop_inbox.sql
4. Generate/export JWK key pair before first startup:

   ./release-scripts/mac/manage-jwk-key.sh --mode bootstrap --db ./data/auth.db --out ./data/keys

5. Export dedicated public key file for downstream projects (optional):

   ./release-scripts/mac/setup-jwk-public-key.sh --db ./data/auth.db --out ./data/keys --public-out ./data/keys/publicKey.pem

   # Windows PowerShell
   .\release-scripts\windows\setup-jwk-public-key.ps1 -Mode bootstrap -DbPath .\data\auth.db -OutDir .\data\keys -PublicOut .\data\keys\publicKey.pem

6. Start with Docker Compose:

   docker compose up -d --build
DEPLOY_EOF

log "release package generated:"
log "  $RELEASE_DIR/backend/app"
log "  $RELEASE_DIR/backend/Dockerfile"
log "  $RELEASE_DIR/backend/application.yml"
log "  $RELEASE_DIR/backend/drop_inbox.sql"
log "  $RELEASE_DIR/frontend/dist"
log "  $RELEASE_DIR/frontend/Dockerfile"
log "  $RELEASE_DIR/docker-compose.yml"
log "  $RELEASE_DIR/release-scripts/mac/manage-jwk-key.sh"
log "  $RELEASE_DIR/release-scripts/mac/setup-jwk-public-key.sh"
log "  $RELEASE_DIR/release-scripts/windows/setup-jwk-public-key.ps1"
log "  $RELEASE_DIR/DEPLOY.md"
