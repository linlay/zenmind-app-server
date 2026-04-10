$ErrorActionPreference = 'Stop'

param(
    [ValidateSet('bootstrap', 'rotate')]
    [string]$Mode = 'bootstrap',
    [string]$Db = '',
    [string]$Out = '',
    [string]$PublicOut = '',
    [string]$KeyId = ''
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Split-Path -Parent $ScriptDir

if (-not $Db) { $Db = if ($env:AUTH_DB_PATH) { $env:AUTH_DB_PATH } else { Join-Path $RootDir 'data/auth.db' } }
if (-not $Out) { $Out = if ($env:KEY_OUTPUT_DIR) { $env:KEY_OUTPUT_DIR } else { Join-Path $RootDir 'data/keys' } }
if (-not $PublicOut) { $PublicOut = Join-Path $Out 'publicKey.pem' }
if (-not $KeyId) { $KeyId = $env:JWK_KEY_ID }

function Write-Log([string]$Message) {
    Write-Host ("[setup-public-key] {0}" -f $Message)
}

function Require-Cmd([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "missing required command: $Name"
    }
}

function New-TempDir {
    $path = Join-Path ([System.IO.Path]::GetTempPath()) ([System.Guid]::NewGuid().ToString())
    [System.IO.Directory]::CreateDirectory($path) | Out-Null
    return $path
}

Require-Cmd openssl
Require-Cmd sqlite3

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Db), $Out, (Split-Path -Parent $PublicOut) | Out-Null

@'
CREATE TABLE IF NOT EXISTS JWK_KEY_ (
  KEY_ID_ TEXT PRIMARY KEY,
  PUBLIC_KEY_ TEXT NOT NULL,
  PRIVATE_KEY_ TEXT NOT NULL,
  CREATE_AT_ TIMESTAMP NOT NULL
);
'@ | & sqlite3 $Db | Out-Null

$tempDir = New-TempDir
try {
    $existingRow = (& sqlite3 -noheader $Db "SELECT KEY_ID_ || '|' || PUBLIC_KEY_ || '|' || PRIVATE_KEY_ FROM JWK_KEY_ ORDER BY CREATE_AT_ ASC LIMIT 1;").Trim()
    if ($Mode -eq 'bootstrap' -and $existingRow) {
        $parts = $existingRow.Split('|', 3)
        [System.IO.File]::WriteAllBytes((Join-Path $tempDir 'public.der'), [Convert]::FromBase64String($parts[1]))
        [System.IO.File]::WriteAllBytes((Join-Path $tempDir 'private.der'), [Convert]::FromBase64String($parts[2]))
        & openssl pkey -pubin -inform DER -outform PEM -in (Join-Path $tempDir 'public.der') -out (Join-Path $Out 'jwk-public.pem') 2>$null | Out-Null
        & openssl pkcs8 -inform DER -outform PEM -nocrypt -in (Join-Path $tempDir 'private.der') -out (Join-Path $Out 'jwk-private.pem') 2>$null | Out-Null
        Copy-Item -Force (Join-Path $Out 'jwk-public.pem') $PublicOut
        Write-Log ("exported existing key pair (kid={0})" -f $parts[0])
        exit 0
    }

    if ($Mode -eq 'rotate') {
        & sqlite3 $Db "DELETE FROM JWK_KEY_;" | Out-Null
    }

    if (-not $KeyId) {
        $KeyId = (& openssl rand -hex 16).Trim()
    }

    & openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out (Join-Path $tempDir 'private.pem') 2>$null | Out-Null
    & openssl rsa -in (Join-Path $tempDir 'private.pem') -pubout -out (Join-Path $tempDir 'public.pem') 2>$null | Out-Null
    & openssl pkcs8 -topk8 -inform PEM -outform DER -nocrypt -in (Join-Path $tempDir 'private.pem') -out (Join-Path $tempDir 'private.der') 2>$null | Out-Null
    & openssl pkey -pubin -inform PEM -outform DER -in (Join-Path $tempDir 'public.pem') -out (Join-Path $tempDir 'public.der') 2>$null | Out-Null

    $privateB64 = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes((Join-Path $tempDir 'private.der')))
    $publicB64 = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes((Join-Path $tempDir 'public.der')))
    $escapedKeyId = $KeyId.Replace("'", "''")
    & sqlite3 $Db "INSERT INTO JWK_KEY_(KEY_ID_, PUBLIC_KEY_, PRIVATE_KEY_, CREATE_AT_) VALUES ('$escapedKeyId', '$publicB64', '$privateB64', CURRENT_TIMESTAMP);" | Out-Null

    Copy-Item -Force (Join-Path $tempDir 'private.pem') (Join-Path $Out 'jwk-private.pem')
    Copy-Item -Force (Join-Path $tempDir 'public.pem') (Join-Path $Out 'jwk-public.pem')
    Copy-Item -Force (Join-Path $tempDir 'public.pem') $PublicOut

    Write-Log ("generated and stored new key pair (kid={0})" -f $KeyId)
} finally {
    Remove-Item -Recurse -Force $tempDir -ErrorAction SilentlyContinue
}
