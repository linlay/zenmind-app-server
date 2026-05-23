$ErrorActionPreference = 'Stop'

[Reflection.Assembly]::LoadWithPartialName('System.IO.Compression.FileSystem') | Out-Null

$WorkspaceRoot = 'C:\Users\42134\Desktop\zenmind-workspace'
$ZipPath = Join-Path $WorkspaceRoot 'zenmind-app-server\dist\release\zenmind-app-server-v0.2.1-windows-amd64.zip'
$ScriptsSource = Join-Path $WorkspaceRoot 'zenmind-app-server\scripts\release-assets\program\scripts'

$TempExtract = Join-Path $env:TEMP "zenmind-zip-patch-$(Get-Random)"
if (Test-Path $TempExtract) { Remove-Item -Recurse -Force $TempExtract }
New-Item -ItemType Directory -Path $TempExtract | Out-Null

Write-Host "Extracting $ZipPath to $TempExtract..."
[System.IO.Compression.ZipFile]::ExtractToDirectory($ZipPath, $TempExtract)

$ManifestPath = Join-Path $TempExtract 'zenmind-app-server\manifest.json'
Write-Host "Original manifest.json contents:"
$manifestContent = Get-Content -Raw -Encoding UTF8 $ManifestPath
Write-Host $manifestContent

# Parse manifest
$manifest = $manifestContent | ConvertFrom-Json

# Modify manifest requiredPaths if it exists
if ($manifest.runtime -and $manifest.runtime.requiredPaths) {
    $paths = [System.Collections.Generic.List[string]]($manifest.runtime.requiredPaths)
    $newPaths = @(
        "scripts/crypto-helpers.ps1",
        "scripts/sqlite-helpers.ps1",
        "scripts/lib/System.Data.SQLite.dll"
    )
    foreach ($np in $newPaths) {
        if (-not $paths.Contains($np)) {
            $paths.Add($np)
            Write-Host "Added $np to manifest requiredPaths"
        }
    }
    $manifest.runtime.requiredPaths = $paths.ToArray()
    $manifestJson = ConvertTo-Json $manifest -Depth 10
    # Write UTF-8 without BOM using .NET API
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($ManifestPath, $manifestJson, $utf8NoBom)
}

# Copy files
$DestScripts = Join-Path $TempExtract 'zenmind-app-server\scripts'
$DestLib = Join-Path $DestScripts 'lib'
if (-not (Test-Path $DestLib)) {
    New-Item -ItemType Directory -Path $DestLib | Out-Null
    Write-Host "Created lib folder inside zip scripts"
}

# Files to copy
$files = @(
    "crypto-helpers.ps1",
    "sqlite-helpers.ps1",
    "setup-public-key.ps1",
    "issue-bridge-access-token.ps1",
    "issue-bridge-runner-token.ps1"
)

foreach ($f in $files) {
    Copy-Item (Join-Path $ScriptsSource $f) (Join-Path $DestScripts $f) -Force
    Write-Host "Copied $f to zip scripts"
}

# Copy DLL
Copy-Item (Join-Path $ScriptsSource "lib\System.Data.SQLite.dll") (Join-Path $DestLib "System.Data.SQLite.dll") -Force
Write-Host "Copied System.Data.SQLite.dll to zip scripts/lib"

# Zip it back up
$BackupZip = $ZipPath + ".bak"
if (Test-Path $BackupZip) { Remove-Item -Force $BackupZip }
Rename-Item $ZipPath -NewName "$((Split-Path $ZipPath -Leaf)).bak"

Write-Host "Creating updated zip at $ZipPath..."
[System.IO.Compression.ZipFile]::CreateFromDirectory($TempExtract, $ZipPath)

# Clean up
Remove-Item -Recurse -Force $TempExtract
Write-Host "ZIP patching completed successfully!"
