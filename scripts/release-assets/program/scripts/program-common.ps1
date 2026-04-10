$script:BundleRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$script:EnvFile = Join-Path $script:BundleRoot '.env'
$script:BackendBin = Join-Path $script:BundleRoot 'backend/zenmind-app-server.exe'
$script:FrontendDir = Join-Path $script:BundleRoot 'frontend'
$script:DistDir = Join-Path $script:FrontendDir 'dist'
$script:RunDir = Join-Path $script:BundleRoot 'run'
$script:LogDir = Join-Path $script:RunDir 'logs'
$script:BackendLog = Join-Path $script:LogDir 'backend.log'
$script:BackendPidFile = Join-Path $script:RunDir 'backend.pid'
$script:DataDir = Join-Path $script:BundleRoot 'data'

function Import-ProgramEnv {
    param([switch]$Optional)

    if (-not (Test-Path $script:EnvFile)) {
        if ($Optional) {
            $env:SERVER_PORT = if ($env:SERVER_PORT) { $env:SERVER_PORT } else { '18080' }
            $env:AUTH_DB_PATH = if ($env:AUTH_DB_PATH) { $env:AUTH_DB_PATH } else { '.\data\auth.db' }
            return
        }
        throw "missing .env (copy from .env.example first)"
    }

    Get-Content $script:EnvFile | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith('#')) { return }
        $parts = $line -split '=', 2
        if ($parts.Count -ne 2) { return }
        $name = $parts[0].Trim()
        $value = $parts[1].Trim()
        if (($value.StartsWith("'") -and $value.EndsWith("'")) -or ($value.StartsWith('"') -and $value.EndsWith('"'))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }

    $env:SERVER_PORT = if ($env:SERVER_PORT) { $env:SERVER_PORT } else { '18080' }
    $env:AUTH_DB_PATH = if ($env:AUTH_DB_PATH) { $env:AUTH_DB_PATH } else { '.\data\auth.db' }
}

function Initialize-ProgramRuntime {
    New-Item -ItemType Directory -Force -Path $script:DataDir, $script:LogDir | Out-Null
}

function Initialize-ProgramBundle {
    Initialize-ProgramRuntime
    if (-not (Test-Path $script:BackendBin)) { throw "missing backend binary: $script:BackendBin" }
    if (-not (Test-Path (Join-Path $script:DistDir 'index.html'))) { throw "missing frontend dist: $script:DistDir\index.html" }
}

function Start-ProgramBackend {
    if (Test-Path $script:BackendPidFile) {
        $existingPid = (Get-Content $script:BackendPidFile | Select-Object -First 1).Trim()
        if ($existingPid) {
            $existingProcess = Get-Process -Id ([int]$existingPid) -ErrorAction SilentlyContinue
            if ($existingProcess) { throw 'backend already running' }
        }
    }

    $backend = Start-Process -FilePath $script:BackendBin -PassThru -WindowStyle Hidden -RedirectStandardOutput $script:BackendLog -RedirectStandardError $script:BackendLog
    Start-Sleep -Seconds 1
    if ($backend.HasExited) { throw "backend failed to start; see $script:BackendLog" }
    Set-Content -Path $script:BackendPidFile -Value $backend.Id
}

function Stop-ProgramBackend {
    if (-not (Test-Path $script:BackendPidFile)) { return }
    $pidValue = (Get-Content $script:BackendPidFile | Select-Object -First 1).Trim()
    if ($pidValue) {
        Stop-Process -Id ([int]$pidValue) -Force -ErrorAction SilentlyContinue
    }
    Remove-Item $script:BackendPidFile -Force -ErrorAction SilentlyContinue
}
