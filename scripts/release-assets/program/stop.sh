#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

. "$SCRIPT_DIR/scripts/program-common.sh"

cd "$SCRIPT_DIR"
program_load_env_optional
program_prepare_runtime_dirs
program_stop_nginx
program_stop_backend
echo "[program-stop] stopped zenmind-app-server"
