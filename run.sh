#!/bin/bash
# 快速启动脚本 - 同时启动服务端和 TUI 客户端
# 使用方法：./run.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"
TUI_DIR="$SCRIPT_DIR/tui-client"

echo "╔════════════════════════════════════════════════╗"
echo "║   minimal-k8s-agent-demo 快速启动               ║"
echo "╚════════════════════════════════════════════════╝"
echo ""

# 检查环境变量
if [ -z "$OPENAI_API_KEY" ]; then
    echo "⚠️  警告：OPENAI_API_KEY 未设置"
    echo "   请设置环境变量：export OPENAI_API_KEY=your-api-key"
    echo ""
fi

# 检查 spring-ai-agent-utils 是否已安装
echo "检查依赖..."
if [ ! -d "../spring-ai-agent-utils" ]; then
    echo "⚠️  警告：未找到 spring-ai-agent-utils 项目"
    echo "   请先运行：cd ../spring-ai-agent-utils && mvn install -DskipTests"
    echo ""
fi

echo ""
echo "步骤 1/3: 构建 TUI 客户端..."
cd "$TUI_DIR"
mvn package -DskipTests -q
echo "✓ TUI 客户端构建完成"

echo ""
echo "步骤 2/3: 启动服务端..."
cd "$PROJECT_DIR"

# 后台启动服务端
mvn spring-boot:run -Dspring-boot.run.fork=true > /tmp/agent-server.log 2>&1 &
SERVER_PID=$!
echo "✓ 服务端已启动 (PID: $SERVER_PID)"

# 等待服务端启动（最多 30 秒）
echo "等待服务端启动..."
for i in {1..30}; do
    if curl -s http://localhost:8080/api/health > /dev/null 2>&1; then
        echo "✓ 服务端已就绪"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "✗ 服务端启动超时"
        echo "查看日志：cat /tmp/agent-server.log"
        kill $SERVER_PID 2>/dev/null || true
        exit 1
    fi
    sleep 1
done

echo ""
echo "步骤 3/3: 启动 TUI 客户端..."
echo ""
echo "正在连接服务器..."

# 运行 TUI 客户端
java -jar target/minimal-k8s-agent-tui-jar-with-dependencies.jar \
    --server ws://localhost:8080/ws/agent

# 清理（TUI 退出后）
echo ""
echo "正在关闭服务端..."
kill $SERVER_PID 2>/dev/null || true
echo "✓ 已关闭服务端"
