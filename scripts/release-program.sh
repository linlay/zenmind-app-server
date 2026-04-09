#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
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
