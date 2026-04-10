@echo off
setlocal EnableExtensions

set "SCRIPT_DIR=%~dp0"
set "ENV_FILE=%SCRIPT_DIR%.env"
set "RUN_DIR=%SCRIPT_DIR%run"
set "LOG_DIR=%RUN_DIR%\logs"
set "BACKEND_PID_FILE=%RUN_DIR%\backend.pid"
set "FRONTEND_PID_FILE=%RUN_DIR%\frontend.pid"

if not exist "%ENV_FILE%" (
  echo [program-start] missing .env (copy from .env.example first) 1>&2
  exit /b 1
)

if not exist "%SCRIPT_DIR%backend\app.exe" (
  echo [program-start] missing backend binary 1>&2
  exit /b 1
)

if not exist "%SCRIPT_DIR%frontend\frontend-gateway.exe" (
  echo [program-start] missing frontend gateway binary 1>&2
  exit /b 1
)

if not exist "%RUN_DIR%" mkdir "%RUN_DIR%"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
if not exist "%SCRIPT_DIR%data" mkdir "%SCRIPT_DIR%data"

for /f "usebackq delims=" %%i in (`powershell -NoProfile -Command "$envPath = '%ENV_FILE%'; Get-Content $envPath | ForEach-Object { if ($_ -match '^[A-Za-z_][A-Za-z0-9_]*=') { $name, $value = $_ -split '=', 2; [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim(), 'Process') } }; if (-not $env:SERVER_PORT) { $env:SERVER_PORT = '8080' }; if (-not $env:FRONTEND_PORT) { $env:FRONTEND_PORT = '11950' }; $env:AUTH_DB_PATH = if ($env:AUTH_DB_PATH) { $env:AUTH_DB_PATH } else { Join-Path '%SCRIPT_DIR%' 'data\auth.db' }; $env:AUTH_SCHEMA_PATH = if ($env:AUTH_SCHEMA_PATH) { $env:AUTH_SCHEMA_PATH } else { Join-Path '%SCRIPT_DIR%' 'backend\schema.sql' }; $env:AUTH_CONFIG_FILES_REGISTRY_PATH = if ($env:AUTH_CONFIG_FILES_REGISTRY_PATH) { $env:AUTH_CONFIG_FILES_REGISTRY_PATH } else { Join-Path '%SCRIPT_DIR%' 'config\config-files.runtime.yml' }; $env:BACKEND_TARGET = if ($env:BACKEND_TARGET) { $env:BACKEND_TARGET } else { 'http://127.0.0.1:' + $env:SERVER_PORT }; $env:LISTEN_ADDR = if ($env:LISTEN_ADDR) { $env:LISTEN_ADDR } else { ':' + $env:FRONTEND_PORT }; $env:STATIC_DIR = if ($env:STATIC_DIR) { $env:STATIC_DIR } else { Join-Path '%SCRIPT_DIR%' 'frontend\dist' }; $backend = Start-Process -FilePath (Join-Path '%SCRIPT_DIR%' 'backend\app.exe') -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path '%LOG_DIR%' 'backend.log') -RedirectStandardError (Join-Path '%LOG_DIR%' 'backend.log'); Start-Sleep -Seconds 1; if ($backend.HasExited) { throw 'backend failed to start' }; $frontend = Start-Process -FilePath (Join-Path '%SCRIPT_DIR%' 'frontend\frontend-gateway.exe') -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path '%LOG_DIR%' 'frontend.log') -RedirectStandardError (Join-Path '%LOG_DIR%' 'frontend.log'); Start-Sleep -Seconds 1; if ($frontend.HasExited) { Stop-Process -Id $backend.Id -Force -ErrorAction SilentlyContinue; throw 'frontend failed to start' }; Set-Content -Path '%BACKEND_PID_FILE%' -Value $backend.Id; Set-Content -Path '%FRONTEND_PID_FILE%' -Value $frontend.Id; Write-Output $env:FRONTEND_PORT"` ) do set "FRONTEND_PORT=%%i"

if errorlevel 1 exit /b 1

echo [program-start] started zenmind-app-server
echo [program-start] browser: http://127.0.0.1:%FRONTEND_PORT%/admin/
