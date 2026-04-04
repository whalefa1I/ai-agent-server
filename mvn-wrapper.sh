#!/bin/bash
# 使用 Java 直接运行 Maven
# 从 Maven 官方仓库下载 maven-launcher 并运行

JAVA_VER=$(java -version 2>&1 | head -1)
echo "Using Java: $JAVA_VER"

# 尝试使用 sdkman 或其他工具安装的 Maven
if [ -f "$HOME/.sdkman/candidates/maven/current/bin/mvn" ]; then
    exec "$HOME/.sdkman/candidates/maven/current/bin/mvn" "$@"
fi

# 尝试在常见位置查找 Maven
for MVN_HOME in /opt/maven /usr/local/opt/maven /usr/share/maven "C:/Program Files/apache-maven-3.*/bin" "D:/dev-tools/apache-maven-*/bin" "D:/Program Files/apache-maven-*/bin"; do
    for mvn in "$MVN_HOME"/mvn "$MVN_HOME"/mvn.cmd "$MVN_HOME"/mvn.bat; do
        if [ -f "$mvn" ]; then
            exec "$mvn" "$@"
        fi
    done
done

echo "Maven not found. Please install Maven or set MVN_HOME environment variable."
exit 1
