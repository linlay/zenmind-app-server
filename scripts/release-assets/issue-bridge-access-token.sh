#!/usr/bin/env bash
set -euo pipefail

SCRIPT_NAME="$(basename "$0")"

DB_PATH="${AUTH_DB_PATH:-./data/auth.db}"
ISSUER="${AUTH_ISSUER:-http://localhost:8080}"
USERNAME="${AUTH_APP_USERNAME:-app}"
DEVICE_NAME="WeChat Bridge"
PLACEHOLDER_DEVICE_TOKEN_BCRYPT='$2a$10$7J8GmW8J0tR9o5Z8L4m5Uuu6fQW4j6mJjM7qY0Q8n2rM5b3y1fVwK'

usage() {
  cat <<EOF
Usage:
  ./$SCRIPT_NAME [--db <sqlite_db_path>] [--issuer <issuer>] [--username <subject>] [--device-name <bridge_device_name>]

What it does:
  1) Reuse or create one ACTIVE bridge device in SQLite
  2) Sign an RS256 app access token without exp using the first JWK private key
  3) Upsert a TOKEN_AUDIT_ record with EXPIRES_AT_ = NULL

Examples:
  ./$SCRIPT_NAME
  ./$SCRIPT_NAME --device-name "wechat-bridge"
  AUTH_ISSUER=https://auth.example.com ./$SCRIPT_NAME
EOF
}

log() {
  printf '[issue-bridge-access-token] %s\n' "$*" >&2
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log "missing required command: $1"
    exit 1
  fi
}

trim() {
  printf '%s' "$1" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//'
}

json_escape() {
  local input="$1"
  local out=""
  local i char
  for ((i = 0; i < ${#input}; i++)); do
    char="${input:i:1}"
    case "$char" in
      '\\') out="${out}\\\\" ;;
      '"') out="${out}\\\"" ;;
      $'\n') out="${out}\\n" ;;
      $'\r') out="${out}\\r" ;;
      $'\t') out="${out}\\t" ;;
      *) out="${out}${char}" ;;
    esac
  done
  printf '%s' "$out"
}

base64url() {
  openssl base64 -A | tr '+/' '-_' | tr -d '='
}

make_uuid() {
  local hex
  hex="$(openssl rand -hex 16)"
  printf '%s-%s-%s-%s-%s' \
    "${hex:0:8}" \
    "${hex:8:4}" \
    "${hex:12:4}" \
    "${hex:16:4}" \
    "${hex:20:12}"
}

while [ $# -gt 0 ]; do
  case "$1" in
    --db)
      DB_PATH="${2:-}"
      shift 2
      ;;
    --issuer)
      ISSUER="${2:-}"
      shift 2
      ;;
    --username)
      USERNAME="${2:-}"
      shift 2
      ;;
    --device-name)
      DEVICE_NAME="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      log "unknown argument: $1"
      usage >&2
      exit 1
      ;;
  esac
done

DB_PATH="$(trim "$DB_PATH")"
ISSUER="$(trim "$ISSUER")"
USERNAME="$(trim "$USERNAME")"
DEVICE_NAME="$(trim "$DEVICE_NAME")"

if [ -z "$DB_PATH" ]; then
  log "db path is required"
  exit 1
fi
if [ -z "$ISSUER" ]; then
  log "issuer is required"
  exit 1
fi
if [ -z "$USERNAME" ]; then
  log "username is required"
  exit 1
fi
if [ -z "$DEVICE_NAME" ]; then
  log "device name is required"
  exit 1
