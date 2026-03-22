#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RELEASE_ASSETS_DIR="$SCRIPT_DIR/release-assets"

die() { echo "[release] $*" >&2; exit 1; }
require_file() { [[ -f "$1" ]] || die "missing required file: $1"; }

if [[ "${VERSION+x}" == x ]]; then
  VERSION="$VERSION"
else
  [[ -f "$REPO_ROOT/VERSION" ]] || die "missing VERSION file"
  VERSION="$(tr -d '[:space:]' < "$REPO_ROOT/VERSION")"
fi

[[ -n "$VERSION" ]] || die "VERSION must not be empty"
[[ "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "VERSION must match vX.Y.Z (got: $VERSION)"

if [[ -z "${ARCH:-}" ]]; then
  case "$(uname -m)" in
    x86_64|amd64) ARCH=amd64 ;;
    arm64|aarch64) ARCH=arm64 ;;
    *) die "cannot detect ARCH from $(uname -m); pass ARCH=amd64|arm64" ;;
  esac
fi

case "$ARCH" in
  amd64|arm64) ;;
  *) die "ARCH must be amd64 or arm64 (got: $ARCH)" ;;
esac

command -v docker >/dev/null 2>&1 || die "docker is required"
docker buildx version >/dev/null 2>&1 || die "docker buildx is required"
command -v go >/dev/null 2>&1 || die "go is required on the host for release builds"
command -v npm >/dev/null 2>&1 || die "npm is required on the host for release builds"

require_file "$REPO_ROOT/.env.example"
require_file "$REPO_ROOT/backend/Dockerfile.release"
require_file "$REPO_ROOT/frontend/Dockerfile.release"
require_file "$REPO_ROOT/backend/go.mod"
require_file "$REPO_ROOT/frontend/package.json"
require_file "$RELEASE_ASSETS_DIR/docker-compose.release.yml"
require_file "$RELEASE_ASSETS_DIR/start.sh"
require_file "$RELEASE_ASSETS_DIR/stop.sh"
require_file "$RELEASE_ASSETS_DIR/README.txt"

ROOT_ENV_SOURCE="$REPO_ROOT/.env.example"
if [[ -f "$REPO_ROOT/.env" ]]; then
  ROOT_ENV_SOURCE="$REPO_ROOT/.env"
fi

set -a
. "$ROOT_ENV_SOURCE"
set +a

DEFAULT_GO_BUILD_IMAGE="golang:1.23-alpine"
DEFAULT_NODE_BUILD_IMAGE="node:20-alpine"
DEFAULT_GOPROXY="https://goproxy.cn,direct"
DEFAULT_NPM_REGISTRY="https://registry.npmmirror.com"

GO_BUILD_IMAGE="${GO_BUILD_IMAGE:-$DEFAULT_GO_BUILD_IMAGE}"
NODE_BUILD_IMAGE="${NODE_BUILD_IMAGE:-$DEFAULT_NODE_BUILD_IMAGE}"
GOPROXY="${GOPROXY:-$DEFAULT_GOPROXY}"
NPM_REGISTRY="${NPM_REGISTRY:-$DEFAULT_NPM_REGISTRY}"
RELEASE_DRY_RUN="${RELEASE_DRY_RUN:-0}"

VITE_BASE_PATH="${VITE_BASE_PATH:-/admin/}"
PLATFORM="linux/$ARCH"
BACKEND_IMAGE="app-server-backend:$VERSION"
FRONTEND_IMAGE="app-server-frontend:$VERSION"
BUNDLE_NAME="zenmind-app-server-${VERSION}-linux-${ARCH}"
BUNDLE_TAR="$REPO_ROOT/dist/release/${BUNDLE_NAME}.tar.gz"

echo "[release] VERSION=$VERSION ARCH=$ARCH PLATFORM=$PLATFORM"
echo "[release] VITE_BASE_PATH=$VITE_BASE_PATH"
echo "[release] GO_BUILD_IMAGE=$GO_BUILD_IMAGE"
echo "[release] NODE_BUILD_IMAGE=$NODE_BUILD_IMAGE"
echo "[release] GOPROXY=$GOPROXY"
echo "[release] NPM_REGISTRY=$NPM_REGISTRY"
echo "[release] host build tools: go=$(go version | awk '{print $3}') npm=$(npm --version)"

