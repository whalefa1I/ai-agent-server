#!/usr/bin/env pwsh
# WebSocket 测试脚本 - 测试 WebSocket 连接和消息
# 使用方法：.\tests\test-websocket.ps1

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  WebSocket 测试 - 实时消息通信" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查应用状态
Write-Host "[前置检查] 应用状态" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/health" -Method Get
    Write-Host "  [PASS] 应用运行正常" -ForegroundColor Green
} catch {
    Write-Host "  [FAIL] 应用未运行" -ForegroundColor Red
    Write-Host "  提示：请先运行 'mvn spring-boot:run -Dspring-boot.run.profiles=local'" -ForegroundColor Yellow
    exit 1
}
Write-Host ""

# 获取 WebSocket Token（如果需要认证）
Write-Host "[步骤 1] 获取 WebSocket Token" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/ws/token" -Method Post -ContentType "application/json" -Body (ConvertTo-Json @{
        sessionId = "test-session"
    })
    $token = $response.token
    Write-Host "  [PASS] 获取 Token：$token" -ForegroundColor Green
} catch {
    Write-Host "  [INFO] Token 端点可能未启用（认证关闭）" -ForegroundColor Yellow
    $token = "test-token"
}
Write-Host ""

# 测试 WebSocket 连接
Write-Host "[步骤 2] 测试 WebSocket 连接" -ForegroundColor Yellow
Write-Host "  提示：PowerShell 原生不支持 WebSocket，使用 PowerShell 模块或浏览器测试" -ForegroundColor Gray
Write-Host ""
Write-Host "  WebSocket URL: ws://localhost:8080/ws/agent/$token" -ForegroundColor Cyan
Write-Host ""
Write-Host "  手动测试步骤：" -ForegroundColor Yellow
Write-Host "  1. 打开浏览器开发者工具 (F12)" -ForegroundColor Gray
Write-Host "  2. 在 Console 中运行：" -ForegroundColor Gray
Write-Host "     const ws = new WebSocket('ws://localhost:8080/ws/agent/$token');" -ForegroundColor White
Write-Host "     ws.onopen = () => console.log('Connected');" -ForegroundColor White
Write-Host "     ws.onmessage = (e) => console.log('Message:', e.data);" -ForegroundColor White
Write-Host "     ws.send(JSON.stringify({type: 'user', content: 'Hello'}));" -ForegroundColor White
Write-Host ""

# 使用 wscat 测试（如果安装）
Write-Host "[步骤 3] 使用 wscat 测试（如已安装）" -ForegroundColor Yellow
try {
    $wscat = Get-Command wscat -ErrorAction Stop
    Write-Host "  正在启动 wscat..." -ForegroundColor Gray
    # wscat -c "ws://localhost:8080/ws/agent/$token" -x '{"type":"user","content":"Hello"}'
    Write-Host "  命令：wscat -c `"ws://localhost:8080/ws/agent/$token`" -x `"{`"type`":`"user`",`"content`":`"Hello`"}`"" -ForegroundColor White
} catch {
    Write-Host "  [INFO] wscat 未安装，可使用以下命令安装：" -ForegroundColor Yellow
    Write-Host "  npm install -g wscat" -ForegroundColor Gray
}
Write-Host ""

# 测试 HTTP 长轮询（备选）
Write-Host "[步骤 4] 测试 HTTP 长轮询（WebSocket 备选）" -ForegroundColor Yellow
try {
    # 发送消息
    $body = @{
        message = "Hello from WebSocket test"
    } | ConvertTo-Json

    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/chat" -Method Post -ContentType "application/json" -Body $body
    Write-Host "  [PASS] HTTP 请求成功" -ForegroundColor Green
    Write-Host "  响应：$($response.content.Substring(0, [Math]::Min(50, $response.content.Length)))..." -ForegroundColor Gray
} catch {
    Write-Host "  [FAIL] HTTP 请求失败：$($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  WebSocket 测试完成" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "完整 WebSocket 测试需要使用浏览器或 wscat 工具" -ForegroundColor Yellow
