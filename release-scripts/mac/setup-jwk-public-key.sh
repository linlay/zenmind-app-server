#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MANAGE_JWK_SCRIPT="$SCRIPT_DIR/manage-jwk-key.sh"

MODE="bootstrap"
DB_PATH="${AUTH_DB_PATH:-./data/auth.db}"
KEY_OUT_DIR="${KEY_OUTPUT_DIR:-./data/keys}"
PUBLIC_OUT=""

usage() {
  cat <<'USAGE_EOF'
Usage:
  ./release-scripts/mac/setup-jwk-public-key.sh \
    [--mode bootstrap|rotate] \
    [--db <sqlite_db_path>] \
    [--out <key_output_dir>] \
    [--public-out <public_key_file_path>]

What it does:
  1) Ensure JWK key pair exists in auth db (or rotate when --mode rotate)
  2) Export jwk-public.pem / jwk-private.pem to --out
  3) Copy public key to --public-out (default: <out>/publicKey.pem)

Examples:
  ./release-scripts/mac/setup-jwk-public-key.sh --db ./data/auth.db --out ./data/keys
  ./release-scripts/mac/setup-jwk-public-key.sh --mode rotate --db ./data/auth.db --out ./data/keys --public-out ./data/keys/publicKey.pem
USAGE_EOF
}

log() {
  printf '[setup-jwk] %s\n' "$*"
}

while [ $# -gt 0 ]; do
  case "$1" in
    --mode)
      MODE="${2:-}"
      shift 2
      ;;
    --db)
      DB_PATH="${2:-}"
      shift 2
      ;;
    --out)
      KEY_OUT_DIR="${2:-}"
      shift 2
      ;;
    --public-out)
      PUBLIC_OUT="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      printf '[setup-jwk] unknown argument: %s\n' "$1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [ "$MODE" != "bootstrap" ] && [ "$MODE" != "rotate" ]; then
  printf '[setup-jwk] invalid --mode: %s (must be bootstrap or rotate)\n' "$MODE" >&2
  exit 1
fi

if [ -z "$PUBLIC_OUT" ]; then
  PUBLIC_OUT="$KEY_OUT_DIR/publicKey.pem"
fi

if [ ! -x "$MANAGE_JWK_SCRIPT" ]; then
  printf '[setup-jwk] manage script not executable: %s\n' "$MANAGE_JWK_SCRIPT" >&2
  exit 1
fi

"$MANAGE_JWK_SCRIPT" --mode "$MODE" --db "$DB_PATH" --out "$KEY_OUT_DIR"

PUBLIC_PEM="$KEY_OUT_DIR/jwk-public.pem"
if [ ! -f "$PUBLIC_PEM" ]; then
  printf '[setup-jwk] public key not found: %s\n' "$PUBLIC_PEM" >&2
  exit 1
fi

mkdir -p "$(dirname "$PUBLIC_OUT")"
cp "$PUBLIC_PEM" "$PUBLIC_OUT"

log "public key exported: $PUBLIC_OUT"
log "done"
