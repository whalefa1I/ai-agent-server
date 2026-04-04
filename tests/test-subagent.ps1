#!/usr/bin/env pwsh
# 子 Agent 测试脚本 - 测试 WorkerAgentExecutor 和子 Agent 功能
# 使用方法：.\tests\test-subagent.ps1

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  子 Agent 测试 - WorkerAgentExecutor" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 测试 1: 检查应用状态
Write-Host "[测试 1] 检查应用运行状态" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/health" -Method Get
    Write-Host "  [PASS] 应用运行正常" -ForegroundColor Green
} catch {
    Write-Host "  [FAIL] 应用未运行" -ForegroundColor Red
    Write-Host "  提示：请先运行 'mvn spring-boot:run -Dspring-boot.run.profiles=local'" -ForegroundColor Yellow
    exit 1
}
Write-Host ""

# 测试 2: 获取当前会话 ID
Write-Host "[测试 2] 获取会话 ID" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/chat/session-id" -Method Get
    $sessionId = $response.sessionId
    Write-Host "  [PASS] 会话 ID: $sessionId" -ForegroundColor Green
} catch {
    Write-Host "  [FAIL] 获取会话 ID 失败" -ForegroundColor Red
    exit 1
}
Write-Host ""

# 测试 3: 测试 Bash Agent（通过 Task 工具）
Write-Host "[测试 3] Bash Agent 测试" -ForegroundColor Yellow
Write-Host "  描述：创建一个专门执行 bash 命令的子 Agent" -ForegroundColor Gray
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/chat" -Method Post -ContentType "application/json" -Body (ConvertTo-Json @{
        message = "请创建一个 Bash Agent 子任务，执行'echo Hello from Bash Agent'命令"
    })

    if ($response.content) {
        Write-Host "  [PASS] Bash Agent 请求已处理" -ForegroundColor Green
        Write-Host "  响应：$($response.content.Substring(0, [Math]::Min(100, $response.content.Length)))..." -ForegroundColor Gray
    }
} catch {
    Write-Host "  [FAIL] Bash Agent 测试失败：$($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# 测试 4: 测试 Explore Agent（通过 Task 工具）
Write-Host "[测试 4] Explore Agent 测试" -ForegroundColor Yellow
Write-Host "  描述：创建一个只读探索的子 Agent" -ForegroundColor Gray
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/chat" -Method Post -ContentType "application/json" -Body (ConvertTo-Json @{
        message = "请创建一个 Explore Agent 子任务，读取当前目录下的 README.md 文件内容"
    })

    if ($response.content) {
        Write-Host "  [PASS] Explore Agent 请求已处理" -ForegroundColor Green
        Write-Host "  响应：$($response.content.Substring(0, [Math]::Min(100, $response.content.Length)))..." -ForegroundColor Gray
    }
} catch {
    Write-Host "  [FAIL] Explore Agent 测试失败：$($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# 测试 5: 测试 Edit Agent（通过 Task 工具）
Write-Host "[测试 5] Edit Agent 测试" -ForegroundColor Yellow
Write-Host "  描述：创建一个负责文件编辑的子 Agent" -ForegroundColor Gray
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/chat" -Method Post -ContentType "application/json" -Body (ConvertTo-Json @{
        message = "请创建一个 Edit Agent 子任务，修改 test.txt 文件，添加一行注释"
    })

    if ($response.content) {
        Write-Host "  [PASS] Edit Agent 请求已处理" -ForegroundColor Green
        Write-Host "  响应：$($response.content.Substring(0, [Math]::Min(100, $response.content.Length)))..." -ForegroundColor Gray
    }
} catch {
    Write-Host "  [FAIL] Edit Agent 测试失败：$($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# 测试 6: 获取工具调用统计
Write-Host "[测试 6] 获取工具调用历史" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/observability/tool-calls?limit=10" -Method Get

    if ($response -and $response.Count -gt 0) {
        Write-Host "  [PASS] 获取到 $($response.Count) 条工具调用记录" -ForegroundColor Green
        foreach ($record in $response) {
            Write-Host "    - $($record.toolName) at $($record.timestamp)" -ForegroundColor Gray
        }
    } else {
        Write-Host "  [INFO] 暂无工具调用记录" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  [FAIL] 获取工具调用历史失败：$($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  子 Agent 测试完成" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "提示：子 Agent 功能需要配置 coordinator.enabled=true 来启用完整功能" -ForegroundColor Yellow
