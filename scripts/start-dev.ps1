# Spring Boot：控制台 + logs/server-*.log
# 需要: JDK 21+、Maven、application.yml / 环境变量

. "$PSScriptRoot\_init-console-utf8.ps1"

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$logDir = Join-Path $projectRoot "logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$logFile = Join-Path $logDir ("server-{0}.log" -f (Get-Date -Format "yyyyMMdd-HHmmss"))

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  ai-agent-server（Spring Boot）" -ForegroundColor Cyan
Write-Host "  日志文件: $logFile" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

mvn spring-boot:run 2>&1 | Tee-Object -FilePath $logFile
