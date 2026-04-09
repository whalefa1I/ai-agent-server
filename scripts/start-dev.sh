#!/usr/bin/env bash
# Linux / macOS: Spring Boot + tee to logs/（UTF-8）
set -euo pipefail
cd "$(dirname "$0")/.."
export LC_ALL="${LC_ALL:-C.UTF-8}"
export LANG="${LANG:-C.UTF-8}"
mkdir -p logs
LOG="logs/server-$(date +%Y%m%d-%H%M%S).log"
echo "========================================"
echo "  ai-agent-server（Spring Boot）"
echo "  日志: $LOG"
echo "========================================"
mvn spring-boot:run 2>&1 | tee "$LOG"
