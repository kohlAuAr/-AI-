param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $projectRoot 'frontend'
if (-not (Test-Path -LiteralPath (Join-Path $frontendRoot 'node_modules\vite\bin\vite.js'))) {
    throw 'Run npm install in frontend first.'
}
Write-Host 'Prototype only: no Java or AI service is required.'
Write-Host 'Phone: connect to the same reachable LAN and open a Network URL printed by Vite.'
Write-Host 'Use Ctrl+C to stop. Do not publish this development server to the Internet.'
Push-Location $frontendRoot
try { & node node_modules/vite/bin/vite.js --config vite.prototype.config.ts }
finally { Pop-Location }
