$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir 'scripts/program-common.ps1')

Set-Location $ScriptDir
Import-ProgramEnv
Initialize-ProgramBundle
Start-ProgramBackend

Write-Host "[program-start] started zenmind-app-server"
