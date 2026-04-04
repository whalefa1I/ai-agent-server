#!/usr/bin/env pwsh
# TUI 客户端测试脚本 - 测试 TUI 使用
# 使用方法：.\tests\test-tui.ps1

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  TUI 客户端测试 - 终端用户界面" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查服务端状态
Write-Host "[前置检查] 服务端状态" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/health" -Method Get
    Write-Host "  [PASS] 服务端运行正常" -ForegroundColor Green
} catch {
    Write-Host "  [FAIL] 服务端未运行" -ForegroundColor Red
    Write-Host "  提示：请先运行 'mvn spring-boot:run -Dspring-boot.run.profiles=local'" -ForegroundColor Yellow
    exit 1
}
Write-Host ""

# 检查 TUI 客户端构建
Write-Host "[前置检查] TUI 客户端构建状态" -ForegroundColor Yellow
$tuiJar = Join-Path $PSScriptRoot "..\tui-client\target\minimal-k8s-agent-tui-jar-with-dependencies.jar"
if (Test-Path $tuiJar) {
    Write-Host "  [PASS] TUI 客户端已构建" -ForegroundColor Green
    Write-Host "  路径：$tuiJar" -ForegroundColor Gray
} else {
    Write-Host "  [FAIL] TUI 客户端未构建" -ForegroundColor Red
    Write-Host "  提示：请先运行 'cd ..\tui-client && mvn package -DskipTests'" -ForegroundColor Yellow
    exit 1
}
Write-Host ""

# 获取 WebSocket Token
Write-Host "[步骤 1] 获取 WebSocket Token" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/ws/token" -Method Post -ContentType "application/json" -Body (ConvertTo-Json @{
        sessionId = "tui-test-session"
    })
    $token = $response.token
    Write-Host "  [PASS] Token：$token" -ForegroundColor Green
} catch {
    Write-Host "  [INFO] 使用默认 token" -ForegroundColor Yellow
    $token = "test-token"
}
Write-Host ""

# 启动 TUI 客户端
Write-Host "[步骤 2] 启动 TUI 客户端" -ForegroundColor Yellow
Write-Host "  提示：TUI 客户端将在新窗口启动" -ForegroundColor Gray
Write-Host ""

# 提供启动命令
$wsUrl = "ws://localhost:8080/ws/agent/$token"
Write-Host "  启动命令：" -ForegroundColor Cyan
Write-Host "  java -jar `"$tuiJar`" --server $wsUrl" -ForegroundColor White
Write-Host ""

# 可选：自动启动
Write-Host "是否现在启动 TUI 客户端？(Y/N)" -ForegroundColor Yellow
$answer = Read-Host

if ($answer -eq 'Y' -or $answer -eq 'y') {
    Write-Host "正在启动 TUI 客户端..." -ForegroundColor Gray
    Start-Process java -ArgumentList "-jar", "`"$tuiJar`"", "--server", $wsUrl
    Write-Host "  [PASS] TUI 客户端已启动" -ForegroundColor Green
} else {
    Write-Host "  [SKIP] TUI 客户端未启动" -ForegroundColor Yellow
}
Write-Host ""

# TUI 功能测试说明
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  TUI 功能测试清单" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "请在 TUI 中测试以下功能：" -ForegroundColor Yellow
Write-Host ""
Write-Host "  [ ] 1. 发送简单消息（如：'你好'）" -ForegroundColor White
Write-Host "  [ ] 2. 请求文件读取（如：'读取 README.md'）" -ForegroundColor White
Write-Host "  [ ] 3. 请求文件搜索（如：'搜索 *.java 文件'）" -ForegroundColor White
Write-Host "  [ ] 4. 请求执行命令（如：'运行 dir'）" -ForegroundColor White
Write-Host "  [ ] 5. 查看工具调用历史" -ForegroundColor White
Write-Host "  [ ] 6. 查看会话统计" -ForegroundColor White
Write-Host "  [ ] 7. 测试子 Agent 委派（如：'创建一个子任务来...'）" -ForegroundColor White
Write-Host "  [ ] 8. 测试错误处理（发送无效请求）" -ForegroundColor White
Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  TUI 测试脚本完成" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
