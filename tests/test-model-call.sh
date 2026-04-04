#!/bin/bash
# 模型调用测试脚本 - 测试 Bailian/Qwen 平台集成
# 使用方法：./tests/test-model-call.sh

set -e

BASE_URL="https://coding.dashscope.aliyuncs.com/v1"
API_KEY="sk-sp-ab63f62c8df3494a8763982b1a741081"
MODEL="qwen-max"
APP_URL="http://localhost:8080"

echo "========================================"
echo "  模型调用测试 - Bailian/Qwen 平台"
echo "========================================"
echo ""

# 测试 1: 直接 API 调用
echo "[测试 1] 直接 API 调用测试"
RESPONSE=$(curl -s -X POST "$BASE_URL/chat/completions" \
    -H "Authorization: Bearer $API_KEY" \
    -H "Content-Type: application/json" \
    -d "{
        \"model\": \"$MODEL\",
        \"messages\": [{\"role\": \"user\", \"content\": \"请用一句话介绍你自己\"}],
        \"max_tokens\": 100
    }")

if echo "$RESPONSE" | grep -q '"choices"'; then
    echo "  [PASS] API 调用成功"
    CONTENT=$(echo "$RESPONSE" | grep -o '"content":"[^"]*"' | head -1 | cut -d'"' -f4)
    echo "  回复：$CONTENT"
else
    echo "  [FAIL] API 返回异常"
    echo "  响应：$RESPONSE"
fi
echo ""

# 测试 2: Spring Boot 应用健康检查
echo "[测试 2] Spring Boot 应用健康检查"
if curl -s -f "$APP_URL/api/health" > /dev/null 2>&1; then
    RESPONSE=$(curl -s "$APP_URL/api/health")
    echo "  [PASS] 应用健康运行"
    echo "  状态：$RESPONSE"
else
    echo "  [FAIL] 应用未运行或无法访问"
    echo "  提示：请先运行 'mvn spring-boot:run -Dspring-boot.run.profiles=local'"
fi
echo ""

# 测试 3: 会话 ID 获取
echo "[测试 3] 会话管理测试"
RESPONSE=$(curl -s "$APP_URL/api/chat/session-id")
if echo "$RESPONSE" | grep -q '"sessionId"'; then
    echo "  [PASS] 获取会话 ID 成功"
    echo "  $RESPONSE"
else
    echo "  [FAIL] 获取会话 ID 失败"
fi
echo ""

# 测试 4: 简单对话测试
echo "[测试 4] 简单对话测试"
RESPONSE=$(curl -s -X POST "$APP_URL/api/chat" \
    -H "Content-Type: application/json" \
    -d '{"message": "你好，请简单介绍一下自己"}')

if echo "$RESPONSE" | grep -q '"content"'; then
    echo "  [PASS] 对话成功"
    CONTENT=$(echo "$RESPONSE" | grep -o '"content":"[^"]*"' | head -1 | cut -d'"' -f4)
    echo "  回复：${CONTENT:0:100}..."
else
    echo "  [WARN] 回复内容为空"
    echo "  响应：$RESPONSE"
fi
echo ""

# 测试 5: 模型调用统计
echo "[测试 5] 获取模型调用统计"
RESPONSE=$(curl -s "$APP_URL/api/observability/stats")
if echo "$RESPONSE" | grep -q '"sessionId"'; then
    echo "  [PASS] 获取统计成功"
    echo "  $RESPONSE"
else
    echo "  [FAIL] 获取统计失败"
fi
echo ""

echo "========================================"
echo "  测试完成"
echo "========================================"
