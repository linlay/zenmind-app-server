param(
    [ValidateSet('bootstrap', 'rotate')]
    [string]$Mode = 'bootstrap',
    [string]$Db = '',
    [string]$Out = '',
    [string]$PublicOut = '',
    [string]$KeyId = ''
)

$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Split-Path -Parent $ScriptDir

if (-not $Db) { $Db = if ($env:AUTH_DB_PATH) { $env:AUTH_DB_PATH } else { Join-Path $RootDir 'data/auth.db' } }
if (-not $Out) { $Out = if ($env:KEY_OUTPUT_DIR) { $env:KEY_OUTPUT_DIR } else { Join-Path $RootDir 'data/keys' } }
if (-not $PublicOut) { $PublicOut = Join-Path $Out 'publicKey.pem' }
if (-not $KeyId) { $KeyId = $env:JWK_KEY_ID }

$BackendBin = Join-Path (Join-Path $RootDir 'backend') 'zenmind-app-server.exe'
if (Test-Path -LiteralPath $BackendBin -PathType Leaf) {
    $goArgs = @('setup-public-key', '--mode', $Mode, '--db', $Db, '--out', $Out, '--public-out', $PublicOut)
    if ($KeyId) { $goArgs += @('--key-id', $KeyId) }
    & $BackendBin @goArgs
    exit $LASTEXITCODE
}

. (Join-Path $ScriptDir 'crypto-helpers.ps1')
. (Join-Path $ScriptDir 'sqlite-helpers.ps1')

function Write-Log([string]$Message) {
    Write-Host ("[setup-public-key] {0}" -f $Message)
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Db), $Out, (Split-Path -Parent $PublicOut) | Out-Null

$conn = New-SqliteConnection -DbPath $Db
try {
    Invoke-SqliteNonQuery -Connection $conn -Sql @'
CREATE TABLE IF NOT EXISTS JWK_KEY_ (
  KEY_ID_ TEXT PRIMARY KEY,
  PUBLIC_KEY_ TEXT NOT NULL,
  PRIVATE_KEY_ TEXT NOT NULL,
  CREATE_AT_ TIMESTAMP NOT NULL
);
'@

    $existingRow = @(Invoke-SqliteQuery -Connection $conn -Sql "SELECT KEY_ID_, PUBLIC_KEY_, PRIVATE_KEY_ FROM JWK_KEY_ ORDER BY CREATE_AT_ ASC LIMIT 1;")
    if ($Mode -eq 'bootstrap' -and $existingRow.Count -gt 0) {
        $row = $existingRow[0]
        $publicDer = [Convert]::FromBase64String($row.PUBLIC_KEY_)
        $privateDer = [Convert]::FromBase64String($row.PRIVATE_KEY_)

        $publicPem = ConvertTo-Pem -DerBytes $publicDer -Label 'PUBLIC KEY'
        $privatePem = ConvertTo-Pem -DerBytes $privateDer -Label 'PRIVATE KEY'

        [System.IO.File]::WriteAllText((Join-Path $Out 'jwk-public.pem'), $publicPem, [Text.Encoding]::ASCII)
        [System.IO.File]::WriteAllText((Join-Path $Out 'jwk-private.pem'), $privatePem, [Text.Encoding]::ASCII)
        Copy-Item -Force (Join-Path $Out 'jwk-public.pem') $PublicOut
        Write-Log ("exported existing key pair (kid={0})" -f $row.KEY_ID_)
        exit 0
    }

    if ($Mode -eq 'rotate') {
        Invoke-SqliteNonQuery -Connection $conn -Sql "DELETE FROM JWK_KEY_;"
    }

    if (-not $KeyId) {
        $KeyId = New-RandomHex -ByteCount 16
    }

    $keyPair = New-RsaKeyPair

    $privateB64 = [Convert]::ToBase64String($keyPair.PrivatePkcs8Der)
    $publicB64 = [Convert]::ToBase64String($keyPair.PublicSpkiDer)
    Invoke-SqliteNonQuery -Connection $conn -Sql @"
INSERT INTO JWK_KEY_(KEY_ID_, PUBLIC_KEY_, PRIVATE_KEY_, CREATE_AT_)
VALUES(@kid, @pub, @priv, CURRENT_TIMESTAMP);
"@ -Parameters @{ kid = $KeyId; pub = $publicB64; priv = $privateB64 }

    [System.IO.File]::WriteAllText((Join-Path $Out 'jwk-private.pem'), $keyPair.PrivatePem, [Text.Encoding]::ASCII)
    [System.IO.File]::WriteAllText((Join-Path $Out 'jwk-public.pem'), $keyPair.PublicPem, [Text.Encoding]::ASCII)
    Copy-Item -Force (Join-Path $Out 'jwk-public.pem') $PublicOut

    Write-Log ("generated and stored new key pair (kid={0})" -f $KeyId)
} finally {
    $conn.Close()
    $conn.Dispose()
}
