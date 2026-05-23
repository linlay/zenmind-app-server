param(
    [string]$Db = '',
    [string]$Issuer = '',
    [string]$Username = '',
    [string]$DeviceName = 'WeChat Bridge'
)

$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Split-Path -Parent $ScriptDir
$PlaceholderDeviceTokenBcrypt = '$2a$10$7J8GmW8J0tR9o5Z8L4m5Uuu6fQW4j6mJjM7qY0Q8n2rM5b3y1fVwK'
$AccessTtlSeconds = 31536000

if (-not $Db) { $Db = if ($env:AUTH_DB_PATH) { $env:AUTH_DB_PATH } else { Join-Path $RootDir 'data/auth.db' } }
if (-not $Issuer) { $Issuer = if ($env:AUTH_ISSUER) { $env:AUTH_ISSUER } else { 'http://localhost:8080' } }
if (-not $Username) { $Username = if ($env:AUTH_APP_USERNAME) { $env:AUTH_APP_USERNAME } else { 'app' } }

$BackendBin = Join-Path (Join-Path $RootDir 'backend') 'zenmind-app-server.exe'
if (Test-Path -LiteralPath $BackendBin -PathType Leaf) {
    & $BackendBin issue-bridge-access-token --db $Db --issuer $Issuer --username $Username --device-name $DeviceName
    exit $LASTEXITCODE
}

. (Join-Path $ScriptDir 'crypto-helpers.ps1')
. (Join-Path $ScriptDir 'sqlite-helpers.ps1')

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Db) | Out-Null

$conn = New-SqliteConnection -DbPath $Db
try {
    Invoke-SqliteNonQuery -Connection $conn -Sql @'
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
'@

    $keyRow = @(Invoke-SqliteQuery -Connection $conn -Sql "SELECT KEY_ID_, PRIVATE_KEY_ FROM JWK_KEY_ ORDER BY CREATE_AT_ ASC LIMIT 1;")
    if ($keyRow.Count -eq 0) {
        throw "no JWK key found in $Db; run ./setup-public-key.ps1 first"
    }
    $keyId = $keyRow[0].KEY_ID_
    $privateKeyB64 = $keyRow[0].PRIVATE_KEY_

    # Find or create device
    $deviceId = Invoke-SqliteScalar -Connection $conn -Sql "SELECT DEVICE_ID_ FROM DEVICE_ WHERE STATUS_ = 'ACTIVE' AND DEVICE_NAME_ = @name ORDER BY UPDATE_AT_ DESC LIMIT 1;" -Parameters @{ name = $DeviceName }
    $nowSql = [DateTime]::UtcNow.ToString('yyyy-MM-dd HH:mm:ss')

    if ($deviceId) {
        Invoke-SqliteNonQuery -Connection $conn -Sql "UPDATE DEVICE_ SET LAST_SEEN_AT_ = @now, UPDATE_AT_ = @now WHERE DEVICE_ID_ = @did AND STATUS_ = 'ACTIVE';" -Parameters @{ now = $nowSql; did = $deviceId }
    } else {
        $deviceId = [guid]::NewGuid().ToString()
        Invoke-SqliteNonQuery -Connection $conn -Sql @"
INSERT INTO DEVICE_(DEVICE_ID_, DEVICE_NAME_, DEVICE_TOKEN_BCRYPT_, STATUS_, LAST_SEEN_AT_, REVOKED_AT_, CREATE_AT_, UPDATE_AT_)
VALUES(@did, @dname, @bcrypt, 'ACTIVE', @now, NULL, @now, @now);
"@ -Parameters @{ did = $deviceId; dname = $DeviceName; bcrypt = $PlaceholderDeviceTokenBcrypt; now = $nowSql }
    }

    # Import private key and sign JWT
    $privateDer = [Convert]::FromBase64String($privateKeyB64)
    $rsa = Import-RsaPrivateKeyFromPkcs8Der $privateDer
    try {
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

        $headerB64 = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($headerJson))
        $payloadB64 = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($payloadJson))
        $signingInput = "$headerB64.$payloadB64"

        $signature = New-Rs256Signature -Rsa $rsa -Data ([Text.Encoding]::ASCII.GetBytes($signingInput))
        $token = "$signingInput.$(ConvertTo-Base64Url $signature)"

        $sha256 = [System.Security.Cryptography.SHA256]::Create()
        try {
            $tokenSha256 = ([System.BitConverter]::ToString($sha256.ComputeHash([Text.Encoding]::UTF8.GetBytes($token)))).Replace('-', '').ToLowerInvariant()
        } finally {
            $sha256.Dispose()
        }

        $tokenId = [guid]::NewGuid().ToString()
        Invoke-SqliteNonQuery -Connection $conn -Sql @"
INSERT INTO TOKEN_AUDIT_(TOKEN_ID_, SOURCE_, TOKEN_VALUE_, TOKEN_SHA256_, USERNAME_, DEVICE_ID_, DEVICE_NAME_, CLIENT_ID_, AUTHORIZATION_ID_, ISSUED_AT_, EXPIRES_AT_, REVOKED_AT_, CREATE_AT_, UPDATE_AT_)
VALUES(@tid, 'APP_ACCESS', @tval, @tsha, @uname, @did, @dname, NULL, NULL, @now, @expsql, NULL, @now, @now)
ON CONFLICT(TOKEN_SHA256_) DO UPDATE SET
  SOURCE_ = excluded.SOURCE_,
  TOKEN_VALUE_ = excluded.TOKEN_VALUE_,
  USERNAME_ = excluded.USERNAME_,
  DEVICE_ID_ = excluded.DEVICE_ID_,
  DEVICE_NAME_ = excluded.DEVICE_NAME_,
  CLIENT_ID_ = excluded.CLIENT_ID_,
  AUTHORIZATION_ID_ = excluded.AUTHORIZATION_ID_,
  ISSUED_AT_ = excluded.ISSUED_AT_,
  EXPIRES_AT_ = excluded.EXPIRES_AT_,
  UPDATE_AT_ = excluded.UPDATE_AT_;
"@ -Parameters @{
            tid = $tokenId
            tval = $token
            tsha = $tokenSha256
            uname = $Username
            did = $deviceId
            dname = $DeviceName
            now = $nowSql
            expsql = $expSql
        }

        Write-Output $token
    } finally {
        $rsa.Dispose()
    }
} finally {
    $conn.Close()
    $conn.Dispose()
}
