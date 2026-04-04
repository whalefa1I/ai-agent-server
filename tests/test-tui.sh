#!/bin/bash
# TUI 客户端测试脚本 - 测试 TUI 使用
# 使用方法：./tests/test-tui.sh

set -e

APP_URL="http://localhost:8080"
TUI_JAR="../tui-client/target/minimal-k8s-agent-tui-jar-with-dependencies.jar"

echo "========================================"
echo "  TUI 客户端测试 - 终端用户界面"
echo "========================================"
echo ""

# 检查服务端状态
echo "[前置检查] 服务端状态"
if curl -s -f "$APP_URL/api/health" > /dev/null 2>&1; then
    echo "  [PASS] 服务端运行正常"
else
    echo "  [FAIL] 服务端未运行"
    echo "  提示：请先运行 'mvn spring-boot:run -Dspring-boot.run.profiles=local'"
    exit 1
fi
echo ""

# 检查 TUI 客户端构建
echo "[前置检查] TUI 客户端构建状态"
if [ -f "$TUI_JAR" ]; then
    echo "  [PASS] TUI 客户端已构建"
    echo "  路径：$TUI_JAR"
else
    echo "  [FAIL] TUI 客户端未构建"
    echo "  提示：请先运行 'cd ../tui-client && mvn package -DskipTests'"
    exit 1
fi
echo ""

# 获取 WebSocket Token
echo "[步骤 1] 获取 WebSocket Token"
TOKEN_RESPONSE=$(curl -s -X POST "$APP_URL/api/ws/token" \
    -H "Content-Type: application/json" \
    -d '{"sessionId": "tui-test-session"}' 2>/dev/null || echo "")

if echo "$TOKEN_RESPONSE" | grep -q '"token"'; then
    TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
    echo "  [PASS] Token: $TOKEN"
else
    echo "  [INFO] 使用默认 token"
    TOKEN="test-token"
fi
echo ""

# WebSocket URL
WS_URL="ws://localhost:8080/ws/agent/$TOKEN"

# 启动 TUI 客户端
echo "[步骤 2] 启动 TUI 客户端"
echo "  启动命令："
echo "  java -jar \"$TUI_JAR\" --server $WS_URL"
echo ""

# 可选：自动启动
echo "是否现在启动 TUI 客户端？(y/n)"
read -r answer

if [ "$answer" = "y" ] || [ "$answer" = "Y" ]; then
    echo "正在启动 TUI 客户端..."
    java -jar "$TUI_JAR" --server "$WS_URL" &
    echo "  [PASS] TUI 客户端已启动 (PID: $!)"
else
    echo "  [SKIP] TUI 客户端未启动"
fi
echo ""

# TUI 功能测试说明
echo "========================================"
echo "  TUI 功能测试清单"
echo "========================================"
echo ""
echo "请在 TUI 中测试以下功能："
echo ""
echo "  [ ] 1. 发送简单消息（如：'你好'）"
echo "  [ ] 2. 请求文件读取（如：'读取 README.md'）"
echo "  [ ] 3. 请求文件搜索（如：'搜索 *.java 文件'）"
echo "  [ ] 4. 请求执行命令（如：'运行 ls'）"
echo "  [ ] 5. 查看工具调用历史"
echo "  [ ] 6. 查看会话统计"
echo "  [ ] 7. 测试子 Agent 委派（如：'创建一个子任务来...'）"
echo "  [ ] 8. 测试错误处理（发送无效请求）"
echo ""

echo "========================================"
echo "  TUI 测试脚本完成"
echo "========================================"
