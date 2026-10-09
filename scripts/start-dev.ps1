param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runDirectory = Join-Path $projectRoot '.run'
$processFile = Join-Path $runDirectory 'dev-processes.json'
$campusJar = Join-Path $projectRoot 'campus-service\target\campus-service-0.1.0-SNAPSHOT.jar'
$aiJar = Join-Path $projectRoot 'ai-service\target\ai-service-0.1.0-SNAPSHOT.jar'
$viteEntry = Join-Path $projectRoot 'frontend\node_modules\vite\bin\vite.js'
foreach ($requiredFile in @($campusJar, $aiJar, $viteEntry)) {
    if (-not (Test-Path -LiteralPath $requiredFile)) { throw 'Build the backends and install frontend dependencies first. See README.md.' }
}
if (Test-Path -LiteralPath $processFile) {
    foreach ($tracked in (Get-Content -LiteralPath $processFile -Raw | ConvertFrom-Json)) {
        if (Get-Process -Id $tracked.processId -ErrorAction SilentlyContinue) { throw 'A tracked process is still running. Run scripts/stop-dev.ps1 first.' }
    }
}
foreach ($port in @(8090, 8091, 5178)) {
    if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) { throw "Port $port is occupied. No existing process was stopped." }
}
# Only parse known KEY=VALUE settings; the file is never executed as shell code.
$localEnvironment = Join-Path $projectRoot '.env.local'
$allowedKeys = @('AI_MODE', 'AI_REDIS_ENABLED', 'CHAT_BASE_URL', 'CHAT_MODEL', 'CHAT_API_KEY', 'EMBEDDING_PROVIDER', 'EMBEDDING_BASE_URL', 'EMBEDDING_MODEL', 'EMBEDDING_API_KEY', 'REDIS_HOST', 'REDIS_PORT', 'REDIS_PASSWORD', 'DB_HOST', 'DB_PORT', 'DB_USER', 'DB_PASSWORD', 'SPRING_PROFILES_ACTIVE')
if (Test-Path -LiteralPath $localEnvironment) {
    foreach ($line in Get-Content -LiteralPath $localEnvironment -Encoding UTF8) {
        if ($line -match '^\s*(#|$)') { continue }
        $parts = $line.Split('=', 2)
        if ($parts.Length -ne 2 -or $allowedKeys -notcontains $parts[0].Trim()) { throw 'Unsupported setting in .env.local. Use .env.example as the template.' }
        [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), 'Process')
    }
}
$null = New-Item -ItemType Directory -Path $runDirectory -Force
$javaExecutable = (Get-Command java).Source
$nodeExecutable = (Get-Command node).Source
$startedProcesses = @()
try {
    $aiProcess = Start-Process -FilePath $javaExecutable -ArgumentList @('-Dfile.encoding=UTF-8', '-jar', ('"' + $aiJar + '"')) -WorkingDirectory (Join-Path $projectRoot 'ai-service') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runDirectory 'ai.log') -RedirectStandardError (Join-Path $runDirectory 'ai-error.log') -PassThru
    $startedProcesses += @{ name = 'ai-service'; processId = $aiProcess.Id }
    $campusProcess = Start-Process -FilePath $javaExecutable -ArgumentList @('-Dfile.encoding=UTF-8', '-jar', ('"' + $campusJar + '"')) -WorkingDirectory (Join-Path $projectRoot 'campus-service') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runDirectory 'campus.log') -RedirectStandardError (Join-Path $runDirectory 'campus-error.log') -PassThru
    $startedProcesses += @{ name = 'campus-service'; processId = $campusProcess.Id }
    $frontendProcess = Start-Process -FilePath $nodeExecutable -ArgumentList ('"' + $viteEntry + '"') -WorkingDirectory (Join-Path $projectRoot 'frontend') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runDirectory 'frontend.log') -RedirectStandardError (Join-Path $runDirectory 'frontend-error.log') -PassThru
    $startedProcesses += @{ name = 'frontend'; processId = $frontendProcess.Id }
    $startedProcesses | ConvertTo-Json | Set-Content -LiteralPath $processFile -Encoding UTF8
} catch {
    foreach ($started in $startedProcesses) { Stop-Process -Id $started.processId -ErrorAction SilentlyContinue }
    throw
}
Write-Host 'Processes started. Allow about 15 seconds for Spring Boot initialization.'
Write-Host 'Open http://127.0.0.1:5178 ; logs are in .run/'
Write-Host 'Verify: node scripts/smoke.mjs'
