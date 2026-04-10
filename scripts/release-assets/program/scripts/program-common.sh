#!/usr/bin/env bash
set -euo pipefail

PROGRAM_COMMON_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUNDLE_ROOT="$(cd "$PROGRAM_COMMON_DIR/.." && pwd)"
ENV_FILE="$BUNDLE_ROOT/.env"
BACKEND_BIN="$BUNDLE_ROOT/backend/zenmind-app-server"
FRONTEND_DIR="$BUNDLE_ROOT/frontend"
DIST_DIR="$FRONTEND_DIR/dist"
RUN_DIR="$BUNDLE_ROOT/run"
LOG_DIR="$RUN_DIR/logs"
BACKEND_LOG="$LOG_DIR/backend.log"
BACKEND_PID_FILE="$RUN_DIR/backend.pid"
DATA_DIR="$BUNDLE_ROOT/data"

program_die() {
  echo "[program] $*" >&2
  exit 1
}

program_load_env() {
  [[ -f "$ENV_FILE" ]] || program_die "missing .env (copy from .env.example first)"
  set -a
  . "$ENV_FILE"
  set +a
  SERVER_PORT="${SERVER_PORT:-18080}"
  AUTH_DB_PATH="${AUTH_DB_PATH:-./data/auth.db}"
  export SERVER_PORT AUTH_DB_PATH
}

program_load_env_optional() {
  if [[ -f "$ENV_FILE" ]]; then
    program_load_env
    return
  fi
  SERVER_PORT="${SERVER_PORT:-18080}"
  AUTH_DB_PATH="${AUTH_DB_PATH:-./data/auth.db}"
  export SERVER_PORT AUTH_DB_PATH
}

program_prepare_runtime_dirs() {
  mkdir -p "$DATA_DIR" "$LOG_DIR"
}

program_prepare_bundle() {
  [[ -x "$BACKEND_BIN" ]] || program_die "missing backend binary: $BACKEND_BIN"
  [[ -f "$DIST_DIR/index.html" ]] || program_die "missing frontend dist: $DIST_DIR/index.html"
  program_prepare_runtime_dirs
}

program_backend_running() {
  [[ -f "$BACKEND_PID_FILE" ]] && kill -0 "$(cat "$BACKEND_PID_FILE")" >/dev/null 2>&1
}

program_start_backend() {
  if program_backend_running; then
    program_die "backend already running"
  fi
  "$BACKEND_BIN" >"$BACKEND_LOG" 2>&1 &
  local backend_pid=$!
  printf '%s\n' "$backend_pid" >"$BACKEND_PID_FILE"
  sleep 1
  if ! kill -0 "$backend_pid" >/dev/null 2>&1; then
    rm -f "$BACKEND_PID_FILE"
    program_die "backend failed to start; see $BACKEND_LOG"
  fi
}

program_stop_backend() {
  if [[ ! -f "$BACKEND_PID_FILE" ]]; then
    return
  fi
  local backend_pid
  backend_pid="$(cat "$BACKEND_PID_FILE")"
  if [[ -n "$backend_pid" ]] && kill -0 "$backend_pid" >/dev/null 2>&1; then
    kill "$backend_pid" >/dev/null 2>&1 || true
  fi
  rm -f "$BACKEND_PID_FILE"
}
