$script:BundleRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$script:EnvFile = Join-Path $script:BundleRoot '.env'
$script:BackendBin = Join-Path $script:BundleRoot 'backend/app.exe'
$script:FrontendDir = Join-Path $script:BundleRoot 'frontend'
$script:DistDir = Join-Path $script:FrontendDir 'dist'
$script:NginxTemplate = Join-Path $script:FrontendDir 'nginx.conf'
$script:RunDir = Join-Path $script:BundleRoot 'run'
$script:LogDir = Join-Path $script:RunDir 'logs'
$script:NginxPrefixDir = Join-Path $script:RunDir 'nginx'
$script:RenderedNginxConf = Join-Path $script:RunDir 'nginx.conf'
$script:NginxPidFile = Join-Path $script:NginxPrefixDir 'logs/nginx.pid'
$script:NginxAccessLog = Join-Path $script:LogDir 'nginx.access.log'
$script:NginxErrorLog = Join-Path $script:LogDir 'nginx.error.log'
$script:BackendLog = Join-Path $script:LogDir 'backend.log'
$script:BackendPidFile = Join-Path $script:RunDir 'backend.pid'
$script:DataDir = Join-Path $script:BundleRoot 'data'

function Import-ProgramEnv {
    param([switch]$Optional)

    if (-not (Test-Path $script:EnvFile)) {
        if ($Optional) {
            $env:SERVER_PORT = if ($env:SERVER_PORT) { $env:SERVER_PORT } else { '18080' }
            $env:FRONTEND_PORT = if ($env:FRONTEND_PORT) { $env:FRONTEND_PORT } else { '11950' }
            $env:NGINX_BIN = if ($env:NGINX_BIN) { $env:NGINX_BIN } else { 'nginx' }
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
    $env:FRONTEND_PORT = if ($env:FRONTEND_PORT) { $env:FRONTEND_PORT } else { '11950' }
    $env:NGINX_BIN = if ($env:NGINX_BIN) { $env:NGINX_BIN } else { 'nginx' }
    $env:AUTH_DB_PATH = if ($env:AUTH_DB_PATH) { $env:AUTH_DB_PATH } else { '.\data\auth.db' }
}

function Initialize-ProgramRuntime {
    New-Item -ItemType Directory -Force -Path $script:DataDir, $script:LogDir, (Join-Path $script:NginxPrefixDir 'logs') | Out-Null
    $script:ServerPort = $env:SERVER_PORT
    $script:FrontendPort = $env:FRONTEND_PORT
    $script:NginxBin = $env:NGINX_BIN
}

function Initialize-ProgramBundle {
    Initialize-ProgramRuntime
    if (-not (Test-Path $script:BackendBin)) { throw "missing backend binary: $script:BackendBin" }
    if (-not (Test-Path (Join-Path $script:DistDir 'index.html'))) { throw "missing frontend dist: $script:DistDir\index.html" }
    if (-not (Test-Path $script:NginxTemplate)) { throw "missing nginx config template: $script:NginxTemplate" }
}

function Assert-NginxAvailable {
    $cmd = Get-Command $script:NginxBin -ErrorAction SilentlyContinue
    if (-not $cmd) { throw "nginx is required; set NGINX_BIN if nginx is not on PATH" }
    $script:NginxCommand = $cmd.Source
}

function Render-NginxConfig {
    $template = Get-Content $script:NginxTemplate -Raw
    $content = $template.Replace('__DIST_DIR__', $script:DistDir.Replace('\', '/')) `
        .Replace('__SERVER_PORT__', [string]$script:ServerPort) `
        .Replace('__FRONTEND_PORT__', [string]$script:FrontendPort) `
        .Replace('__NGINX_PID_FILE__', ($script:NginxPidFile.Replace('\', '/'))) `
        .Replace('__NGINX_ACCESS_LOG__', ($script:NginxAccessLog.Replace('\', '/'))) `
        .Replace('__NGINX_ERROR_LOG__', ($script:NginxErrorLog.Replace('\', '/')))
    Set-Content -Path $script:RenderedNginxConf -Value $content -NoNewline
}

function Test-NginxConfig {
    & $script:NginxCommand -t -p $script:NginxPrefixDir -c $script:RenderedNginxConf | Out-Null
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

function Start-OrReload-Nginx {
    if (Test-Path $script:NginxPidFile) {
        $nginxPid = (Get-Content $script:NginxPidFile | Select-Object -First 1).Trim()
        if ($nginxPid -and (Get-Process -Id ([int]$nginxPid) -ErrorAction SilentlyContinue)) {
            & $script:NginxCommand -p $script:NginxPrefixDir -c $script:RenderedNginxConf -s reload | Out-Null
            return
        }
    }
    & $script:NginxCommand -p $script:NginxPrefixDir -c $script:RenderedNginxConf | Out-Null
}

function Stop-NginxProcess {
    if (-not (Test-Path $script:RenderedNginxConf)) { return }
    if (Test-Path $script:NginxPidFile) {
        & $script:NginxCommand -p $script:NginxPrefixDir -c $script:RenderedNginxConf -s stop | Out-Null
    }
}
