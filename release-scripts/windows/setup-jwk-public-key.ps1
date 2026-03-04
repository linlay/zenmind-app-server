param(
    [ValidateSet('bootstrap', 'rotate')]
    [string]$Mode = 'bootstrap',
    [string]$DbPath = $(if ($env:AUTH_DB_PATH) { $env:AUTH_DB_PATH } else { './data/auth.db' }),
    [string]$OutDir = $(if ($env:KEY_OUTPUT_DIR) { $env:KEY_OUTPUT_DIR } else { './data/keys' }),
    [string]$PublicOut = '',
    [string]$KeyId = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Log([string]$Message) {
    Write-Host "[setup-jwk-win] $Message"
}

function Require-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "missing required command: $Name"
    }
}

function New-HexKeyId([int]$ByteLength = 16) {
    $bytes = New-Object byte[] $ByteLength
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    -join ($bytes | ForEach-Object { $_.ToString('x2') })
}

function Convert-ToPem([string]$Label, [byte[]]$DerBytes) {
    $base64 = [System.Convert]::ToBase64String($DerBytes)
    $wrapped = ($base64 -split '(.{1,64})' | Where-Object { $_ -ne '' }) -join "`n"
    "-----BEGIN $Label-----`n$wrapped`n-----END $Label-----"
}

function Sqlite-Escape([string]$Value) {
    $Value -replace "'", "''"
}

function Ensure-PowerShell7 {
    if ($PSVersionTable.PSVersion.Major -lt 7) {
        throw "PowerShell 7+ is required (current: $($PSVersionTable.PSVersion))."
    }
}

Ensure-PowerShell7
Require-Command 'sqlite3'

if ([string]::IsNullOrWhiteSpace($PublicOut)) {
    $PublicOut = Join-Path $OutDir 'publicKey.pem'
}

$DbDir = Split-Path -Parent $DbPath
if (-not [string]::IsNullOrWhiteSpace($DbDir)) {
    New-Item -ItemType Directory -Force -Path $DbDir | Out-Null
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

sqlite3 $DbPath @"
CREATE TABLE IF NOT EXISTS JWK_KEY_ (
  KEY_ID_ TEXT PRIMARY KEY,
  PUBLIC_KEY_ TEXT NOT NULL,
  PRIVATE_KEY_ TEXT NOT NULL,
  CREATE_AT_ TIMESTAMP NOT NULL
);
"@ | Out-Null

$row = sqlite3 -separator '|' $DbPath "SELECT KEY_ID_ || '|' || PUBLIC_KEY_ || '|' || PRIVATE_KEY_ FROM JWK_KEY_ ORDER BY CREATE_AT_ ASC LIMIT 1;"

if ($Mode -eq 'bootstrap' -and -not [string]::IsNullOrWhiteSpace($row)) {
    $parts = $row -split '\|', 3
    if ($parts.Count -ne 3) {
        throw 'unexpected row format in JWK_KEY_'
    }

    $existingKid = $parts[0]
    $publicDer = [System.Convert]::FromBase64String($parts[1])
    $privateDer = [System.Convert]::FromBase64String($parts[2])

    $publicPem = Convert-ToPem -Label 'PUBLIC KEY' -DerBytes $publicDer
    $privatePem = Convert-ToPem -Label 'PRIVATE KEY' -DerBytes $privateDer

    $publicPemPath = Join-Path $OutDir 'jwk-public.pem'
    $privatePemPath = Join-Path $OutDir 'jwk-private.pem'

    Set-Content -Path $publicPemPath -Value $publicPem -Encoding ascii
    Set-Content -Path $privatePemPath -Value $privatePem -Encoding ascii

    $publicOutDir = Split-Path -Parent $PublicOut
    if (-not [string]::IsNullOrWhiteSpace($publicOutDir)) {
        New-Item -ItemType Directory -Force -Path $publicOutDir | Out-Null
    }
    Set-Content -Path $PublicOut -Value $publicPem -Encoding ascii

    Log "exported existing key pair (kid=$existingKid)"
    Log "public key exported: $PublicOut"
    Log 'done'
    exit 0
}

if ($Mode -eq 'rotate') {
    sqlite3 $DbPath "DELETE FROM JWK_KEY_;" | Out-Null
    Log 'cleared existing rows in JWK_KEY_'
}

if ([string]::IsNullOrWhiteSpace($KeyId)) {
    $KeyId = New-HexKeyId
}

$rsa = [System.Security.Cryptography.RSA]::Create(2048)
try {
    $privateDer = $rsa.ExportPkcs8PrivateKey()
    $publicDer = $rsa.ExportSubjectPublicKeyInfo()
}
finally {
    $rsa.Dispose()
}

$privatePem = Convert-ToPem -Label 'PRIVATE KEY' -DerBytes $privateDer
$publicPem = Convert-ToPem -Label 'PUBLIC KEY' -DerBytes $publicDer

$privatePemPath = Join-Path $OutDir 'jwk-private.pem'
$publicPemPath = Join-Path $OutDir 'jwk-public.pem'
Set-Content -Path $privatePemPath -Value $privatePem -Encoding ascii
Set-Content -Path $publicPemPath -Value $publicPem -Encoding ascii

$publicB64 = [System.Convert]::ToBase64String($publicDer)
$privateB64 = [System.Convert]::ToBase64String($privateDer)
$sqlKeyId = Sqlite-Escape $KeyId
$sqlPublicB64 = Sqlite-Escape $publicB64
$sqlPrivateB64 = Sqlite-Escape $privateB64

sqlite3 $DbPath "INSERT INTO JWK_KEY_(KEY_ID_, PUBLIC_KEY_, PRIVATE_KEY_, CREATE_AT_) VALUES ('$sqlKeyId', '$sqlPublicB64', '$sqlPrivateB64', CURRENT_TIMESTAMP);" | Out-Null

$publicOutDir = Split-Path -Parent $PublicOut
if (-not [string]::IsNullOrWhiteSpace($publicOutDir)) {
    New-Item -ItemType Directory -Force -Path $publicOutDir | Out-Null
}
Set-Content -Path $PublicOut -Value $publicPem -Encoding ascii

Log "generated and stored new key pair (kid=$KeyId)"
Log "db path: $DbPath"
Log "public key exported: $PublicOut"
Log 'note: rotating key invalidates previously issued app access tokens.'
Log 'note: restart backend after rotate so OAuth2 JWK source reloads the new key.'
Log 'done'
