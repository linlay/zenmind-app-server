#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

. "$SCRIPT_DIR/scripts/program-common.sh"

cd "$SCRIPT_DIR"
program_load_env
program_prepare_bundle
program_require_nginx
program_render_nginx_conf
program_test_nginx_conf

echo "[program-deploy] backend binary: $BACKEND_BIN"
echo "[program-deploy] nginx template: $NGINX_TEMPLATE"
echo "[program-deploy] rendered nginx config: $NGINX_RENDERED_CONF"
echo "[program-deploy] browser: http://127.0.0.1:${FRONTEND_PORT}/admin/"
