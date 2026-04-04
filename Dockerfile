# 构建阶段
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# 复制 pom.xml 并下载依赖
COPY pom.xml .
COPY tui-client/pom.xml tui-client/
RUN mvn dependency:go-offline -B || true

# 复制源代码
COPY src src/
COPY tui-client/src tui-client/src/

# 构建主项目（包括 TUI 客户端）
RUN mvn clean package -DskipTests -B

# 运行阶段
FROM eclipse-temurin-17-jre-alpine

WORKDIR /app

# 创建配置目录
RUN mkdir -p /root/.claude

# 复制构建产物
COPY --from=builder /app/target/minimal-k8s-agent-demo-*.jar app.jar
COPY --from=builder /app/tui-client/target/minimal-k8s-agent-tui-*-jar-with-dependencies.jar tui-client.jar

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]
