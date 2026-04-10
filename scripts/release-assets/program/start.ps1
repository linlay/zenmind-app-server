$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir 'scripts/program-common.ps1')

Set-Location $ScriptDir
Import-ProgramEnv
Initialize-ProgramBundle
Assert-NginxAvailable
Render-NginxConfig
Start-ProgramBackend
Start-OrReload-Nginx

Write-Host "[program-start] started zenmind-app-server"
Write-Host ("[program-start] browser: http://127.0.0.1:{0}/admin/" -f $script:FrontendPort)
