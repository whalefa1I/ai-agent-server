#!/usr/bin/env pwsh
# 模型调用测试脚本 - 测试 Bailian/Qwen 平台集成
# 使用方法：.\tests\test-model-call.ps1

param(
    [string]$BaseUrl = "https://coding.dashscope.aliyuncs.com/v1",
    [string]$ApiKey = "sk-sp-ab63f62c8df3494a8763982b1a741081",
    [string]$Model = "qwen-max"
)

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  模型调用测试 - Bailian/Qwen 平台" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 测试 1: 直接 API 调用（验证 API Key 和网络）
Write-Host "[测试 1] 直接 API 调用测试" -ForegroundColor Yellow
try {
    $headers = @{
        "Authorization" = "Bearer $ApiKey"
        "Content-Type" = "application/json"
    }

    $body = @{
        model = $Model
        messages = @(
            @{
                role = "user"
                content = "请用一句话介绍你自己"
            }
        )
        max_tokens = 100
    } | ConvertTo-Json -Depth 10

    Write-Host "  发送请求到：$BaseUrl/chat/completions" -ForegroundColor Gray
    $response = Invoke-RestMethod -Uri "$BaseUrl/chat/completions" -Method Post -Headers $headers -Body $body

    if ($response.choices -and $response.choices.Count -gt 0) {
        Write-Host "  [PASS] API 调用成功" -ForegroundColor Green
        Write-Host "  回复：$($response.choices[0].message.content)" -ForegroundColor Gray
    } else {
        Write-Host "  [FAIL] API 返回异常" -ForegroundColor Red
        Write-Host "  响应：$($response | ConvertTo-Json)" -ForegroundColor Red
    }
} catch {
    Write-Host "  [FAIL] API 调用失败：$($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# 测试 2: Spring Boot 应用健康检查
Write-Host "[测试 2] Spring Boot 应用健康检查" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/health" -Method Get -ErrorAction Stop
    if ($response.status -eq "UP") {
        Write-Host "  [PASS] 应用健康运行" -ForegroundColor Green
        Write-Host "  状态：$($response.status)" -ForegroundColor Gray
    } else {
        Write-Host "  [FAIL] 应用状态异常" -ForegroundColor Red
    }
} catch {
    Write-Host "  [FAIL] 应用未运行或无法访问：$($_.Exception.Message)" -ForegroundColor Red
    Write-Host "  提示：请先运行 'mvn spring-boot:run -Dspring-boot.run.profiles=local'" -ForegroundColor Yellow
}
Write-Host ""

# 测试 3: 会话 ID 获取
Write-Host "[测试 3] 会话管理测试" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/chat/session-id" -Method Get
    Write-Host "  [PASS] 获取会话 ID 成功" -ForegroundColor Green
    Write-Host "  SessionId: $($response.sessionId)" -ForegroundColor Gray
} catch {
    Write-Host "  [FAIL] 获取会话 ID 失败：$($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# 测试 4: 简单对话测试
Write-Host "[测试 4] 简单对话测试" -ForegroundColor Yellow
try {
    $body = @{
        message = "你好，请简单介绍一下自己"
    } | ConvertTo-Json

    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/chat" -Method Post -ContentType "application/json" -Body $body

    Write-Host "  请求：你好，请简单介绍一下自己" -ForegroundColor Gray
    if ($response.content) {
        Write-Host "  [PASS] 对话成功" -ForegroundColor Green
        Write-Host "  回复：$($response.content)" -ForegroundColor Gray
    } else {
        Write-Host "  [WARN] 回复内容为空" -ForegroundColor Yellow
        Write-Host "  响应：$($response | ConvertTo-Json)" -ForegroundColor Gray
    }
} catch {
    Write-Host "  [FAIL] 对话失败：$($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# 测试 5: 模型调用统计
Write-Host "[测试 5] 获取模型调用统计" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/observability/stats" -Method Get
    Write-Host "  [PASS] 获取统计成功" -ForegroundColor Green
    Write-Host "  输入 Token: $($response.totalInputTokens)" -ForegroundColor Gray
    Write-Host "  输出 Token: $($response.totalOutputTokens)" -ForegroundColor Gray
    Write-Host "  消息数量：$($response.messageCount)" -ForegroundColor Gray
} catch {
    Write-Host "  [FAIL] 获取统计失败：$($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  测试完成" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