if [[ "$RELEASE_DRY_RUN" == "1" ]]; then
  echo "[release] dry-run enabled; skipping host build and bundle assembly"
  exit 0
fi

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/zenmind-release.XXXXXX")"
trap 'rm -rf "$TMP_DIR"' EXIT

IMAGES_DIR="$TMP_DIR/images"
BUNDLE_ROOT="$TMP_DIR/zenmind-app-server"
BACKEND_BUILD_DIR="$TMP_DIR/backend-build"
FRONTEND_BUILD_DIR="$TMP_DIR/frontend-build"
mkdir -p "$IMAGES_DIR" "$BUNDLE_ROOT/images" "$BUNDLE_ROOT/data"
mkdir -p "$BACKEND_BUILD_DIR" "$FRONTEND_BUILD_DIR"

echo "[release] building backend binary on host..."
(
  cd "$REPO_ROOT/backend"
  GOPROXY="$GOPROXY" \
  GOOS=linux \
  GOARCH="$ARCH" \
  CGO_ENABLED=0 \
  go build -trimpath -ldflags='-s -w -buildid=' -o "$BACKEND_BUILD_DIR/app" ./cmd/server
)
cp "$REPO_ROOT/backend/schema.sql" "$BACKEND_BUILD_DIR/schema.sql"

echo "[release] building frontend assets on host..."
(
  cd "$REPO_ROOT/frontend"
  npm config set registry "$NPM_REGISTRY" >/dev/null
  npm ci
  VITE_BASE_PATH="$VITE_BASE_PATH" npm run build
)
cp -R "$REPO_ROOT/frontend/dist/." "$FRONTEND_BUILD_DIR/dist/"

echo "[release] building frontend gateway binary on host..."
(
  cd "$REPO_ROOT/frontend"
  GOPROXY="$GOPROXY" \
  GOOS=linux \
  GOARCH="$ARCH" \
  CGO_ENABLED=0 \
  go build -trimpath -ldflags='-s -w -buildid=' -o "$FRONTEND_BUILD_DIR/frontend-gateway" ./proxy/main.go
)

echo "[release] packaging backend image..."
docker buildx build \
  --platform "$PLATFORM" \
  --file "$REPO_ROOT/backend/Dockerfile.release" \
  --tag "$BACKEND_IMAGE" \
  --output "type=docker,dest=$IMAGES_DIR/app-server-backend.tar" \
  "$BACKEND_BUILD_DIR"

echo "[release] packaging frontend image..."
docker buildx build \
  --platform "$PLATFORM" \
  --file "$REPO_ROOT/frontend/Dockerfile.release" \
  --tag "$FRONTEND_IMAGE" \
  --output "type=docker,dest=$IMAGES_DIR/app-server-frontend.tar" \
  "$FRONTEND_BUILD_DIR"

cp "$RELEASE_ASSETS_DIR/docker-compose.release.yml" "$BUNDLE_ROOT/docker-compose.release.yml"
cp "$RELEASE_ASSETS_DIR/start.sh" "$BUNDLE_ROOT/start.sh"
cp "$RELEASE_ASSETS_DIR/stop.sh" "$BUNDLE_ROOT/stop.sh"
cp "$RELEASE_ASSETS_DIR/README.txt" "$BUNDLE_ROOT/README.txt"
cp "$REPO_ROOT/.env.example" "$BUNDLE_ROOT/.env.example"
cp "$IMAGES_DIR/app-server-backend.tar" "$BUNDLE_ROOT/images/app-server-backend.tar"
cp "$IMAGES_DIR/app-server-frontend.tar" "$BUNDLE_ROOT/images/app-server-frontend.tar"

sed -i.bak "s/^APP_SERVER_VERSION=.*/APP_SERVER_VERSION=$VERSION/" "$BUNDLE_ROOT/.env.example"
rm -f "$BUNDLE_ROOT/.env.example.bak"
grep -q "^APP_SERVER_VERSION=$VERSION$" "$BUNDLE_ROOT/.env.example" || die "failed to set APP_SERVER_VERSION in bundle .env.example"

chmod +x "$BUNDLE_ROOT/start.sh" "$BUNDLE_ROOT/stop.sh"

mkdir -p "$(dirname "$BUNDLE_TAR")"
tar -czf "$BUNDLE_TAR" -C "$TMP_DIR" zenmind-app-server

echo "[release] done: $BUNDLE_TAR"
