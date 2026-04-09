#!/usr/bin/env bash
set -euo pipefail

APP_NAME="app-server"
BINARY_NAME="app-server"
RUNTIME_DIR=".runtime"
PID_FILE="$RUNTIME_DIR/$APP_NAME.pid"
LOG_FILE="$RUNTIME_DIR/$APP_NAME.log"

die() {
  echo "[start] $*" >&2
  exit 1
}

require_file() {
  local path="$1"
  [[ -e "$path" ]] || die "required file not found: $path"
}

ensure_bundle_root() {
  require_file "./$BINARY_NAME"
  require_file "./.env.example"
  require_file "./frontend/dist/index.html"
  require_file "./schema.sql"
}

load_env() {
  [[ -f ./.env ]] || die ".env not found; run: cp .env.example .env"
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
}

ensure_runtime_dirs() {
  mkdir -p "$RUNTIME_DIR" ./data
}

check_stale_pid() {
  if [[ ! -f "$PID_FILE" ]]; then
    return
  fi
  local pid
  pid="$(cat "$PID_FILE")"
  if [[ -n "$pid" ]] && kill -0 "$pid" >/dev/null 2>&1; then
    die "$APP_NAME is already running with pid $pid"
  fi
  rm -f "$PID_FILE"
}

start_daemon() {
  check_stale_pid
  : >"$LOG_FILE"
  nohup "./$BINARY_NAME" >>"$LOG_FILE" 2>&1 &
  local pid=$!
  echo "$pid" >"$PID_FILE"
  sleep 1
  if ! kill -0 "$pid" >/dev/null 2>&1; then
    rm -f "$PID_FILE"
    die "daemon failed to start; check $LOG_FILE"
  fi
  local port="${SERVER_PORT:-11950}"
  echo "[start] started $APP_NAME in daemon mode (pid=$pid)"
  echo "[start] log file: $LOG_FILE"
  echo "[start] browser: http://127.0.0.1:${port}/admin/"
}

main() {
  local mode="${1:-}"
  if [[ -n "$mode" && "$mode" != "--daemon" ]]; then
    die "unsupported argument: $mode"
  fi

  ensure_bundle_root
  load_env
  ensure_runtime_dirs

  if [[ "$mode" == "--daemon" ]]; then
    start_daemon
    return
  fi

  exec "./$BINARY_NAME"
}

main "$@"
