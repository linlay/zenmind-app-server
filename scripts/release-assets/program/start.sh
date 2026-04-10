#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

. "$SCRIPT_DIR/scripts/program-common.sh"

cd "$SCRIPT_DIR"
program_load_env
program_prepare_bundle
program_require_nginx
program_render_nginx_conf
program_start_backend
program_start_or_reload_nginx

echo "[program-start] started zenmind-app-server"
echo "[program-start] browser: http://127.0.0.1:${FRONTEND_PORT}/admin/"
