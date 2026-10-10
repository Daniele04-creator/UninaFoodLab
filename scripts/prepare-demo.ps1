

#requires -Version 7.2
[CmdletBinding()]
param([string]$ReferenceDate)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ($ReferenceDate) {
    $null = [DateTime]::ParseExact($ReferenceDate, 'yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture)
}
. (Join-Path $PSScriptRoot 'local-environment.ps1')
$java = Get-LocalJava
& (Join-Path $PSScriptRoot 'start-local.ps1') -NoLaunch
$previous = Import-LocalConfiguration $projectRoot
Push-Location $projectRoot
try {
    if ($env:UNINAFOODLAB_ENV -ne 'development') { throw 'Lo scenario demo richiede UNINAFOODLAB_ENV=development.' }
    [Environment]::SetEnvironmentVariable('POSTGRES_SUPERUSER_PASSWORD', $null, 'Process')
    & .\mvnw.cmd -q '-Dmaven.test.skip=true' compile dependency:build-classpath '-DincludeScope=runtime' '-Dmdep.outputFile=target/demo-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Compilazione demo non riuscita.' }
    $dependencies = (Get-Content -LiteralPath 'target/demo-classpath.txt' -Raw).Trim()
    $classpath = (Join-Path $projectRoot 'target/classes') + [IO.Path]::PathSeparator + $dependencies
    $arguments = @('-cp', $classpath, 'it.uninafoodlab.infrastructure.demo.InitializePresentationDemo')
    if ($ReferenceDate) { $arguments += $ReferenceDate }
    [Environment]::SetEnvironmentVariable('POSTGRES_SUPERUSER_PASSWORD', $null, 'Process')
    & $java @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Preparazione demo non riuscita; consultare il messaggio precedente.' }
} finally {
    Pop-Location
    Restore-LocalConfiguration $previous
}
