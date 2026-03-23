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

What it does:
  1) Ensure JWK key pair exists in auth db (or rotate when --mode rotate)
  2) Export jwk-public.pem / jwk-private.pem to --out
  3) Copy public key to --public-out (default: <out>/publicKey.pem)

Examples:
  ./$SCRIPT_NAME --db ./data/auth.db --out ./data/keys
  ./$SCRIPT_NAME --mode rotate --db ./data/auth.db --out ./data/keys --public-out ./data/keys/publicKey.pem
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
    --mode)
      MODE="${2:-}"
      shift 2
      ;;
    --db)
      DB_PATH="${2:-}"
      shift 2
      ;;
    --out)
      OUT_DIR="${2:-}"
      shift 2
      ;;
    --public-out)
      PUBLIC_OUT="${2:-}"
      shift 2
      ;;
    --key-id)
      KEY_ID="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      printf '[setup-public-key] unknown argument: %s\n' "$1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [ "$MODE" != "bootstrap" ] && [ "$MODE" != "rotate" ]; then
  printf '[setup-public-key] invalid --mode: %s (must be bootstrap or rotate)\n' "$MODE" >&2
  exit 1
fi

if [ -n "$KEY_ID" ] && ! printf '%s' "$KEY_ID" | grep -Eq '^[A-Za-z0-9._-]+$'; then
  printf '[setup-public-key] invalid --key-id; allowed chars: A-Za-z0-9._-\n' >&2
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

export_pair_from_base64() {
  local key_id="$1"
  local public_b64="$2"
  local private_b64="$3"
  local public_der="$TMP_DIR/public.der"
  local private_der="$TMP_DIR/private.der"

  printf '%s' "$public_b64" | openssl base64 -A -d >"$public_der"
  printf '%s' "$private_b64" | openssl base64 -A -d >"$private_der"

  openssl pkey -pubin -inform DER -outform PEM -in "$public_der" -out "$OUT_DIR/jwk-public.pem" >/dev/null 2>&1
  openssl pkcs8 -inform DER -outform PEM -nocrypt -in "$private_der" -out "$OUT_DIR/jwk-private.pem" >/dev/null 2>&1

  mkdir -p "$(dirname "$PUBLIC_OUT")"
  cp "$OUT_DIR/jwk-public.pem" "$PUBLIC_OUT"

  log "exported existing key pair (kid=$key_id)"
}

existing_row="$(sqlite3 -noheader "$DB_PATH" "SELECT KEY_ID_ || '|' || PUBLIC_KEY_ || '|' || PRIVATE_KEY_ FROM JWK_KEY_ ORDER BY CREATE_AT_ ASC LIMIT 1;")"

if [ "$MODE" = "bootstrap" ] && [ -n "$existing_row" ]; then
  IFS='|' read -r existing_kid existing_pub_b64 existing_priv_b64 <<<"$existing_row"
  export_pair_from_base64 "$existing_kid" "$existing_pub_b64" "$existing_priv_b64"
  log "public key:  $OUT_DIR/jwk-public.pem"
  log "private key: $OUT_DIR/jwk-private.pem"
  log "public key exported: $PUBLIC_OUT"
  log "done"
  exit 0
fi

if [ "$MODE" = "rotate" ]; then
  sqlite3 "$DB_PATH" "DELETE FROM JWK_KEY_;"
  log "cleared existing rows in JWK_KEY_"
fi

if [ -z "$KEY_ID" ]; then
  KEY_ID="$(openssl rand -hex 16)"
fi

private_pem="$TMP_DIR/private.pem"
public_pem="$TMP_DIR/public.pem"

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$private_pem" >/dev/null 2>&1
openssl rsa -in "$private_pem" -pubout -out "$public_pem" >/dev/null 2>&1

private_b64="$(openssl pkcs8 -topk8 -inform PEM -outform DER -nocrypt -in "$private_pem" | openssl base64 -A)"
public_b64="$(openssl pkey -pubin -inform PEM -outform DER -in "$public_pem" | openssl base64 -A)"

sqlite3 "$DB_PATH" "INSERT INTO JWK_KEY_(KEY_ID_, PUBLIC_KEY_, PRIVATE_KEY_, CREATE_AT_) VALUES ('$KEY_ID', '$public_b64', '$private_b64', CURRENT_TIMESTAMP);"

cp "$private_pem" "$OUT_DIR/jwk-private.pem"
cp "$public_pem" "$OUT_DIR/jwk-public.pem"

mkdir -p "$(dirname "$PUBLIC_OUT")"
cp "$public_pem" "$PUBLIC_OUT"

log "generated and stored new key pair (kid=$KEY_ID)"
log "db path:      $DB_PATH"
log "public key:   $OUT_DIR/jwk-public.pem"
log "private key:  $OUT_DIR/jwk-private.pem"
log "public key exported: $PUBLIC_OUT"
if [ "$MODE" = "rotate" ]; then
  log "note: rotating key invalidates previously issued app access tokens."
  log "note: restart backend after rotate so OAuth2 JWK source reloads the new key."
fi
log "done"
