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

require_cmd mvn
require_cmd npm

if [ ! -f "$BACKEND_DIR/pom.xml" ]; then
  printf '[package] backend/pom.xml not found\n' >&2
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
mkdir -p "$RELEASE_DIR/backend" "$RELEASE_DIR/frontend" "$RELEASE_DIR/release-scripts/mac"

log "build backend jar"
(
  cd "$BACKEND_DIR"
  mvn -q -DskipTests package
)

BACKEND_JAR="$(find "$BACKEND_DIR/target" -maxdepth 1 -type f -name '*.jar' ! -name '*original*.jar' | head -n 1)"
if [ -z "${BACKEND_JAR:-}" ]; then
  printf '[package] backend jar not found in backend/target\n' >&2
  exit 1
fi

cp "$BACKEND_JAR" "$RELEASE_DIR/backend/app.jar"

cat >"$RELEASE_DIR/backend/Dockerfile" <<'DOCKER_BACKEND_EOF'
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /data
COPY app.jar /app/app.jar
EXPOSE 8080
ENV AUTH_DB_PATH=/data/auth.db
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
DOCKER_BACKEND_EOF

log "build frontend dist"
(
  cd "$FRONTEND_DIR"
  if [ ! -d node_modules ]; then
    npm ci
  fi
  npm run build
)

if [ ! -d "$FRONTEND_DIR/dist" ]; then
  printf '[package] frontend dist directory not found\n' >&2
  exit 1
fi

cp -R "$FRONTEND_DIR/dist" "$RELEASE_DIR/frontend/dist"
cp "$FRONTEND_DIR/nginx.conf" "$RELEASE_DIR/frontend/nginx.conf"

cat >"$RELEASE_DIR/frontend/Dockerfile" <<'DOCKER_FRONTEND_EOF'
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
DOCKER_FRONTEND_EOF

cp "$COMPOSE_FILE" "$RELEASE_DIR/docker-compose.yml"
cp "$MANAGE_JWK_SCRIPT" "$RELEASE_DIR/release-scripts/mac/manage-jwk-key.sh"
chmod +x "$RELEASE_DIR/release-scripts/mac/manage-jwk-key.sh"

cat >"$RELEASE_DIR/DEPLOY.md" <<'DEPLOY_EOF'
# Release Deployment

1. Copy this `release` directory to the target host.
2. Prepare a runtime `.env` in the release root (`./.env`) with production values.
3. Generate/export JWK key pair before first startup:

   ./release-scripts/mac/manage-jwk-key.sh --mode bootstrap --db ./data/auth.db --out ./data/keys

4. Start with Docker Compose:

   docker compose up -d --build
DEPLOY_EOF

log "release package generated:"
log "  $RELEASE_DIR/backend/app.jar"
log "  $RELEASE_DIR/backend/Dockerfile"
log "  $RELEASE_DIR/frontend/dist"
log "  $RELEASE_DIR/frontend/Dockerfile"
log "  $RELEASE_DIR/docker-compose.yml"
log "  $RELEASE_DIR/release-scripts/mac/manage-jwk-key.sh"
log "  $RELEASE_DIR/DEPLOY.md"
