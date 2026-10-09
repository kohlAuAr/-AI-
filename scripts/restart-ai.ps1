param([switch]$BackupData)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Split-Path -Parent $PSScriptRoot)).Path
$runDirectory = Join-Path $projectRoot '.run'
$processFile = Join-Path $runDirectory 'dev-processes.json'
$aiJar = Join-Path $projectRoot 'ai-service\target\ai-service-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $aiJar)) { throw 'Package ai-service first.' }
if (-not (Test-Path -LiteralPath $processFile)) { throw 'No tracked developer processes. Use start-dev.ps1 first.' }
$tracked = Get-Content -LiteralPath $processFile -Raw | ConvertFrom-Json
$ai = @($tracked | Where-Object { $_.name -eq 'ai-service' })
if ($ai.Count -ne 1) { throw 'Expected exactly one tracked AI process.' }
$existing = Get-CimInstance Win32_Process -Filter "ProcessId = $($ai[0].processId)"
if ($existing) {
    if ($existing.Name -notmatch '^java(w)?\.exe$' -or -not $existing.CommandLine.Contains($aiJar)) { throw 'PID belongs to another process; nothing was stopped.' }
    Stop-Process -Id $existing.ProcessId
    Wait-Process -Id $existing.ProcessId -Timeout 20 -ErrorAction SilentlyContinue
}
if (Get-NetTCPConnection -State Listen -LocalPort 8091 -ErrorAction SilentlyContinue) { throw 'Port 8091 is occupied; no untracked process was stopped.' }
if ($BackupData) {
    $database = Join-Path $projectRoot 'ai-service\data\ai.mv.db'
    if (Test-Path -LiteralPath $database) {
        $destination = Join-Path $runDirectory ('ai-backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
        $null = New-Item -ItemType Directory -Path $destination
        Copy-Item -LiteralPath $database -Destination (Join-Path $destination 'ai.mv.db')
        Write-Host "Closed H2 database copied to $destination"
    }
}
# Read only known keys. Ollama is user-managed and is never started/stopped here.
$allowedKeys = @('AI_MODE', 'AI_REDIS_ENABLED', 'CHAT_BASE_URL', 'CHAT_MODEL', 'CHAT_API_KEY', 'EMBEDDING_PROVIDER', 'EMBEDDING_BASE_URL', 'EMBEDDING_MODEL', 'EMBEDDING_API_KEY', 'REDIS_HOST', 'REDIS_PORT', 'REDIS_PASSWORD', 'DB_HOST', 'DB_PORT', 'DB_USER', 'DB_PASSWORD', 'SPRING_PROFILES_ACTIVE')
$localEnvironment = Join-Path $projectRoot '.env.local'
if (Test-Path -LiteralPath $localEnvironment) {
    foreach ($line in Get-Content -LiteralPath $localEnvironment -Encoding UTF8) {
        if ($line -match '^\s*(#|$)') { continue }
        $parts = $line.Split('=', 2)
        if ($parts.Length -ne 2 -or $allowedKeys -notcontains $parts[0].Trim()) { throw 'Unsupported local environment setting.' }
        [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), 'Process')
    }
}
$javaExecutable = (Get-Command java).Source
$started = Start-Process -FilePath $javaExecutable -ArgumentList @('-Dfile.encoding=UTF-8', '-jar', ('"' + $aiJar + '"')) -WorkingDirectory (Join-Path $projectRoot 'ai-service') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runDirectory 'ai.log') -RedirectStandardError (Join-Path $runDirectory 'ai-error.log') -PassThru
$ai[0].processId = $started.Id
$tracked | ConvertTo-Json | Set-Content -LiteralPath $processFile -Encoding UTF8
Write-Host "Only ai-service restarted (PID $($started.Id)); campus, frontend and Ollama unchanged."
