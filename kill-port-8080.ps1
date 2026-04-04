#!/usr/bin/env pwsh
# Kill process listening on port 8080
$ErrorActionPreference = "SilentlyContinue"

Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | ForEach-Object {
    Write-Host "Killing process $($_.OwningProcess) on port 8080"
    Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue
}

Write-Host "Done"
