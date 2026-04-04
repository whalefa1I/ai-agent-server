#!/bin/bash
# 环境验证脚本 - minimal-k8s-agent-demo
# 使用方法：./verify-environment.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "╔════════════════════════════════════════════════╗"
echo "║   minimal-k8s-agent-demo 环境验证              ║"
echo "╚════════════════════════════════════════════════╝"
echo.

# 设置路径
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot"
export MAVEN_HOME="/c/ProgramData/chocolatey/lib/maven/apache-maven-3.9.14"
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"

echo "[1/6] 检查 Java 版本..."
java -version
echo.

echo "[2/6] 检查 Maven 版本..."
mvn --version
echo.

echo "[3/6] 检查 OPENAI_API_KEY..."
if [ -z "$OPENAI_API_KEY" ]; then
    echo "[警告] OPENAI_API_KEY 未设置"
    echo "   请设置：export OPENAI_API_KEY=your-api-key"
    echo "   百炼平台：export OPENAI_BASE_URL=https://coding.dashscope.aliyuncs.com/v1"
else
    echo "[OK] API Key 已设置"
    echo "   OPENAI_BASE_URL=$OPENAI_BASE_URL"
fi
echo.

echo "[4/6] 检查 spring-ai-agent-utils..."
if [ -f "../spring-ai-agent-utils/pom.xml" ]; then
    echo "[OK] spring-ai-agent-utils 存在"
else
    echo "[错误] 未找到 spring-ai-agent-utils"
    exit 1
fi
echo.

echo "[5/6] 检查 Maven 仓库位置..."
if [ -d "D:/.m2/repository" ]; then
    echo "[OK] Maven 仓库位于 D:/.m2/repository"
else
    echo "[提示] D:/.m2/repository 不存在，将使用默认位置"
fi
echo.

echo "[6/6] 检查项目依赖..."
cd ../spring-ai-agent-utils
echo "正在安装 spring-ai-agent-utils..."
mvn install -DskipTests
if [ $? -ne 0 ]; then
    echo "[错误] spring-ai-agent-utils 安装失败"
    cd ../minimal-k8s-agent-demo
    exit 1
fi
echo "[OK] spring-ai-agent-utils 安装成功"
cd ../minimal-k8s-agent-demo
echo.

echo "╔════════════════════════════════════════════════╗"
echo "║   环境验证完成！                                ║"
echo "╚════════════════════════════════════════════════╝"
echo.
echo "下一步:"
echo "  1. 编译项目：mvn clean compile"
echo "  2. 运行测试：mvn test"
echo "  3. 启动服务：mvn spring-boot:run"
echo.
