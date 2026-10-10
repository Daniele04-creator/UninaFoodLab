function Get-LocalJava {
    if (-not $env:JAVA_HOME) { throw 'Installa JDK 25 e imposta JAVA_HOME alla sua cartella; riapri PowerShell.' }
    $javaPath = Join-Path $env:JAVA_HOME 'bin/java.exe'
    $javacPath = Join-Path $env:JAVA_HOME 'bin/javac.exe'
    if (-not (Test-Path -LiteralPath $javaPath) -or -not (Test-Path -LiteralPath $javacPath)) {
        throw 'JAVA_HOME deve indicare un JDK 25 completo, non un JRE.'
    }
    $version = (& $javacPath -version 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $version -notmatch '^javac 25(?:\.|\s|$)') { throw 'Questo progetto richiede JDK 25. Correggi JAVA_HOME.' }
    return $javaPath
}

function Import-LocalConfiguration([string]$ProjectRoot) {
    $configPath = Join-Path $ProjectRoot '.env'
    if (-not (Test-Path -LiteralPath $configPath)) { throw 'Configurazione .env assente: esegui prima scripts/start-local.ps1 -NoLaunch.' }
    $names = @('POSTGRES_SUPERUSER_PASSWORD', 'UNINAFOODLAB_DB_NAME', 'UNINAFOODLAB_DB_PORT',
        'UNINAFOODLAB_DB_URL', 'UNINAFOODLAB_DB_USER', 'UNINAFOODLAB_DB_PASSWORD',
        'UNINAFOODLAB_DB_MIGRATOR_USER', 'UNINAFOODLAB_DB_MIGRATOR_PASSWORD', 'UNINAFOODLAB_ENV')
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $configPath) {
        if ($line -match '^\s*(#|$)') { continue }
        $name, $value = $line -split '=', 2
        $name = $name.Trim()
        if ($names -contains $name) {
            if ($null -eq $value -or [string]::IsNullOrWhiteSpace($value)) { throw "Variabile $name mancante in .env." }
            $value = $value.Trim()
            if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            $values[$name] = $value
        }
    }
    foreach ($name in $names) {
        if (-not $values.ContainsKey($name)) { throw "Variabile $name mancante in .env; consulta .env.example." }
    }
    if ($values.UNINAFOODLAB_DB_USER -ne 'uninafoodlab_app' -or $values.UNINAFOODLAB_DB_MIGRATOR_USER -ne 'uninafoodlab_migrator') {
        throw 'Il bootstrap Compose usa i ruoli uninafoodlab_app e uninafoodlab_migrator: mantieni questi nomi in .env.'
    }
    $expectedUrl = 'jdbc:postgresql://localhost:' + $values.UNINAFOODLAB_DB_PORT + '/' + $values.UNINAFOODLAB_DB_NAME
    if ($values.UNINAFOODLAB_DB_URL -ne $expectedUrl) { throw 'UNINAFOODLAB_DB_URL deve usare localhost e coincidere con DB_PORT e DB_NAME di .env.' }
    $previous = @{}
    foreach ($name in $names) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process')
    }
    return $previous
}

function Restore-LocalConfiguration([hashtable]$Previous) {
    foreach ($name in $Previous.Keys) { [Environment]::SetEnvironmentVariable($name, $Previous[$name], 'Process') }
}
