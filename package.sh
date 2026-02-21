#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
RELEASE_DIR="$ROOT_DIR/release"
COMPOSE_FILE="$ROOT_DIR/docker-compose.yml"
ROOT_ENV_EXAMPLE="$ROOT_DIR/.env.example"

log() {
  printf '[package] %s\n' "$*"
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

if [ ! -f "$ROOT_ENV_EXAMPLE" ]; then
  printf '[package] .env.example not found in project root\n' >&2
  exit 1
fi

if [ -f "$ROOT_DIR/.env" ]; then
  log "load environment from $ROOT_DIR/.env"
  set -a
  # shellcheck disable=SC1090
  . "$ROOT_DIR/.env"
  set +a
fi

log "clean release directory: $RELEASE_DIR"
rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR/backend" "$RELEASE_DIR/frontend"

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

cat >"$RELEASE_DIR/backend/Dockerfile" <<'EOF'
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /data
COPY app.jar /app/app.jar
EXPOSE 8080
ENV AUTH_DB_PATH=/data/auth.db
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
EOF

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

cat >"$RELEASE_DIR/frontend/Dockerfile" <<'EOF'
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
EOF

cp "$COMPOSE_FILE" "$RELEASE_DIR/docker-compose.yml"
cp "$ROOT_ENV_EXAMPLE" "$RELEASE_DIR/.env.example"

cat >"$RELEASE_DIR/DEPLOY.md" <<'EOF'
# Release Deployment

1. Copy this `release` directory to the target host.
2. Enter the directory and create environment file:

   cp .env.example .env

3. Edit `.env` with production values.
   Backend and frontend image build both read this root `.env` first.
4. Start with Docker Compose:

   docker compose up -d --build
EOF

log "release package generated:"
log "  $RELEASE_DIR/backend/app.jar"
log "  $RELEASE_DIR/backend/Dockerfile"
log "  $RELEASE_DIR/frontend/dist"
log "  $RELEASE_DIR/frontend/Dockerfile"
log "  $RELEASE_DIR/docker-compose.yml"
log "  $RELEASE_DIR/.env.example"
log "  $RELEASE_DIR/DEPLOY.md"
