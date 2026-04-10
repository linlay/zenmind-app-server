$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir 'scripts/program-common.ps1')

Set-Location $ScriptDir
Import-ProgramEnv
Initialize-ProgramBundle

Write-Host ("[program-deploy] backend binary: {0}" -f $script:BackendBin)
Write-Host ("[program-deploy] frontend dist: {0}" -f $script:DistDir)
