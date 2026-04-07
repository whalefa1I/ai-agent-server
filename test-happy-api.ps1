# Happy API 测试脚本 - PowerShell 版本
# 用途：模拟用户发送消息并观察前后端响应

$ServerUrl = "http://localhost:8080"
$AccountId = "account-test-$(Get-Date -UFormat %s)"
$SessionId = "session-test-$(Get-Date -UFormat %s)"

Write-Host "=== Happy API 测试 ===" -ForegroundColor Green
Write-Host "Server: $ServerUrl"
Write-Host "Account: $AccountId"
Write-Host "Session: $SessionId"
Write-Host ""

# 获取或生成 API Key
$ApiKey = Get-Content "$env:USERPROFILE\.happy-api-key" -ErrorAction SilentlyContinue
if (-not $ApiKey) {
    Write-Host "生成新的 API Key..." -ForegroundColor Yellow
    $ApiKeyResponse = Invoke-RestMethod -Uri "$ServerUrl/api/auth/apikey/generate" -Method Post -ContentType "application/json"
    $ApiKey = $ApiKeyResponse.apiKey
    $KeyPrefix = $ApiKeyResponse.keyPrefix
    Write-Host "API Key: $KeyPrefix***" -ForegroundColor Cyan
    $ApiKey | Out-File "$env:USERPROFILE\.happy-api-key" -Encoding utf8
}

# 测试 1: 发送用户消息
Write-Host ""
Write-Host "=== 测试 1: 发送用户消息 ===" -ForegroundColor Cyan

$Timestamp = [int][double]::Parse((Get-Date -UFormat %s))
$ArtifactId = "test-msg-$Timestamp"

$headerObj = @{
    type = "message"
    subtype = "user-message"
    title = "User Message"
    timestamp = $Timestamp
}
$bodyObj = @{
    type = "user-message"
    content = "你好，请创建一个测试文件 test.txt，内容为'这是个测试文件'"
    timestamp = $Timestamp
}

$HeaderJson = $headerObj | ConvertTo-Json -Compress
$BodyJson = $bodyObj | ConvertTo-Json -Compress

$HeaderBase64 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($HeaderJson))
$BodyBase64 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($BodyJson))

$artifact = @{
    id = $ArtifactId
    accountId = $AccountId
    sessionId = $SessionId
    header = $HeaderBase64
    body = $BodyBase64
    dataEncryptionKey = ""
    headerVersion = 1
    bodyVersion = 1
    seq = 0
    createdAt = $Timestamp * 1000
    updatedAt = $Timestamp * 1000
} | ConvertTo-Json

Write-Host "发送消息..." -ForegroundColor Yellow
$response = Invoke-RestMethod -Uri "$ServerUrl/api/v1/artifacts" `
    -Method Post `
    -ContentType "application/json" `
    -Headers @{ "X-API-Key" = $ApiKey } `
    -Body $artifact

Write-Host "响应:" -ForegroundColor Green
$response | ConvertTo-Json | Write-Host

# 等待 AI 处理
Write-Host ""
Write-Host "等待 AI 处理..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

# 测试 2: 获取 artifacts
Write-Host ""
Write-Host "=== 测试 2: 获取 artifacts ===" -ForegroundColor Cyan

$artifacts = Invoke-RestMethod -Uri "$ServerUrl/api/v1/artifacts?accountId=$AccountId" `
    -Headers @{ "X-API-Key" = $ApiKey }

Write-Host "找到 $($artifacts.Count) 个 artifacts:" -ForegroundColor Green
$artifacts | ForEach-Object {
    Write-Host "  - ID: $($_.id), HeaderVersion: $($_.headerVersion), BodyVersion: $($_.bodyVersion)"
}

# 测试 3: 解析并显示 artifact 内容
Write-Host ""
Write-Host "=== 测试 3: 解析 artifact 内容 ===" -ForegroundColor Cyan

foreach ($artifact in $artifacts) {
    Write-Host "`nArtifact: $($artifact.id)" -ForegroundColor Yellow

    if ($artifact.header) {
        try {
            $headerContent = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($artifact.header))
            $headerObj = $headerContent | ConvertFrom-Json
            Write-Host "  Type: $($headerObj.type)" -ForegroundColor Cyan
            Write-Host "  Subtype: $($headerObj.subtype)" -ForegroundColor Cyan
            if ($headerObj.title) {
                Write-Host "  Title: $($headerObj.title)" -ForegroundColor Cyan
            }
        } catch {
            Write-Host "  Header 解析失败" -ForegroundColor Red
        }
    }

    if ($artifact.body -and $artifact.body -ne "") {
        try {
            $bodyContent = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($artifact.body))
            $bodyObj = $bodyContent | ConvertFrom-Json
            if ($bodyObj.content) {
                Write-Host "  Content: $($bodyObj.content)" -ForegroundColor Green
            }
            if ($bodyObj.status) {
                Write-Host "  Status: $($bodyObj.status)" -ForegroundColor Yellow
            }
            if ($bodyObj.output) {
                Write-Host "  Output: $($bodyObj.output)" -ForegroundColor Green
            }
            if ($bodyObj.error) {
                Write-Host "  Error: $($bodyObj.error)" -ForegroundColor Red
            }
        } catch {
            Write-Host "  Body 解析失败" -ForegroundColor Red
        }
    }
}

Write-Host ""
Write-Host "=== 测试结束 ===" -ForegroundColor Green
