$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$processFile = Join-Path $projectRoot '.run\dev-processes.json'
if (-not (Test-Path -LiteralPath $processFile)) { Write-Host 'No tracked processes.'; exit 0 }
foreach ($tracked in (Get-Content -LiteralPath $processFile -Raw | ConvertFrom-Json)) {
    $processInfo = Get-CimInstance Win32_Process -Filter ("ProcessId = " + [int]$tracked.processId)
    if (-not $processInfo) { continue }
    $commandLine = [string]$processInfo.CommandLine
    if ($commandLine.IndexOf($projectRoot, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
        throw "Process $($tracked.processId) no longer belongs to this project. It was not stopped."
    }
    Stop-Process -Id $tracked.processId
    Write-Host "Stopped $($tracked.name)."
}
Remove-Item -LiteralPath $processFile
