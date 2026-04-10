#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"
BACKEND_BIN="$SCRIPT_DIR/backend/app"
FRONTEND_BIN="$SCRIPT_DIR/frontend/frontend-gateway"
RUN_DIR="$SCRIPT_DIR/run"
LOG_DIR="$RUN_DIR/logs"
BACKEND_PID_FILE="$RUN_DIR/backend.pid"
FRONTEND_PID_FILE="$RUN_DIR/frontend.pid"

die() { echo "[program-start] $*" >&2; exit 1; }

[[ -f "$ENV_FILE" ]] || die "missing .env (copy from .env.example first)"
[[ -x "$BACKEND_BIN" ]] || die "missing backend binary: $BACKEND_BIN"
[[ -x "$FRONTEND_BIN" ]] || die "missing frontend gateway binary: $FRONTEND_BIN"

if [[ -f "$BACKEND_PID_FILE" ]] && kill -0 "$(cat "$BACKEND_PID_FILE")" >/dev/null 2>&1; then
  die "backend already running"
fi
if [[ -f "$FRONTEND_PID_FILE" ]] && kill -0 "$(cat "$FRONTEND_PID_FILE")" >/dev/null 2>&1; then
  die "frontend already running"
fi

set -a
. "$ENV_FILE"
set +a

SERVER_PORT="${SERVER_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-11950}"
export SERVER_PORT FRONTEND_PORT
export AUTH_DB_PATH="${AUTH_DB_PATH:-$SCRIPT_DIR/data/auth.db}"
export AUTH_SCHEMA_PATH="${AUTH_SCHEMA_PATH:-$SCRIPT_DIR/backend/schema.sql}"
export AUTH_CONFIG_FILES_REGISTRY_PATH="${AUTH_CONFIG_FILES_REGISTRY_PATH:-$SCRIPT_DIR/config/config-files.runtime.yml}"
export BACKEND_TARGET="${BACKEND_TARGET:-http://127.0.0.1:${SERVER_PORT}}"
export LISTEN_ADDR="${LISTEN_ADDR:-:${FRONTEND_PORT}}"
export STATIC_DIR="${STATIC_DIR:-$SCRIPT_DIR/frontend/dist}"

mkdir -p "$RUN_DIR" "$LOG_DIR" "$SCRIPT_DIR/data"

"$BACKEND_BIN" >"$LOG_DIR/backend.log" 2>&1 &
backend_pid=$!
printf '%s\n' "$backend_pid" >"$BACKEND_PID_FILE"

sleep 1
if ! kill -0 "$backend_pid" >/dev/null 2>&1; then
  die "backend failed to start; see $LOG_DIR/backend.log"
fi

"$FRONTEND_BIN" >"$LOG_DIR/frontend.log" 2>&1 &
frontend_pid=$!
printf '%s\n' "$frontend_pid" >"$FRONTEND_PID_FILE"

sleep 1
if ! kill -0 "$frontend_pid" >/dev/null 2>&1; then
  kill "$backend_pid" >/dev/null 2>&1 || true
  rm -f "$BACKEND_PID_FILE"
  die "frontend failed to start; see $LOG_DIR/frontend.log"
fi

echo "[program-start] started zenmind-app-server $APP_SERVER_VERSION"
echo "[program-start] browser: http://127.0.0.1:${FRONTEND_PORT}/admin/"
