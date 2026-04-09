#!/usr/bin/env bash
set -euo pipefail

APP_NAME="app-server"
PID_FILE=".runtime/$APP_NAME.pid"

die() {
  echo "[stop] $*" >&2
  exit 1
}

[[ -f "$PID_FILE" ]] || die "pid file not found: $PID_FILE"

pid="$(cat "$PID_FILE")"
[[ -n "$pid" ]] || die "pid file is empty: $PID_FILE"

if ! kill -0 "$pid" >/dev/null 2>&1; then
  rm -f "$PID_FILE"
  die "process $pid is not running; removed stale pid file"
fi

kill "$pid"

for _ in $(seq 1 30); do
  if ! kill -0 "$pid" >/dev/null 2>&1; then
    rm -f "$PID_FILE"
    echo "[stop] stopped $APP_NAME (pid=$pid)"
    exit 0
  fi
  sleep 1
done

die "process $pid did not stop within 30s"
