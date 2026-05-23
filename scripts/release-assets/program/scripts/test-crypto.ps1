$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'crypto-helpers.ps1')

Write-Host "[test] Generating RSA 2048 keypair..."
$kp = New-RsaKeyPair
Write-Host ("[test] PKCS8 DER length: {0}" -f $kp.PrivatePkcs8Der.Length)
Write-Host ("[test] SPKI DER length: {0}" -f $kp.PublicSpkiDer.Length)

Write-Host "[test] Private PEM header:"
Write-Host ($kp.PrivatePem.Split("`n")[0])
Write-Host "[test] Public PEM header:"
Write-Host ($kp.PublicPem.Split("`n")[0])

Write-Host ("[test] RandomHex: {0}" -f (New-RandomHex))
Write-Host ("[test] Base64Url: {0}" -f (ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes("hello world"))))

Write-Host "[test] Import private key from PKCS8 DER..."
$rsa2 = Import-RsaPrivateKeyFromPkcs8Der $kp.PrivatePkcs8Der

Write-Host "[test] Sign with RS256..."
$sig = New-Rs256Signature -Rsa $rsa2 -Data ([Text.Encoding]::UTF8.GetBytes("hello"))
Write-Host ("[test] Signature length: {0}" -f $sig.Length)

Write-Host "[test] Verify signature..."
$verified = $rsa2.VerifyData(
    [Text.Encoding]::UTF8.GetBytes("hello"),
    $sig,
    [System.Security.Cryptography.HashAlgorithmName]::SHA256,
    [System.Security.Cryptography.RSASignaturePadding]::Pkcs1
)
Write-Host ("[test] Verify: {0}" -f $verified)
if (-not $verified) { throw "Signature verification FAILED" }

Write-Host "[test] PEM roundtrip test..."
$pemDer = ConvertFrom-Pem $kp.PublicPem
if ($pemDer.Length -ne $kp.PublicSpkiDer.Length) {
    throw ("PEM roundtrip FAILED: expected {0} bytes, got {1}" -f $kp.PublicSpkiDer.Length, $pemDer.Length)
}
Write-Host "[test] PEM roundtrip OK"

$rsa2.Dispose()
Write-Host ""
Write-Host "=== ALL CRYPTO TESTS PASSED ==="
