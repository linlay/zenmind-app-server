$ErrorActionPreference = 'Stop'

param(
    [string]$Db = '',
    [string]$Issuer = '',
    [string]$Username = '',
    [string]$DeviceName = 'WeChat Bridge'
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Split-Path -Parent $ScriptDir
$PlaceholderDeviceTokenBcrypt = '$2a$10$7J8GmW8J0tR9o5Z8L4m5Uuu6fQW4j6mJjM7qY0Q8n2rM5b3y1fVwK'
$AccessTtlSeconds = 31536000

if (-not $Db) { $Db = if ($env:AUTH_DB_PATH) { $env:AUTH_DB_PATH } else { Join-Path $RootDir 'data/auth.db' } }
if (-not $Issuer) { $Issuer = if ($env:AUTH_ISSUER) { $env:AUTH_ISSUER } else { 'http://localhost:8080' } }
if (-not $Username) { $Username = if ($env:AUTH_APP_USERNAME) { $env:AUTH_APP_USERNAME } else { 'app' } }

function Require-Cmd([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "missing required command: $Name"
    }
}

function Escape-Sql([string]$Value) {
    return $Value.Replace("'", "''")
}

function New-TempDir {
    $path = Join-Path ([System.IO.Path]::GetTempPath()) ([System.Guid]::NewGuid().ToString())
    [System.IO.Directory]::CreateDirectory($path) | Out-Null
    return $path
}

function Convert-ToBase64Url([byte[]]$Bytes) {
    return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

Require-Cmd openssl
Require-Cmd sqlite3

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Db) | Out-Null

@'
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
'@ | & sqlite3 $Db | Out-Null

$keyRow = (& sqlite3 -separator '|' -noheader $Db "SELECT KEY_ID_, PRIVATE_KEY_ FROM JWK_KEY_ ORDER BY CREATE_AT_ ASC LIMIT 1;").Trim()
if (-not $keyRow) {
    throw "no JWK key found in $Db; run ./setup-public-key.ps1 first"
}
$keyParts = $keyRow.Split('|', 2)
$keyId = $keyParts[0]
$privateKeyB64 = $keyParts[1]

$escapedDeviceName = Escape-Sql $DeviceName
$deviceId = (& sqlite3 -separator '|' -noheader $Db "SELECT DEVICE_ID_ FROM DEVICE_ WHERE STATUS_ = 'ACTIVE' AND DEVICE_NAME_ = '$escapedDeviceName' ORDER BY UPDATE_AT_ DESC LIMIT 1;").Trim()
$nowSql = [DateTime]::UtcNow.ToString('yyyy-MM-dd HH:mm:ss')
if ($deviceId) {
    & sqlite3 $Db "UPDATE DEVICE_ SET LAST_SEEN_AT_ = '$nowSql', UPDATE_AT_ = '$nowSql' WHERE DEVICE_ID_ = '$deviceId' AND STATUS_ = 'ACTIVE';" | Out-Null
} else {
    $deviceId = [guid]::NewGuid().ToString()
    $escapedPlaceholder = Escape-Sql $PlaceholderDeviceTokenBcrypt
    & sqlite3 $Db "INSERT INTO DEVICE_(DEVICE_ID_, DEVICE_NAME_, DEVICE_TOKEN_BCRYPT_, STATUS_, LAST_SEEN_AT_, REVOKED_AT_, CREATE_AT_, UPDATE_AT_) VALUES('$deviceId', '$escapedDeviceName', '$escapedPlaceholder', 'ACTIVE', '$nowSql', NULL, '$nowSql', '$nowSql');" | Out-Null
}

$tempDir = New-TempDir
try {
    $privateDer = Join-Path $tempDir 'private.der'
    $privatePem = Join-Path $tempDir 'private.pem'
    [System.IO.File]::WriteAllBytes($privateDer, [Convert]::FromBase64String($privateKeyB64))
    & openssl pkcs8 -inform DER -outform PEM -nocrypt -in $privateDer -out $privatePem 2>$null | Out-Null

    $iat = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $exp = $iat + $AccessTtlSeconds
    $expSql = [DateTimeOffset]::FromUnixTimeSeconds($exp).UtcDateTime.ToString('yyyy-MM-dd HH:mm:ss')
    $jti = [guid]::NewGuid().ToString()
    $headerJson = @{ alg = 'RS256'; kid = $keyId; typ = 'JWT' } | ConvertTo-Json -Compress
    $payloadJson = @{
        iss = $Issuer
        sub = $Username
        iat = $iat
        exp = $exp
        jti = $jti
        scope = 'app'
        device_id = $deviceId
    } | ConvertTo-Json -Compress

    $headerB64 = Convert-ToBase64Url ([Text.Encoding]::UTF8.GetBytes($headerJson))
    $payloadB64 = Convert-ToBase64Url ([Text.Encoding]::UTF8.GetBytes($payloadJson))
    $signingInput = "$headerB64.$payloadB64"
    $signingPath = Join-Path $tempDir 'signing-input.txt'
    $signaturePath = Join-Path $tempDir 'signature.bin'
    [System.IO.File]::WriteAllText($signingPath, $signingInput, [Text.Encoding]::ASCII)
    & openssl dgst -sha256 -sign $privatePem -out $signaturePath $signingPath 2>$null | Out-Null
    $token = "$signingInput.$(Convert-ToBase64Url ([System.IO.File]::ReadAllBytes($signaturePath)))"

    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $tokenSha256 = ([System.BitConverter]::ToString($sha256.ComputeHash([Text.Encoding]::UTF8.GetBytes($token)))).Replace('-', '').ToLowerInvariant()
    } finally {
        $sha256.Dispose()
    }

    $tokenId = [guid]::NewGuid().ToString()
    $escapedUsername = Escape-Sql $Username
    $escapedToken = Escape-Sql $token
    $escapedTokenSha = Escape-Sql $tokenSha256
    & sqlite3 $Db "INSERT INTO TOKEN_AUDIT_(TOKEN_ID_, SOURCE_, TOKEN_VALUE_, TOKEN_SHA256_, USERNAME_, DEVICE_ID_, DEVICE_NAME_, CLIENT_ID_, AUTHORIZATION_ID_, ISSUED_AT_, EXPIRES_AT_, REVOKED_AT_, CREATE_AT_, UPDATE_AT_) VALUES('$tokenId', 'APP_ACCESS', '$escapedToken', '$escapedTokenSha', '$escapedUsername', '$deviceId', '$escapedDeviceName', NULL, NULL, '$nowSql', '$expSql', NULL, '$nowSql', '$nowSql') ON CONFLICT(TOKEN_SHA256_) DO UPDATE SET SOURCE_ = excluded.SOURCE_, TOKEN_VALUE_ = excluded.TOKEN_VALUE_, USERNAME_ = excluded.USERNAME_, DEVICE_ID_ = excluded.DEVICE_ID_, DEVICE_NAME_ = excluded.DEVICE_NAME_, CLIENT_ID_ = excluded.CLIENT_ID_, AUTHORIZATION_ID_ = excluded.AUTHORIZATION_ID_, ISSUED_AT_ = excluded.ISSUED_AT_, EXPIRES_AT_ = excluded.EXPIRES_AT_, UPDATE_AT_ = excluded.UPDATE_AT_;" | Out-Null

    Write-Output $token
} finally {
    Remove-Item -Recurse -Force $tempDir -ErrorAction SilentlyContinue
}
