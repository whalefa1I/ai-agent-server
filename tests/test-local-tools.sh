#!/bin/bash
# 本地工具测试脚本 - 测试 6 个基本工具
# 使用方法：./tests/test-local-tools.sh

set -e

APP_URL="http://localhost:8080"
TEST_DIR="/tmp/minimal-k8s-agent-test-$$"

echo "========================================"
echo "  本地工具测试 - 6 个基本工具"
echo "========================================"
echo ""

# 创建测试目录
mkdir -p "$TEST_DIR"
echo -e "Hello, World!\nThis is a test file.\nLine 3." > "$TEST_DIR/test.txt"

echo "测试目录：$TEST_DIR"
echo ""

# 测试 1: glob 工具
echo "[测试 1] glob 工具 - 文件搜索"
RESPONSE=$(curl -s -X POST "$APP_URL/api/chat" \
    -H "Content-Type: application/json" \
    -d "{\"message\": \"请使用 glob 工具搜索 $TEST_DIR 目录下的所有.txt 文件\"}")
echo "  [INFO] 请求已发送"
echo "  响应：${RESPONSE:0:100}..."
echo ""

# 测试 2: file_read 工具
echo "[测试 2] file_read 工具 - 读取文件"
RESPONSE=$(curl -s -X POST "$APP_URL/api/chat" \
    -H "Content-Type: application/json" \
    -d "{\"message\": \"请读取文件内容：$TEST_DIR/test.txt\"}")
echo "  [INFO] 请求已发送"
echo "  响应：${RESPONSE:0:100}..."
echo ""

# 测试 3: file_write 工具
echo "[测试 3] file_write 工具 - 写入文件"
NEW_FILE="$TEST_DIR/new.txt"
RESPONSE=$(curl -s -X POST "$APP_URL/api/chat" \
    -H "Content-Type: application/json" \
    -d "{\"message\": \"请创建新文件 $NEW_FILE，内容为'This is a new file'\"}")
echo "  [INFO] 请求已发送"
echo "  响应：${RESPONSE:0:100}..."
echo ""

# 测试 4: file_edit 工具
echo "[测试 4] file_edit 工具 - 编辑文件"
RESPONSE=$(curl -s -X POST "$APP_URL/api/chat" \
    -H "Content-Type: application/json" \
    -d "{\"message\": \"请编辑文件 $TEST_DIR/test.txt，将'World'替换为'Testing'\"}")
echo "  [INFO] 请求已发送"
echo "  响应：${RESPONSE:0:100}..."
echo ""

# 测试 5: bash 工具
echo "[测试 5] bash 工具 - 执行 Shell 命令"
RESPONSE=$(curl -s -X POST "$APP_URL/api/chat" \
    -H "Content-Type: application/json" \
    -d "{\"message\": \"请执行命令：ls -la $TEST_DIR\"}")
echo "  [INFO] 请求已发送"
echo "  响应：${RESPONSE:0:100}..."
echo ""

# 测试 6: grep 工具
echo "[测试 6] grep 工具 - 文本搜索"
RESPONSE=$(curl -s -X POST "$APP_URL/api/chat" \
    -H "Content-Type: application/json" \
    -d "{\"message\": \"请在文件 $TEST_DIR/test.txt 中搜索包含'test'的行\"}")
echo "  [INFO] 请求已发送"
echo "  响应：${RESPONSE:0:100}..."
echo ""

# 清理
echo ""
echo "清理测试目录..."
rm -rf "$TEST_DIR"

echo ""
echo "========================================"
echo "  本地工具测试完成"
echo "========================================"
echo ""
echo "提示：请在应用日志中查看详细执行结果"
