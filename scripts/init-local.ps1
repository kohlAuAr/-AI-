$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$localFile = Join-Path $projectRoot '.env.local'
$template = Join-Path $projectRoot '.env.example'
$content = if (Test-Path -LiteralPath $localFile) { [IO.File]::ReadAllText($localFile) } else { [IO.File]::ReadAllText($template) }
foreach ($setting in @('AI_INTERNAL_TOKEN', 'AI_CONFIG_MASTER_KEY')) {
    if ($content -match "(?m)^[ \t]*$setting[ \t]*=[ \t]*\S+[ \t]*\r?$") { continue }
    $bytes = New-Object byte[] 32
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    $generatedValue = [Convert]::ToBase64String($bytes)
    if ($content -match "(?m)^[ \t]*$setting[ \t]*=.*$") { $content = [regex]::Replace($content, "(?m)^[ \t]*$setting[ \t]*=.*$", ($setting + '=' + $generatedValue)) }
    else { $content = $content.TrimEnd() + "`r`n$setting=$generatedValue`r`n" }
}
if ($content -notmatch '(?m)^[ \t]*AI_MODEL_ALLOWED_URLS[ \t]*=') {
    $content = $content.TrimEnd() + "`r`nAI_MODEL_ALLOWED_URLS=http://127.0.0.1:11434,https://api.deepseek.com/v1,https://open.bigmodel.cn/api/paas/v4,https://dashscope.aliyuncs.com/compatible-mode/v1`r`n"
}
[IO.File]::WriteAllText($localFile, $content, (New-Object Text.UTF8Encoding($false)))
Write-Host 'Local internal token and model encryption key configured; existing values retained. No secrets printed. Back up .env.local securely; never commit it.'
