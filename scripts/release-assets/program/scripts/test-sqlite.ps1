$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'sqlite-helpers.ps1')

$testDb = Join-Path $env:TEMP "test-sqlite-helpers-$(Get-Random).db"
Write-Host "[test] Using DB: $testDb"

try {
    $conn = New-SqliteConnection -DbPath $testDb
    Write-Host "[test] Connection opened"

    Invoke-SqliteNonQuery -Connection $conn -Sql @"
CREATE TABLE IF NOT EXISTS JWK_KEY_ (
  KEY_ID_ TEXT PRIMARY KEY,
  PUBLIC_KEY_ TEXT NOT NULL,
  PRIVATE_KEY_ TEXT NOT NULL,
  CREATE_AT_ TIMESTAMP NOT NULL
);
"@
    Write-Host "[test] Table created"

    Invoke-SqliteNonQuery -Connection $conn -Sql @"
INSERT INTO JWK_KEY_(KEY_ID_, PUBLIC_KEY_, PRIVATE_KEY_, CREATE_AT_)
VALUES(@kid, @pub, @priv, CURRENT_TIMESTAMP);
"@ -Parameters @{ kid = 'test-key-1'; pub = 'PUBDATA'; priv = 'PRIVDATA' }
    Write-Host "[test] Row inserted"

    $rows = Invoke-SqliteQuery -Connection $conn -Sql "SELECT KEY_ID_, PUBLIC_KEY_, PRIVATE_KEY_ FROM JWK_KEY_ ORDER BY CREATE_AT_ ASC LIMIT 1;"
    Write-Host ("[test] Query returned: kid={0}, pub={1}, priv={2}" -f $rows[0].KEY_ID_, $rows[0].PUBLIC_KEY_, $rows[0].PRIVATE_KEY_)

    $count = Invoke-SqliteScalar -Connection $conn -Sql "SELECT COUNT(*) FROM JWK_KEY_;"
    Write-Host ("[test] Count: {0}" -f $count)

    $missing = Invoke-SqliteScalar -Connection $conn -Sql "SELECT KEY_ID_ FROM JWK_KEY_ WHERE KEY_ID_ = 'nonexistent';"
    Write-Host ("[test] Missing query returns null: {0}" -f ($null -eq $missing))

    $conn.Close()
    $conn.Dispose()
    Write-Host ""
    Write-Host "=== ALL SQLITE TESTS PASSED ==="
} finally {
    Remove-Item -Force $testDb -ErrorAction SilentlyContinue
    Remove-Item -Force "$testDb-wal" -ErrorAction SilentlyContinue
    Remove-Item -Force "$testDb-shm" -ErrorAction SilentlyContinue
}
