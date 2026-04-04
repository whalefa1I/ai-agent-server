#!/usr/bin/env pwsh
# 本地工具测试脚本 - 测试 6 个基本工具
# 使用方法：.\tests\test-local-tools.ps1

$ErrorActionPreference = "Stop"
$TestDir = Join-Path $PSScriptRoot "temp-test"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  本地工具测试 - 6 个基本工具" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 创建测试目录
if (Test-Path $TestDir) {
    Remove-Item $TestDir -Recurse -Force
}
New-Item -ItemType Directory -Path $TestDir | Out-Null

# 创建测试文件
$testFile = Join-Path $TestDir "test.txt"
"Hello, World!`nThis is a test file.`nLine 3." | Set-Content -Path $testFile -Encoding UTF8

Write-Host "测试目录：$TestDir" -ForegroundColor Gray
Write-Host ""

# 测试 1: glob 工具
Write-Host "[测试 1] glob 工具 - 文件搜索" -ForegroundColor Yellow
try {
    $body = @{
        pattern = "*.txt"
        path = $TestDir
    } | ConvertTo-Json

    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/chat" -Method Post -ContentType "application/json" -Body (ConvertTo-Json @{
        message = "请使用 glob 工具搜索 $TestDir 目录下的所有.txt 文件"
    })

    Write-Host "  [INFO] 请求已发送，请检查应用日志" -ForegroundColor Gray
} catch {
    Write-Host "  [SKIP] 需要应用运行中" -ForegroundColor Yellow
}
Write-Host ""

# 测试 2: file_read 工具
Write-Host "[测试 2] file_read 工具 - 读取文件" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/chat" -Method Post -ContentType "application/json" -Body (ConvertTo-Json @{
        message = "请读取文件内容：$testFile"
    })
    Write-Host "  [INFO] 请求已发送，请检查应用日志" -ForegroundColor Gray
} catch {
    Write-Host "  [SKIP] 需要应用运行中" -ForegroundColor Yellow
}
Write-Host ""

# 测试 3: file_write 工具
Write-Host "[测试 3] file_write 工具 - 写入文件" -ForegroundColor Yellow
$newFile = Join-Path $TestDir "new.txt"
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/chat" -Method Post -ContentType "application/json" -Body (ConvertTo-Json @{
        message = "请创建新文件 $newFile，内容为'This is a new file created by file_write tool'"
    })
    Write-Host "  [INFO] 请求已发送，请检查应用日志" -ForegroundColor Gray
} catch {
    Write-Host "  [SKIP] 需要应用运行中" -ForegroundColor Yellow
}
Write-Host ""

# 测试 4: file_edit 工具
Write-Host "[测试 4] file_edit 工具 - 编辑文件" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/chat" -Method Post -ContentType "application/json" -Body (ConvertTo-Json @{
        message = "请编辑文件 $testFile，将'World'替换为'Testing'"
    })
    Write-Host "  [INFO] 请求已发送，请检查应用日志" -ForegroundColor Gray
} catch {
    Write-Host "  [SKIP] 需要应用运行中" -ForegroundColor Yellow
}
Write-Host ""

# 测试 5: bash 工具
Write-Host "[测试 5] bash 工具 - 执行 Shell 命令" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/chat" -Method Post -ContentType "application/json" -Body (ConvertTo-Json @{
        message = "请执行命令：dir $TestDir"
    })
    Write-Host "  [INFO] 请求已发送，请检查应用日志" -ForegroundColor Gray
} catch {
    Write-Host "  [SKIP] 需要应用运行中" -ForegroundColor Yellow
}
Write-Host ""

# 测试 6: grep 工具
Write-Host "[测试 6] grep 工具 - 文本搜索" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/chat" -Method Post -ContentType "application/json" -Body (ConvertTo-Json @{
        message = "请在文件 $testFile 中搜索包含'test'的行"
    })
    Write-Host "  [INFO] 请求已发送，请检查应用日志" -ForegroundColor Gray
} catch {
    Write-Host "  [SKIP] 需要应用运行中" -ForegroundColor Yellow
}
Write-Host ""

# 清理
Write-Host ""
Write-Host "清理测试目录..." -ForegroundColor Gray
Remove-Item $TestDir -Recurse -Force

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  本地工具测试完成" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "提示：请在应用日志中查看详细执行结果" -ForegroundColor Yellow
