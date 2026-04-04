# 构建阶段
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

# 复制 pom.xml
COPY pom.xml .

# 下载依赖（忽略失败）
RUN mvn dependency:go-offline -B || true

# 复制源代码
COPY src src/

# 构建主项目
RUN mvn clean package -DskipTests -B -e

# 运行阶段 - 使用 Debian 基础镜像（包含 libgcc，避免 QUIC 原生库加载失败）
FROM eclipse-temurin:21-jre

WORKDIR /app

# 复制构建产物
COPY --from=builder /app/target/minimal-k8s-agent-demo-*.jar app.jar

# 暴露端口
EXPOSE 8080

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]
