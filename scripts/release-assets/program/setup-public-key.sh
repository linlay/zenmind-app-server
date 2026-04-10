#!/usr/bin/env bash
set -euo pipefail

SCRIPT_NAME="$(basename "$0")"

MODE="bootstrap"
DB_PATH="${AUTH_DB_PATH:-./data/auth.db}"
OUT_DIR="${KEY_OUTPUT_DIR:-./data/keys}"
PUBLIC_OUT=""
KEY_ID="${JWK_KEY_ID:-}"

usage() {
  cat <<EOF
Usage:
  ./$SCRIPT_NAME [--mode bootstrap|rotate] [--db <sqlite_db_path>] [--out <output_dir>] [--public-out <public_key_file_path>] [--key-id <kid>]
EOF
}

log() {
  printf '[setup-public-key] %s\n' "$*"
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf '[setup-public-key] missing required command: %s\n' "$1" >&2
    exit 1
  fi
}

while [ $# -gt 0 ]; do
  case "$1" in
    --mode) MODE="${2:-}"; shift 2 ;;
    --db) DB_PATH="${2:-}"; shift 2 ;;
    --out) OUT_DIR="${2:-}"; shift 2 ;;
    --public-out) PUBLIC_OUT="${2:-}"; shift 2 ;;
    --key-id) KEY_ID="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) printf '[setup-public-key] unknown argument: %s\n' "$1" >&2; usage >&2; exit 1 ;;
  esac
done

if [ "$MODE" != "bootstrap" ] && [ "$MODE" != "rotate" ]; then
  printf '[setup-public-key] invalid --mode: %s (must be bootstrap or rotate)\n' "$MODE" >&2
  exit 1
fi

if [ -z "$PUBLIC_OUT" ]; then
  PUBLIC_OUT="$OUT_DIR/publicKey.pem"
fi

require_cmd openssl
require_cmd sqlite3

mkdir -p "$(dirname "$DB_PATH")" "$OUT_DIR"

sqlite3 "$DB_PATH" <<'SQL'
CREATE TABLE IF NOT EXISTS JWK_KEY_ (
  KEY_ID_ TEXT PRIMARY KEY,
  PUBLIC_KEY_ TEXT NOT NULL,
  PRIVATE_KEY_ TEXT NOT NULL,
  CREATE_AT_ TIMESTAMP NOT NULL
);
SQL

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

existing_row="$(sqlite3 -noheader "$DB_PATH" "SELECT KEY_ID_ || '|' || PUBLIC_KEY_ || '|' || PRIVATE_KEY_ FROM JWK_KEY_ ORDER BY CREATE_AT_ ASC LIMIT 1;")"
if [ "$MODE" = "bootstrap" ] && [ -n "$existing_row" ]; then
  IFS='|' read -r existing_kid existing_pub_b64 existing_priv_b64 <<EOF
$existing_row
EOF
  printf '%s' "$existing_pub_b64" | openssl base64 -A -d >"$TMP_DIR/public.der"
  printf '%s' "$existing_priv_b64" | openssl base64 -A -d >"$TMP_DIR/private.der"
  openssl pkey -pubin -inform DER -outform PEM -in "$TMP_DIR/public.der" -out "$OUT_DIR/jwk-public.pem" >/dev/null 2>&1
  openssl pkcs8 -inform DER -outform PEM -nocrypt -in "$TMP_DIR/private.der" -out "$OUT_DIR/jwk-private.pem" >/dev/null 2>&1
  cp "$OUT_DIR/jwk-public.pem" "$PUBLIC_OUT"
  log "exported existing key pair (kid=$existing_kid)"
  exit 0
fi

if [ "$MODE" = "rotate" ]; then
  sqlite3 "$DB_PATH" "DELETE FROM JWK_KEY_;"
fi

if [ -z "$KEY_ID" ]; then
  KEY_ID="$(openssl rand -hex 16)"
fi

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$TMP_DIR/private.pem" >/dev/null 2>&1
openssl rsa -in "$TMP_DIR/private.pem" -pubout -out "$TMP_DIR/public.pem" >/dev/null 2>&1

private_b64="$(openssl pkcs8 -topk8 -inform PEM -outform DER -nocrypt -in "$TMP_DIR/private.pem" | openssl base64 -A)"
public_b64="$(openssl pkey -pubin -inform PEM -outform DER -in "$TMP_DIR/public.pem" | openssl base64 -A)"

sqlite3 "$DB_PATH" "INSERT INTO JWK_KEY_(KEY_ID_, PUBLIC_KEY_, PRIVATE_KEY_, CREATE_AT_) VALUES ('$KEY_ID', '$public_b64', '$private_b64', CURRENT_TIMESTAMP);"

cp "$TMP_DIR/private.pem" "$OUT_DIR/jwk-private.pem"
cp "$TMP_DIR/public.pem" "$OUT_DIR/jwk-public.pem"
mkdir -p "$(dirname "$PUBLIC_OUT")"
cp "$TMP_DIR/public.pem" "$PUBLIC_OUT"

log "generated and stored new key pair (kid=$KEY_ID)"
