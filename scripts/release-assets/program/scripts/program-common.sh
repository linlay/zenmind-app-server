#!/usr/bin/env bash
set -euo pipefail

PROGRAM_COMMON_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUNDLE_ROOT="$(cd "$PROGRAM_COMMON_DIR/.." && pwd)"
ENV_FILE="$BUNDLE_ROOT/.env"
BACKEND_BIN="$BUNDLE_ROOT/backend/app"
FRONTEND_DIR="$BUNDLE_ROOT/frontend"
DIST_DIR="$FRONTEND_DIR/dist"
NGINX_TEMPLATE="$FRONTEND_DIR/nginx.conf"
RUN_DIR="$BUNDLE_ROOT/run"
LOG_DIR="$RUN_DIR/logs"
NGINX_PREFIX_DIR="$RUN_DIR/nginx"
NGINX_RENDERED_CONF="$RUN_DIR/nginx.conf"
NGINX_PID_FILE="$NGINX_PREFIX_DIR/logs/nginx.pid"
NGINX_ACCESS_LOG="$LOG_DIR/nginx.access.log"
NGINX_ERROR_LOG="$LOG_DIR/nginx.error.log"
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
  FRONTEND_PORT="${FRONTEND_PORT:-11950}"
  NGINX_BIN="${NGINX_BIN:-nginx}"
  AUTH_DB_PATH="${AUTH_DB_PATH:-./data/auth.db}"
  export SERVER_PORT FRONTEND_PORT NGINX_BIN AUTH_DB_PATH
}

program_load_env_optional() {
  if [[ -f "$ENV_FILE" ]]; then
    program_load_env
    return
  fi
  SERVER_PORT="${SERVER_PORT:-18080}"
  FRONTEND_PORT="${FRONTEND_PORT:-11950}"
  NGINX_BIN="${NGINX_BIN:-nginx}"
  AUTH_DB_PATH="${AUTH_DB_PATH:-./data/auth.db}"
  export SERVER_PORT FRONTEND_PORT NGINX_BIN AUTH_DB_PATH
}

program_prepare_runtime_dirs() {
  mkdir -p "$DATA_DIR" "$LOG_DIR" "$NGINX_PREFIX_DIR/logs"
}

program_prepare_bundle() {
  [[ -x "$BACKEND_BIN" ]] || program_die "missing backend binary: $BACKEND_BIN"
  [[ -f "$DIST_DIR/index.html" ]] || program_die "missing frontend dist: $DIST_DIR/index.html"
  [[ -f "$NGINX_TEMPLATE" ]] || program_die "missing nginx config template: $NGINX_TEMPLATE"
  program_prepare_runtime_dirs
}

program_require_nginx() {
  if [[ "$NGINX_BIN" == */* ]]; then
    [[ -x "$NGINX_BIN" ]] || program_die "nginx binary is not executable: $NGINX_BIN"
    return
  fi
  command -v "$NGINX_BIN" >/dev/null 2>&1 || program_die "nginx is required; set NGINX_BIN if nginx is not on PATH"
}

program_sed_escape() {
  printf '%s' "$1" | sed -e 's/[\/&]/\\&/g'
}

program_render_nginx_conf() {
  local dist_dir_escaped nginx_pid_file_escaped nginx_access_log_escaped nginx_error_log_escaped
  dist_dir_escaped="$(program_sed_escape "$DIST_DIR")"
  nginx_pid_file_escaped="$(program_sed_escape "$NGINX_PID_FILE")"
  nginx_access_log_escaped="$(program_sed_escape "$NGINX_ACCESS_LOG")"
  nginx_error_log_escaped="$(program_sed_escape "$NGINX_ERROR_LOG")"

  sed \
    -e "s/__DIST_DIR__/$dist_dir_escaped/g" \
    -e "s/__SERVER_PORT__/$SERVER_PORT/g" \
    -e "s/__FRONTEND_PORT__/$FRONTEND_PORT/g" \
    -e "s/__NGINX_PID_FILE__/$nginx_pid_file_escaped/g" \
    -e "s/__NGINX_ACCESS_LOG__/$nginx_access_log_escaped/g" \
    -e "s/__NGINX_ERROR_LOG__/$nginx_error_log_escaped/g" \
    "$NGINX_TEMPLATE" >"$NGINX_RENDERED_CONF"
}

program_test_nginx_conf() {
  "$NGINX_BIN" -t -p "$NGINX_PREFIX_DIR" -c "$NGINX_RENDERED_CONF"
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

program_nginx_running() {
  [[ -f "$NGINX_PID_FILE" ]] && kill -0 "$(cat "$NGINX_PID_FILE")" >/dev/null 2>&1
}

program_start_or_reload_nginx() {
  if program_nginx_running; then
    "$NGINX_BIN" -p "$NGINX_PREFIX_DIR" -c "$NGINX_RENDERED_CONF" -s reload
    return
  fi
  "$NGINX_BIN" -p "$NGINX_PREFIX_DIR" -c "$NGINX_RENDERED_CONF"
}

program_stop_nginx() {
  if [[ ! -f "$NGINX_RENDERED_CONF" ]]; then
    return
  fi
  if program_nginx_running; then
    "$NGINX_BIN" -p "$NGINX_PREFIX_DIR" -c "$NGINX_RENDERED_CONF" -s stop >/dev/null 2>&1 || true
  fi
}
