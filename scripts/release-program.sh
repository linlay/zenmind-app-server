#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/release-common.sh"

PROGRAM_ASSETS_DIR="$RELEASE_ASSETS_DIR/program"

prepare_release_context
ensure_release_requirements
require_dir "$PROGRAM_ASSETS_DIR"
require_file "$PROGRAM_ASSETS_DIR/README.txt"
require_file "$PROGRAM_ASSETS_DIR/start.sh"
require_file "$PROGRAM_ASSETS_DIR/stop.sh"
require_file "$PROGRAM_ASSETS_DIR/start.cmd"
require_file "$PROGRAM_ASSETS_DIR/stop.cmd"
require_file "$PROGRAM_ASSETS_DIR/setup-public-key.sh"
require_file "$PROGRAM_ASSETS_DIR/issue-bridge-access-token.sh"
require_file "$PROGRAM_ASSETS_DIR/issue-bridge-runner-token.sh"
require_file "$PROGRAM_ASSETS_DIR/zenmind-app-server.service"

PROGRAM_TARGET_OUTPUT="$(program_target_matrix_lines)"
PROGRAM_TARGET_PAIRS=()
while IFS= read -r pair; do
  [[ -n "$pair" ]] || continue
  PROGRAM_TARGET_PAIRS+=("$pair")
done <<EOF
$PROGRAM_TARGET_OUTPUT
EOF
[[ "${#PROGRAM_TARGET_PAIRS[@]}" -gt 0 ]] || die "no program targets resolved"

log "VERSION=$VERSION"
log "PROGRAM_TARGETS=${PROGRAM_TARGET_PAIRS[*]}"
log "VITE_BASE_PATH=$VITE_BASE_PATH"
log "GOPROXY=$GOPROXY"
log "NPM_REGISTRY=$NPM_REGISTRY"
log "host build tools: go=$(go version | awk '{print $3}') npm=$(npm --version)"

if [[ "$RELEASE_DRY_RUN" == "1" ]]; then
  log "dry-run enabled; skipping host build and bundle assembly"
  exit 0
fi

ensure_release_dist
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/zenmind-program-release.XXXXXX")"
trap 'rm -rf "$TMP_DIR"' EXIT

FRONTEND_DIST_DIR="$TMP_DIR/frontend-dist"
build_frontend_dist "$FRONTEND_DIST_DIR"

for pair in "${PROGRAM_TARGET_PAIRS[@]}"; do
  target_os="${pair%%/*}"
  target_arch="${pair##*/}"
  stage_root="$TMP_DIR/${APP_NAME}-${target_os}-${target_arch}"
  bundle_root="$stage_root/$APP_NAME"
  backend_dir="$bundle_root/backend"
  frontend_dir="$bundle_root/frontend"
  config_dir="$bundle_root/config"
  data_dir="$bundle_root/data"
  run_dir="$bundle_root/run"
  backend_binary="$backend_dir/app"
  frontend_binary="$frontend_dir/frontend-gateway"
  if [[ "$target_os" == "windows" ]]; then
    backend_binary="$backend_dir/app.exe"
    frontend_binary="$frontend_dir/frontend-gateway.exe"
  fi

  mkdir -p "$backend_dir" "$frontend_dir/dist" "$config_dir" "$data_dir" "$run_dir"

  build_backend_binary "$target_os" "$target_arch" "$backend_binary"
  build_frontend_gateway_binary "$target_os" "$target_arch" "$frontend_binary"

  cp "$REPO_ROOT/backend/schema.sql" "$backend_dir/schema.sql"
  cp -R "$FRONTEND_DIST_DIR/." "$frontend_dir/dist/"
  copy_env_example_with_version "$bundle_root/.env.example"
  write_runtime_registry "$config_dir/config-files.runtime.yml"
  cp "$PROGRAM_ASSETS_DIR/README.txt" "$bundle_root/README.txt"
  cp "$PROGRAM_ASSETS_DIR/setup-public-key.sh" "$bundle_root/setup-public-key.sh"
  cp "$PROGRAM_ASSETS_DIR/issue-bridge-access-token.sh" "$bundle_root/issue-bridge-access-token.sh"
  cp "$PROGRAM_ASSETS_DIR/issue-bridge-runner-token.sh" "$bundle_root/issue-bridge-runner-token.sh"

  if [[ "$target_os" == "windows" ]]; then
    cp "$PROGRAM_ASSETS_DIR/start.cmd" "$bundle_root/start.cmd"
    cp "$PROGRAM_ASSETS_DIR/stop.cmd" "$bundle_root/stop.cmd"
  else
    cp "$PROGRAM_ASSETS_DIR/start.sh" "$bundle_root/start.sh"
    cp "$PROGRAM_ASSETS_DIR/stop.sh" "$bundle_root/stop.sh"
    chmod +x \
      "$bundle_root/start.sh" \
      "$bundle_root/stop.sh" \
      "$bundle_root/setup-public-key.sh" \
      "$bundle_root/issue-bridge-access-token.sh" \
      "$bundle_root/issue-bridge-runner-token.sh"
    if [[ "$target_os" == "linux" ]]; then
      cp "$PROGRAM_ASSETS_DIR/zenmind-app-server.service" "$bundle_root/zenmind-app-server.service"
    fi
  fi

  bundle_tar="$RELEASE_DIST_DIR/$(bundle_filename "program" "$VERSION" "$target_os" "$target_arch")"
  tar -czf "$bundle_tar" -C "$stage_root" "$APP_NAME"
  log "done: $bundle_tar"
done
