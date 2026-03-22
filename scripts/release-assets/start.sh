#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.release.yml"
IMAGES_DIR="$SCRIPT_DIR/images"

die() { echo "[start] $*" >&2; exit 1; }

[[ -f "$ENV_FILE" ]] || die "missing .env (copy from .env.example first)"
[[ -f "$COMPOSE_FILE" ]] || die "missing docker-compose.release.yml"

command -v docker >/dev/null 2>&1 || die "docker is required"
docker compose version >/dev/null 2>&1 || die "docker compose v2 is required"

set -a
. "$ENV_FILE"
set +a

APP_SERVER_VERSION="${APP_SERVER_VERSION:-latest}"
FRONTEND_PORT="${FRONTEND_PORT:-11950}"
BACKEND_IMAGE="app-server-backend:$APP_SERVER_VERSION"
FRONTEND_IMAGE="app-server-frontend:$APP_SERVER_VERSION"

load_image() {
  local ref="$1"
  local tar="$2"
  if docker image inspect "$ref" >/dev/null 2>&1; then
    return 0
  fi
  [[ -f "$tar" ]] || die "missing image tar: $tar"
  docker load -i "$tar" >/dev/null
  docker image inspect "$ref" >/dev/null 2>&1 || die "failed to load image: $ref"
}

load_image "$BACKEND_IMAGE" "$IMAGES_DIR/app-server-backend.tar"
load_image "$FRONTEND_IMAGE" "$IMAGES_DIR/app-server-frontend.tar"

mkdir -p "$SCRIPT_DIR/data"

export APP_SERVER_VERSION FRONTEND_PORT
docker compose --project-directory "$SCRIPT_DIR" -f "$COMPOSE_FILE" up -d

echo "[start] started zenmind-app-server $APP_SERVER_VERSION"
echo "[start] browser: http://127.0.0.1:${FRONTEND_PORT}/admin/"
