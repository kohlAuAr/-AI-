$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$localFile = Join-Path $projectRoot '.env.local'
$template = Join-Path $projectRoot '.env.example'
$content = if (Test-Path -LiteralPath $localFile) { [IO.File]::ReadAllText($localFile) } else { [IO.File]::ReadAllText($template) }
if ($content -match '(?m)^[ \t]*AI_INTERNAL_TOKEN[ \t]*=[ \t]*\S+[ \t]*\r?$') {
    Write-Host 'Shared internal token already configured; existing settings unchanged.'
    exit 0
}
$bytes = New-Object byte[] 32
$random = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $random.GetBytes($bytes) } finally { $random.Dispose() }
$token = [Convert]::ToBase64String($bytes)
if ($content -match '(?m)^[ \t]*AI_INTERNAL_TOKEN[ \t]*=.*$') { $content = [regex]::Replace($content, '(?m)^[ \t]*AI_INTERNAL_TOKEN[ \t]*=.*$', ('AI_INTERNAL_TOKEN=' + $token)) }
else { $content = $content.TrimEnd() + "`r`nAI_INTERNAL_TOKEN=$token`r`n" }
[IO.File]::WriteAllText($localFile, $content, (New-Object Text.UTF8Encoding($false)))
Write-Host 'Local shared internal token generated. No value was printed; .env.local must stay ignored.'
