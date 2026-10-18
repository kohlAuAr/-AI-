param([ValidateSet('start', 'stop', 'status')][string]$Action = 'status')
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Split-Path -Parent $PSScriptRoot)).Path
$config = Join-Path $projectRoot 'infra\redis-local.conf'
$runDirectory = Join-Path $projectRoot '.run\redis'
$pidFile = Join-Path $runDirectory 'process.json'
$tracked = if (Test-Path -LiteralPath $pidFile) { Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json } else { $null }
$process = if ($tracked) { Get-CimInstance Win32_Process -Filter "ProcessId = $($tracked.processId)" } else { $null }
if ($process -and ($process.Name -ne 'redis-server.exe' -or -not $process.CommandLine.Contains($config))) {
    throw 'Tracked PID no longer belongs to project Redis. Nothing was stopped.'
}
if ($Action -eq 'status') {
    if ($process) { Write-Host "Project Redis PID $($process.ProcessId), address 127.0.0.1:16379" }
    else { Write-Host 'Project Redis is not running.' }
    exit
}
if ($Action -eq 'stop') {
    if ($process) {
        Stop-Process -Id $process.ProcessId
        Wait-Process -Id $process.ProcessId -Timeout 15 -ErrorAction SilentlyContinue
        Write-Host 'Stopped only project Redis. Temporary cache and rate windows were discarded; business databases unchanged.'
    }
    exit
}
if ($process) { Write-Host 'Project Redis is already running.'; exit }
if (Get-NetTCPConnection -State Listen -LocalPort 16379 -ErrorAction SilentlyContinue) {
    throw 'Port 16379 belongs to another process. No process was stopped.'
}
$executable = (Get-Command redis-server.exe -ErrorAction Stop).Source
$null = New-Item -ItemType Directory -Path $runDirectory -Force
$started = Start-Process -FilePath $executable -ArgumentList ('"' + $config + '"') -WorkingDirectory $runDirectory -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runDirectory 'server.log') -RedirectStandardError (Join-Path $runDirectory 'error.log') -PassThru
@{ processId = $started.Id } | ConvertTo-Json | Set-Content -LiteralPath $pidFile -Encoding UTF8
Write-Host "Started isolated local Redis PID $($started.Id) on 16379 using the already installed executable. No models or services on 6379 changed."
