#requires -Version 7.2
[CmdletBinding()]
param([switch]$NoLaunch)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'local-environment.ps1')
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$null = Get-LocalJava
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Installa Docker Desktop e avvialo prima di eseguire questo script.' }
& docker info --format '{{.ServerVersion}}' *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop non è pronto: avvialo e attendi che il motore sia disponibile.' }
& docker compose version *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose non disponibile: aggiorna Docker Desktop.' }
$configPath = Join-Path $projectRoot '.env'
if (-not (Test-Path -LiteralPath $configPath)) {
    $config = [IO.File]::ReadAllText((Join-Path $projectRoot '.env.example'))
    foreach ($name in @('POSTGRES_SUPERUSER_PASSWORD', 'UNINAFOODLAB_DB_MIGRATOR_PASSWORD', 'UNINAFOODLAB_DB_PASSWORD')) {
        $password = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()
        $config = [regex]::Replace($config, "(?m)^$name=.*$", "$name=$password")
    }

    $stream = [IO.File]::Open($configPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
    try {
        $bytes = [Text.UTF8Encoding]::new($false).GetBytes($config)
        $stream.Write($bytes, 0, $bytes.Length)
    } finally { $stream.Dispose() }
    Write-Host 'Creata .env con password casuali: conserva questo file privato insieme ai backup del database.'
}
$previous = Import-LocalConfiguration $projectRoot
Push-Location $projectRoot
try {
    & docker compose --env-file $configPath up -d --wait
    if ($LASTEXITCODE -ne 0) { throw 'Avvio PostgreSQL non riuscito. Controlla Docker e la porta di .env; non eliminare il volume per risolvere un errore.' }
    if ($NoLaunch) { Write-Host 'PostgreSQL pronto. Le migrazioni saranno applicate al lancio della GUI.'; return }
    [Environment]::SetEnvironmentVariable('POSTGRES_SUPERUSER_PASSWORD', $null, 'Process')
    & .\mvnw.cmd javafx:run
    if ($LASTEXITCODE -ne 0) { throw 'Avvio app non riuscito: consulta gli errori precedenti. Configurazione e dati sono stati conservati.' }
} finally {
    Pop-Location
    Restore-LocalConfiguration $previous
}
