#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

. "$SCRIPT_DIR/scripts/program-common.sh"

cd "$SCRIPT_DIR"
program_load_env
program_prepare_bundle
program_start_backend

echo "[program-start] started zenmind-app-server"
