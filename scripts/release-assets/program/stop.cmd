@echo off
setlocal EnableExtensions

set "SCRIPT_DIR=%~dp0"
set "RUN_DIR=%SCRIPT_DIR%run"
set "BACKEND_PID_FILE=%RUN_DIR%\backend.pid"
set "FRONTEND_PID_FILE=%RUN_DIR%\frontend.pid"

powershell -NoProfile -Command ^
  "$files = @('%FRONTEND_PID_FILE%', '%BACKEND_PID_FILE%');" ^
  "foreach ($file in $files) {" ^
  "  if (Test-Path $file) {" ^
  "    $pid = (Get-Content $file | Select-Object -First 1).Trim();" ^
  "    if ($pid) { Stop-Process -Id ([int]$pid) -Force -ErrorAction SilentlyContinue };" ^
  "    Remove-Item $file -Force -ErrorAction SilentlyContinue;" ^
  "  }" ^
  "}"

echo [program-stop] stopped zenmind-app-server