fi
if [ ${#DEVICE_NAME} -gt 64 ]; then
  DEVICE_NAME="${DEVICE_NAME:0:64}"
fi

require_cmd openssl
require_cmd sqlite3

mkdir -p "$(dirname "$DB_PATH")"

sqlite3 "$DB_PATH" <<'SQL'
CREATE TABLE IF NOT EXISTS JWK_KEY_ (
  KEY_ID_ TEXT PRIMARY KEY,
  PUBLIC_KEY_ TEXT NOT NULL,
  PRIVATE_KEY_ TEXT NOT NULL,
  CREATE_AT_ TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS DEVICE_ (
  DEVICE_ID_ TEXT PRIMARY KEY,
  DEVICE_NAME_ TEXT NOT NULL,
  DEVICE_TOKEN_BCRYPT_ TEXT NOT NULL,
  STATUS_ TEXT NOT NULL DEFAULT 'ACTIVE',
  LAST_SEEN_AT_ TIMESTAMP,
  REVOKED_AT_ TIMESTAMP,
  CREATE_AT_ TIMESTAMP NOT NULL,
  UPDATE_AT_ TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS TOKEN_AUDIT_ (
  TOKEN_ID_ TEXT PRIMARY KEY,
  SOURCE_ TEXT NOT NULL,
  TOKEN_VALUE_ TEXT NOT NULL,
  TOKEN_SHA256_ TEXT NOT NULL UNIQUE,
  USERNAME_ TEXT,
  DEVICE_ID_ TEXT,
  DEVICE_NAME_ TEXT,
  CLIENT_ID_ TEXT,
  AUTHORIZATION_ID_ TEXT,
  ISSUED_AT_ TIMESTAMP NOT NULL,
  EXPIRES_AT_ TIMESTAMP,
  REVOKED_AT_ TIMESTAMP,
  CREATE_AT_ TIMESTAMP NOT NULL,
  UPDATE_AT_ TIMESTAMP NOT NULL
);
SQL

key_row="$(sqlite3 -separator '|' -noheader "$DB_PATH" "SELECT KEY_ID_, PRIVATE_KEY_ FROM JWK_KEY_ ORDER BY CREATE_AT_ ASC LIMIT 1;")"
if [ -z "$key_row" ]; then
  log "no JWK key found in $DB_PATH; run ./setup-public-key.sh first"
  exit 1
fi
IFS='|' read -r KEY_ID PRIVATE_KEY_B64 <<EOF
$key_row
EOF

if [ -z "$KEY_ID" ] || [ -z "$PRIVATE_KEY_B64" ]; then
  log "invalid JWK key row in $DB_PATH"
  exit 1
fi

escaped_device_name="$(printf "%s" "$DEVICE_NAME" | sed "s/'/''/g")"
device_row="$(sqlite3 -separator '|' -noheader "$DB_PATH" "SELECT DEVICE_ID_ FROM DEVICE_ WHERE STATUS_ = 'ACTIVE' AND DEVICE_NAME_ = '$escaped_device_name' ORDER BY UPDATE_AT_ DESC LIMIT 1;")"
now_sql="$(date -u '+%Y-%m-%d %H:%M:%S')"
if [ -n "$device_row" ]; then
  DEVICE_ID="$device_row"
  sqlite3 "$DB_PATH" "UPDATE DEVICE_ SET LAST_SEEN_AT_ = '$now_sql', UPDATE_AT_ = '$now_sql' WHERE DEVICE_ID_ = '$DEVICE_ID' AND STATUS_ = 'ACTIVE';" >/dev/null
else
  DEVICE_ID="$(make_uuid)"
  escaped_placeholder="$(printf "%s" "$PLACEHOLDER_DEVICE_TOKEN_BCRYPT" | sed "s/'/''/g")"
  sqlite3 "$DB_PATH" "INSERT INTO DEVICE_(DEVICE_ID_, DEVICE_NAME_, DEVICE_TOKEN_BCRYPT_, STATUS_, LAST_SEEN_AT_, REVOKED_AT_, CREATE_AT_, UPDATE_AT_) VALUES('$DEVICE_ID', '$escaped_device_name', '$escaped_placeholder', 'ACTIVE', '$now_sql', NULL, '$now_sql', '$now_sql');" >/dev/null
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
PRIVATE_DER="$TMP_DIR/private.der"
PRIVATE_PEM="$TMP_DIR/private.pem"
SIGNING_INPUT="$TMP_DIR/signing-input.txt"
SIGNATURE_BIN="$TMP_DIR/signature.bin"

printf '%s' "$PRIVATE_KEY_B64" | openssl base64 -A -d >"$PRIVATE_DER"
openssl pkcs8 -inform DER -outform PEM -nocrypt -in "$PRIVATE_DER" -out "$PRIVATE_PEM" >/dev/null 2>&1

iat="$(date -u +%s)"
jti="$(make_uuid)"
header_json="$(printf '{"alg":"RS256","kid":"%s","typ":"JWT"}' "$(json_escape "$KEY_ID")")"
payload_json="$(printf '{"iss":"%s","sub":"%s","iat":%s,"jti":"%s","scope":"app","device_id":"%s"}' \
  "$(json_escape "$ISSUER")" \
  "$(json_escape "$USERNAME")" \
  "$iat" \
  "$(json_escape "$jti")" \
  "$(json_escape "$DEVICE_ID")")"

header_b64="$(printf '%s' "$header_json" | base64url)"
payload_b64="$(printf '%s' "$payload_json" | base64url)"
signing_input_text="${header_b64}.${payload_b64}"
printf '%s' "$signing_input_text" >"$SIGNING_INPUT"
openssl dgst -sha256 -sign "$PRIVATE_PEM" -out "$SIGNATURE_BIN" "$SIGNING_INPUT" >/dev/null 2>&1
signature_b64="$(base64url <"$SIGNATURE_BIN")"
token="${signing_input_text}.${signature_b64}"

token_sha256="$(printf '%s' "$token" | openssl dgst -sha256 -hex | awk '{print $NF}')"
token_id="$(make_uuid)"
escaped_issuer_username="$(printf "%s" "$USERNAME" | sed "s/'/''/g")"
escaped_token="$(printf "%s" "$token" | sed "s/'/''/g")"
escaped_token_sha256="$(printf "%s" "$token_sha256" | sed "s/'/''/g")"
sqlite3 "$DB_PATH" "INSERT INTO TOKEN_AUDIT_(TOKEN_ID_, SOURCE_, TOKEN_VALUE_, TOKEN_SHA256_, USERNAME_, DEVICE_ID_, DEVICE_NAME_, CLIENT_ID_, AUTHORIZATION_ID_, ISSUED_AT_, EXPIRES_AT_, REVOKED_AT_, CREATE_AT_, UPDATE_AT_) VALUES('$token_id', 'APP_ACCESS', '$escaped_token', '$escaped_token_sha256', '$escaped_issuer_username', '$DEVICE_ID', '$escaped_device_name', NULL, NULL, '$now_sql', NULL, NULL, '$now_sql', '$now_sql') ON CONFLICT(TOKEN_SHA256_) DO UPDATE SET SOURCE_ = excluded.SOURCE_, TOKEN_VALUE_ = excluded.TOKEN_VALUE_, USERNAME_ = excluded.USERNAME_, DEVICE_ID_ = excluded.DEVICE_ID_, DEVICE_NAME_ = excluded.DEVICE_NAME_, CLIENT_ID_ = excluded.CLIENT_ID_, AUTHORIZATION_ID_ = excluded.AUTHORIZATION_ID_, ISSUED_AT_ = excluded.ISSUED_AT_, EXPIRES_AT_ = excluded.EXPIRES_AT_, UPDATE_AT_ = excluded.UPDATE_AT_;" >/dev/null

printf '%s\n' "$token"
