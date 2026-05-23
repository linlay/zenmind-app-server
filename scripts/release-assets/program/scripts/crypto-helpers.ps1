<#
.SYNOPSIS
  Pure .NET cryptography helpers — replaces openssl CLI dependency.
  Compatible with Windows PowerShell 5.1 (.NET Framework 4.7.2+).
#>

# ---------------------------------------------------------------------------
# ASN.1 DER encoding primitives
# ---------------------------------------------------------------------------

function script:Write-DerLength([int]$Length) {
    if ($Length -lt 128) {
        return [byte[]]@($Length)
    }
    if ($Length -lt 256) {
        return [byte[]]@(0x81, [byte]$Length)
    }
    $hi = [byte](($Length -shr 8) -band 0xFF)
    $lo = [byte]($Length -band 0xFF)
    return [byte[]]@(0x82, $hi, $lo)
}

function script:Write-DerTag([byte]$Tag, [byte[]]$Content) {
    $len = Write-DerLength $Content.Length
    $result = New-Object byte[] (1 + $len.Length + $Content.Length)
    $result[0] = $Tag
    [Array]::Copy($len, 0, $result, 1, $len.Length)
    [Array]::Copy($Content, 0, $result, 1 + $len.Length, $Content.Length)
    return ,$result
}

function script:Write-DerSequence([byte[][]]$Items) {
    $totalLen = 0
    foreach ($item in $Items) { $totalLen += $item.Length }
    $body = New-Object byte[] $totalLen
    $offset = 0
    foreach ($item in $Items) {
        [Array]::Copy($item, 0, $body, $offset, $item.Length)
        $offset += $item.Length
    }
    return ,(Write-DerTag 0x30 $body)
}

function script:Write-DerInteger([byte[]]$UnsignedBigEndian) {
    # ASN.1 INTEGER is signed two's-complement.
    # If high bit is set on the leading byte, prepend 0x00.
    if ($UnsignedBigEndian.Length -gt 0 -and ($UnsignedBigEndian[0] -band 0x80)) {
        $padded = New-Object byte[] ($UnsignedBigEndian.Length + 1)
        $padded[0] = 0
        [Array]::Copy($UnsignedBigEndian, 0, $padded, 1, $UnsignedBigEndian.Length)
        return ,(Write-DerTag 0x02 $padded)
    }
    return ,(Write-DerTag 0x02 $UnsignedBigEndian)
}

function script:Write-DerIntegerSmall([int]$Value) {
    if ($Value -lt 128) {
        return ,(Write-DerTag 0x02 ([byte[]]@($Value)))
    }
    $bytes = [System.BitConverter]::GetBytes($Value)
    [Array]::Reverse($bytes)
    $trimmed = @($bytes | Where-Object { $_ -ne 0 })
    if ($trimmed.Count -eq 0) { $trimmed = @(0) }
    return ,(Write-DerInteger ([byte[]]$trimmed))
}

function script:Write-DerBitString([byte[]]$Content) {
    # BIT STRING with zero unused bits
    $body = New-Object byte[] ($Content.Length + 1)
    $body[0] = 0  # unused bits
    [Array]::Copy($Content, 0, $body, 1, $Content.Length)
    return ,(Write-DerTag 0x03 $body)
}

function script:Write-DerOctetString([byte[]]$Content) {
    return ,(Write-DerTag 0x04 $Content)
}

function script:Write-DerNull {
    return ,([byte[]]@(0x05, 0x00))
}

function script:Write-DerOidRsaEncryption {
    # OID 1.2.840.113549.1.1.1 (rsaEncryption)
    return ,([byte[]]@(0x06, 0x09, 0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x01))
}

# ---------------------------------------------------------------------------
# RSA key DER encoding
# ---------------------------------------------------------------------------

function script:Export-RsaPublicKeySpkiDer {
    param([System.Security.Cryptography.RSA]$Rsa)

    $params = $Rsa.ExportParameters($false)
    $rsaPublicKey = Write-DerSequence @(
        (Write-DerInteger $params.Modulus),
        (Write-DerInteger $params.Exponent)
    )
    $algorithmId = Write-DerSequence @(
        (Write-DerOidRsaEncryption),
        (Write-DerNull)
    )
    $spki = Write-DerSequence @(
        $algorithmId,
        (Write-DerBitString $rsaPublicKey)
    )
    return ,$spki
}

# ---------------------------------------------------------------------------
# PEM conversion
# ---------------------------------------------------------------------------

function ConvertTo-Pem {
    param(
        [byte[]]$DerBytes,
        [string]$Label
    )
    $base64 = [Convert]::ToBase64String($DerBytes, [Base64FormattingOptions]::InsertLineBreaks)
    return "-----BEGIN $Label-----`r`n$base64`r`n-----END $Label-----`r`n"
}

function ConvertFrom-Pem {
    param([string]$PemText)
    $stripped = ($PemText -replace '-----[^-]+-----', '').Trim()
    return ,[Convert]::FromBase64String($stripped)
}

# ---------------------------------------------------------------------------
# High-level key operations
# ---------------------------------------------------------------------------

function New-RsaKeyPair {
    <#
    .SYNOPSIS
      Generate a new 2048-bit RSA keypair.
    .OUTPUTS
      PSObject with: PrivatePkcs8Der, PublicSpkiDer, PrivatePem, PublicPem
    #>
    $rsa = [System.Security.Cryptography.RSACng]::new(2048)
    try {
        $pkcs8Der = $rsa.Key.Export([System.Security.Cryptography.CngKeyBlobFormat]::Pkcs8PrivateBlob)
        $spkiDer = Export-RsaPublicKeySpkiDer $rsa

        return [PSCustomObject]@{
            PrivatePkcs8Der = $pkcs8Der
            PublicSpkiDer   = $spkiDer
            PrivatePem      = ConvertTo-Pem -DerBytes $pkcs8Der -Label 'PRIVATE KEY'
            PublicPem       = ConvertTo-Pem -DerBytes $spkiDer  -Label 'PUBLIC KEY'
        }
    } finally {
        $rsa.Dispose()
    }
}

function Import-RsaPrivateKeyFromPkcs8Der {
    <#
    .SYNOPSIS
      Import an RSA private key from PKCS#8 DER bytes.
    .OUTPUTS
      System.Security.Cryptography.RSACng — caller must dispose.
    #>
    param([byte[]]$Der)
    $cngKey = [System.Security.Cryptography.CngKey]::Import(
        $Der,
        [System.Security.Cryptography.CngKeyBlobFormat]::Pkcs8PrivateBlob
    )
    return [System.Security.Cryptography.RSACng]::new($cngKey)
}

# ---------------------------------------------------------------------------
# Utility functions
# ---------------------------------------------------------------------------

function New-RandomHex {
    param([int]$ByteCount = 16)
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $bytes = New-Object byte[] $ByteCount
        $rng.GetBytes($bytes)
        return ([System.BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
    } finally {
        $rng.Dispose()
    }
}

function ConvertTo-Base64Url {
    param([byte[]]$Bytes)
    return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function New-Rs256Signature {
    <#
    .SYNOPSIS
      Sign data with RS256 (RSA PKCS#1 v1.5 + SHA-256).
    .OUTPUTS
      Signature as byte array.
    #>
    param(
        [System.Security.Cryptography.RSA]$Rsa,
        [byte[]]$Data
    )
    return ,$Rsa.SignData(
        $Data,
        [System.Security.Cryptography.HashAlgorithmName]::SHA256,
        [System.Security.Cryptography.RSASignaturePadding]::Pkcs1
    )
}
