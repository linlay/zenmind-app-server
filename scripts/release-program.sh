#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
<<<<<<< HEAD
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
=======
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PROGRAM_ASSETS_DIR="$SCRIPT_DIR/release-program-assets"

APP_NAME="zenmind-app-server"
BINARY_NAME="app-server"

die() { echo "[release] $*" >&2; exit 1; }

detect_arch() {
  case "$(uname -m)" in
    x86_64|amd64) echo "amd64" ;;
    arm64|aarch64) echo "arm64" ;;
    *) die "cannot detect ARCH from $(uname -m); pass ARCH=amd64|arm64" ;;
  esac
}

validate_target_os() {
  case "$1" in
    darwin|windows|linux) ;;
    *) die "target OS must be darwin, windows, or linux (got: $1)" ;;
  esac
}

validate_arch() {
  case "$1" in
    amd64|arm64) ;;
    *) die "ARCH must be amd64 or arm64 (got: $1)" ;;
  esac
}

binary_name_for_os() {
  local target_os="$1"
  if [[ "$target_os" == "windows" ]]; then
    printf '%s.exe\n' "$BINARY_NAME"
  else
    printf '%s\n' "$BINARY_NAME"
  fi
}

parse_program_target_matrix() {
  local raw="${PROGRAM_TARGET_MATRIX:-}"
  if [[ -n "$raw" ]]; then
    raw="${raw//,/ }"
    for entry in $raw; do
      [[ "$entry" == */* ]] || die "PROGRAM_TARGET_MATRIX entries must be os/arch (got: $entry)"
      local os="${entry%%/*}"
      local arch="${entry#*/}"
      validate_target_os "$os"
      validate_arch "$arch"
      printf '%s %s\n' "$os" "$arch"
    done
    return
  fi
  printf 'darwin %s\n' "$ARCH"
}

VERSION="${VERSION:-$(cat "$REPO_ROOT/VERSION" 2>/dev/null || echo "dev")}"
[[ "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "VERSION must match vX.Y.Z (got: $VERSION)"
ARCH="${ARCH:-$(detect_arch)}"
validate_arch "$ARCH"
RELEASE_DIR="$REPO_ROOT/dist/release"

command -v go >/dev/null 2>&1 || die "go is required"
command -v npm >/dev/null 2>&1 || die "npm is required"
command -v tar >/dev/null 2>&1 || die "tar is required"

cd "$REPO_ROOT"

echo "[release] building frontend dist..."
(
  cd "$REPO_ROOT/frontend"
  npm ci
  VITE_BASE_PATH="/admin/" npm run build
)

build_program_bundle() {
  local target_os="$1"
  local target_arch="$2"
  local bin_name
  local bundle_name
  local bundle_tar
  local tmp_dir
  local bundle_root

  bin_name="$(binary_name_for_os "$target_os")"
  bundle_name="${APP_NAME}-program-${VERSION}-${target_os}-${target_arch}"
  bundle_tar="$RELEASE_DIR/${bundle_name}.tar.gz"

  echo "[release] program VERSION=$VERSION TARGET_OS=$target_os ARCH=$target_arch"

  tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/${APP_NAME}-program-release.XXXXXX")"
  trap 'rm -rf "$tmp_dir"' RETURN

  bundle_root="$tmp_dir/$APP_NAME"
  mkdir -p "$bundle_root/frontend" "$bundle_root/data"

  echo "[release] building program binary for $target_os..."
  (
    cd "$REPO_ROOT/backend"
    CGO_ENABLED=0 GOOS="$target_os" GOARCH="$target_arch" \
      go build \
      -trimpath -ldflags='-s -w -buildid=' \
      -o "$bundle_root/$bin_name" \
      ./cmd/server
  )

  echo "[release] assembling program bundle for $target_os..."
  cp "$REPO_ROOT/.env.example" "$bundle_root/.env.example"
  cp "$REPO_ROOT/backend/schema.sql" "$bundle_root/schema.sql"
  cp "$PROGRAM_ASSETS_DIR/README.txt" "$bundle_root/README.txt"
  cp -R "$REPO_ROOT/frontend/dist" "$bundle_root/frontend/dist"

  # Set program-mode defaults in .env.example
  sed -i.bak 's|^FRONTEND_PORT=.*$|SERVER_PORT=11950|' "$bundle_root/.env.example"
  rm -f "$bundle_root/.env.example.bak"
  if ! grep -q '^FRONTEND_DIST_DIR=' "$bundle_root/.env.example"; then
    printf '\nFRONTEND_DIST_DIR=./frontend/dist\n' >> "$bundle_root/.env.example"
  fi
  if ! grep -q '^AUTH_DB_PATH=' "$bundle_root/.env.example"; then
    printf 'AUTH_DB_PATH=./data/auth.db\n' >> "$bundle_root/.env.example"
  else
    sed -i.bak 's|^AUTH_DB_PATH=.*$|AUTH_DB_PATH=./data/auth.db|' "$bundle_root/.env.example"
    rm -f "$bundle_root/.env.example.bak"
  fi
  if ! grep -q '^AUTH_SCHEMA_PATH=' "$bundle_root/.env.example"; then
    printf 'AUTH_SCHEMA_PATH=./schema.sql\n' >> "$bundle_root/.env.example"
  fi

  if [[ "$target_os" == "windows" ]]; then
    echo "[release] windows start/stop scripts not yet supported for program mode"
  else
    cp "$PROGRAM_ASSETS_DIR/start.sh" "$bundle_root/start.sh"
    cp "$PROGRAM_ASSETS_DIR/stop.sh" "$bundle_root/stop.sh"
    chmod +x "$bundle_root/$bin_name" "$bundle_root/start.sh" "$bundle_root/stop.sh"
  fi

  mkdir -p "$RELEASE_DIR"
  tar -czf "$bundle_tar" -C "$tmp_dir" "$APP_NAME"

  echo "[release] done: $bundle_tar"
  rm -rf "$tmp_dir"
  trap - RETURN
}

while read -r target_os target_arch; do
  [[ -n "$target_os" ]] || continue
  [[ -n "$target_arch" ]] || die "missing ARCH for program target $target_os"
  build_program_bundle "$target_os" "$target_arch"
done < <(parse_program_target_matrix)
>>>>>>> 9df5df13e8ebdaf2169bf919e6593c62a42f095e
