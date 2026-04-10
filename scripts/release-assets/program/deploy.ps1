$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir 'scripts/program-common.ps1')

Set-Location $ScriptDir
Import-ProgramEnv
Initialize-ProgramBundle
Assert-NginxAvailable
Render-NginxConfig
Test-NginxConfig

Write-Host ("[program-deploy] backend binary: {0}" -f $script:BackendBin)
Write-Host ("[program-deploy] nginx template: {0}" -f $script:NginxTemplate)
Write-Host ("[program-deploy] rendered nginx config: {0}" -f $script:RenderedNginxConf)
Write-Host ("[program-deploy] browser: http://127.0.0.1:{0}/admin/" -f $script:FrontendPort)
