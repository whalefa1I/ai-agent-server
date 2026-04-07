#!/bin/bash
# 一键启动脚本 - Unix Shell (Linux/Mac/WSL)
# 使用方法：./start-server.sh

cd "$(dirname "$0")"

# 设置 Java 21 路径 (根据你的实际路径修改)
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk}"
export PATH="$JAVA_HOME/bin:$PATH"

# 百炼平台配置
export DASHSCOPE_API_KEY="sk-sp-ab63f62c8df3494a8763982b1a741081"
export DASHSCOPE_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode/v1"
export DASHSCOPE_MODEL="qwen-plus"

echo "╔════════════════════════════════════════════════╗"
echo "║   ai-agent-server 服务端启动 (百炼平台)          ║"
echo "╚════════════════════════════════════════════════╝"
echo

# 检查是否已编译
if [ ! -f "target/minimal-k8s-agent-demo-0.1.0-SNAPSHOT.jar" ]; then
    echo "[1/2] 正在编译项目..."
    ./mvnw clean package -DskipTests
    if [ $? -ne 0 ]; then
        echo "[错误] 编译失败"
        exit 1
    fi
    echo "[OK] 编译完成"
else
    echo "[1/2] 使用已编译的 jar 文件"
fi

echo
echo "[2/2] 启动服务端..."
echo "API Key: $DASHSCOPE_API_KEY"
echo "Base URL: $DASHSCOPE_BASE_URL"
echo "Model: $DASHSCOPE_MODEL"
echo
echo "按 Ctrl+C 停止服务"
echo

exec "$JAVA_HOME/bin/java" \
    -Dfile.encoding=UTF-8 \
    -Ddashscope.api.key="$DASHSCOPE_API_KEY" \
    -Ddashscope.base.url="$DASHSCOPE_BASE_URL" \
    -Ddashscope.model="$DASHSCOPE_MODEL" \
    -jar target/minimal-k8s-agent-demo-0.1.0-SNAPSHOT.jar
