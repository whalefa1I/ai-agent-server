#!/bin/bash
# 子 Agent 测试脚本 - 测试 WorkerAgentExecutor 和子 Agent 功能
# 使用方法：./tests/test-subagent.sh

set -e

APP_URL="http://localhost:8080"

echo "========================================"
echo "  子 Agent 测试 - WorkerAgentExecutor"
echo "========================================"
echo ""

# 测试 1: 检查应用状态
echo "[测试 1] 检查应用运行状态"
if curl -s -f "$APP_URL/api/health" > /dev/null 2>&1; then
    echo "  [PASS] 应用运行正常"
else
    echo "  [FAIL] 应用未运行"
    echo "  提示：请先运行 'mvn spring-boot:run -Dspring-boot.run.profiles=local'"
    exit 1
fi
echo ""

# 测试 2: 获取会话 ID
echo "[测试 2] 获取会话 ID"
RESPONSE=$(curl -s "$APP_URL/api/chat/session-id")
if echo "$RESPONSE" | grep -q '"sessionId"'; then
    SESSION_ID=$(echo "$RESPONSE" | grep -o '"sessionId":"[^"]*"' | cut -d'"' -f4)
    echo "  [PASS] 会话 ID: $SESSION_ID"
else
    echo "  [FAIL] 获取会话 ID 失败"
    exit 1
fi
echo ""

# 测试 3: Bash Agent
echo "[测试 3] Bash Agent 测试"
RESPONSE=$(curl -s -X POST "$APP_URL/api/chat" \
    -H "Content-Type: application/json" \
    -d '{"message": "请创建一个 Bash Agent 子任务，执行 echo Hello from Bash Agent 命令"}')
if echo "$RESPONSE" | grep -q '"content"'; then
    echo "  [PASS] Bash Agent 请求已处理"
    CONTENT=$(echo "$RESPONSE" | grep -o '"content":"[^"]*"' | head -1 | cut -d'"' -f4)
    echo "  响应：${CONTENT:0:100}..."
else
    echo "  [FAIL] Bash Agent 测试失败"
fi
echo ""

# 测试 4: Explore Agent
echo "[测试 4] Explore Agent 测试"
RESPONSE=$(curl -s -X POST "$APP_URL/api/chat" \
    -H "Content-Type: application/json" \
    -d '{"message": "请创建一个 Explore Agent 子任务，读取当前目录下的 README.md 文件内容"}')
if echo "$RESPONSE" | grep -q '"content"'; then
    echo "  [PASS] Explore Agent 请求已处理"
    CONTENT=$(echo "$RESPONSE" | grep -o '"content":"[^"]*"' | head -1 | cut -d'"' -f4)
    echo "  响应：${CONTENT:0:100}..."
else
    echo "  [FAIL] Explore Agent 测试失败"
fi
echo ""

# 测试 5: Edit Agent
echo "[测试 5] Edit Agent 测试"
RESPONSE=$(curl -s -X POST "$APP_URL/api/chat" \
    -H "Content-Type: application/json" \
    -d '{"message": "请创建一个 Edit Agent 子任务，修改 test.txt 文件，添加一行注释"}')
if echo "$RESPONSE" | grep -q '"content"'; then
    echo "  [PASS] Edit Agent 请求已处理"
    CONTENT=$(echo "$RESPONSE" | grep -o '"content":"[^"]*"' | head -1 | cut -d'"' -f4)
    echo "  响应：${CONTENT:0:100}..."
else
    echo "  [FAIL] Edit Agent 测试失败"
fi
echo ""

# 测试 6: 工具调用历史
echo "[测试 6] 获取工具调用历史"
RESPONSE=$(curl -s "$APP_URL/api/observability/tool-calls?limit=10")
if echo "$RESPONSE" | grep -q '\['; then
    COUNT=$(echo "$RESPONSE" | grep -o '\[.*\]' | wc -c)
    echo "  [PASS] 获取到工具调用记录"
    echo "  $RESPONSE"
else
    echo "  [INFO] 暂无工具调用记录"
fi
echo ""

echo "========================================"
echo "  子 Agent 测试完成"
echo "========================================"
echo ""
echo "提示：子 Agent 功能需要配置 coordinator.enabled=true 来启用完整功能"
