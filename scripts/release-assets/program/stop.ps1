$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir 'scripts/program-common.ps1')

Set-Location $ScriptDir
Import-ProgramEnv -Optional
Initialize-ProgramRuntime
Stop-NginxProcess
Stop-ProgramBackend
Write-Host "[program-stop] stopped zenmind-app-server"
