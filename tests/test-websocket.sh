#!/bin/bash
# WebSocket 测试脚本 - 测试 WebSocket 连接和消息
# 使用方法：./tests/test-websocket.sh

set -e

APP_URL="http://localhost:8080"
WS_URL="ws://localhost:8080/ws/agent/test-token"

echo "========================================"
echo "  WebSocket 测试 - 实时消息通信"
echo "========================================"
echo ""

# 检查应用状态
echo "[前置检查] 应用状态"
if curl -s -f "$APP_URL/api/health" > /dev/null 2>&1; then
    echo "  [PASS] 应用运行正常"
else
    echo "  [FAIL] 应用未运行"
    echo "  提示：请先运行 'mvn spring-boot:run -Dspring-boot.run.profiles=local'"
    exit 1
fi
echo ""

# 获取 Token（如果需要认证）
echo "[步骤 1] 获取 WebSocket Token"
TOKEN_RESPONSE=$(curl -s -X POST "$APP_URL/api/ws/token" \
    -H "Content-Type: application/json" \
    -d '{"sessionId": "test-session"}' 2>/dev/null || echo "")

if echo "$TOKEN_RESPONSE" | grep -q '"token"'; then
    TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
    echo "  [PASS] 获取 Token: $TOKEN"
    WS_URL="ws://localhost:8080/ws/agent/$TOKEN"
else
    echo "  [INFO] Token 端点可能未启用（认证关闭）"
    TOKEN="test-token"
fi
echo ""

# WebSocket 连接信息
echo "[步骤 2] WebSocket 连接信息"
echo "  WebSocket URL: $WS_URL"
echo ""
echo "  手动测试方法：" -ForegroundColor Yellow
echo "  1. 使用 wscat (需要安装：npm install -g wscat)"
echo "     wscat -c \"$WS_URL\""
echo ""
echo "  2. 使用 Python websockets 库"
echo "     python3 -c \"import websockets; asyncio.run(websockets.connect('$WS_URL'))\""
echo ""

# 检查 wscat
echo "[步骤 3] 检查 wscat"
if command -v wscat &> /dev/null; then
    echo "  [PASS] wscat 已安装"
    echo "  运行：wscat -c \"$WS_URL\" -x '{\"type\":\"user\",\"content\":\"Hello\"}'"
else
    echo "  [INFO] wscat 未安装"
    echo "  安装：npm install -g wscat"
fi
echo ""

# HTTP 长轮询测试
echo "[步骤 4] HTTP 长轮询测试（WebSocket 备选）"
RESPONSE=$(curl -s -X POST "$APP_URL/api/chat" \
    -H "Content-Type: application/json" \
    -d '{"message": "Hello from WebSocket test"}')

if echo "$RESPONSE" | grep -q '"content"'; then
    echo "  [PASS] HTTP 请求成功"
    CONTENT=$(echo "$RESPONSE" | grep -o '"content":"[^"]*"' | head -1 | cut -d'"' -f4)
    echo "  响应：${CONTENT:0:50}..."
else
    echo "  [FAIL] HTTP 请求失败"
fi
echo ""

echo "========================================"
echo "  WebSocket 测试完成"
echo "========================================"
echo ""
echo "完整 WebSocket 测试需要使用浏览器或 wscat 工具"
