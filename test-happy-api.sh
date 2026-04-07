#!/bin/bash
# 测试脚本：模拟用户发送消息并观察前后端响应

SERVER_URL="http://localhost:8080"

# 获取 API Key（如果已保存则使用）
API_KEY=$(cat ~/.happy-api-key 2>/dev/null || echo "")
if [ -z "$API_KEY" ]; then
    echo "生成新的 API Key..."
    API_KEY_RESPONSE=$(curl -s -X POST "$SERVER_URL/api/auth/apikey/generate" \
        -H "Content-Type: application/json")
    API_KEY=$(echo $API_KEY_RESPONSE | jq -r '.apiKey')
    KEY_PREFIX=$(echo $API_KEY_RESPONSE | jq -r '.keyPrefix')
    echo "API Key: $KEY_PREFIX***"
    echo $API_KEY > ~/.happy-api-key
fi

# 获取或创建账户 ID 和会话 ID
ACCOUNT_ID="account-test-$(date +%s)"
SESSION_ID="session-test-$(date +%s)"

echo "=== 测试开始 ==="
echo "Server: $SERVER_URL"
echo "Account: $ACCOUNT_ID"
echo "Session: $SESSION_ID"
echo ""

# 测试 1: 发送简单消息
echo "=== 测试 1: 发送简单消息 ==="
TIMESTAMP=$(date +%s)
ARTIFACT_ID="test-msg-$TIMESTAMP"

HEADER=$(echo -n "{\"type\":\"message\",\"subtype\":\"user-message\",\"title\":\"User Message\",\"timestamp\":$TIMESTAMP}" | base64 -w 0)
BODY=$(echo -n "{\"type\":\"user-message\",\"content\":\"你好，请创建一个测试文件\",\"timestamp\":$TIMESTAMP}" | base64 -w 0)

curl -s -X POST "$SERVER_URL/api/v1/artifacts" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: $API_KEY" \
    -d "{
        \"id\": \"$ARTIFACT_ID\",
        \"accountId\": \"$ACCOUNT_ID\",
        \"sessionId\": \"$SESSION_ID\",
        \"header\": \"$HEADER\",
        \"body\": \"$BODY\",
        \"dataEncryptionKey\": \"\",
        \"headerVersion\": 1,
        \"bodyVersion\": 1,
        \"seq\": 0,
        \"createdAt\": $TIMESTAMP,
        \"updatedAt\": $TIMESTAMP
    }" | jq '.'

echo ""
echo "等待 AI 回复..."
sleep 3

# 测试 2: 获取 artifacts
echo "=== 测试 2: 获取 artifacts ==="
curl -s "$SERVER_URL/api/v1/artifacts?accountId=$ACCOUNT_ID" \
    -H "X-API-Key: $API_KEY" | jq '.[] | {id, headerVersion, bodyVersion, createdAt}'

echo ""
echo "=== 测试结束 ==="
