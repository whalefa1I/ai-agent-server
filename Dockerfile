# 构建阶段
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

# 复制 pom.xml 并下载依赖
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# 复制源代码
COPY src src/

# 构建主项目
RUN mvn clean package -DskipTests -B

# 运行阶段
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 复制构建产物
COPY --from=builder /app/target/*.jar app.jar

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]
