# 构建阶段
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

# 复制 pom.xml
COPY pom.xml .

# 下载依赖（忽略失败）
RUN mvn dependency:go-offline -B || true

# 复制源代码
COPY src src/

# 技能资源（与 SkillService 默认 ./skills 对齐，供运行阶段 COPY）
COPY skills skills/

# 构建主项目
RUN mvn clean package -DskipTests -B -e

# 运行阶段 - 使用 Debian 基础镜像（包含 libgcc，避免 QUIC 原生库加载失败）
FROM eclipse-temurin:21-jre

WORKDIR /app

# 开发/回归场景需要本地 bash 可直接执行 python 脚本
RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 python3-pip \
    && ln -sf /usr/bin/python3 /usr/bin/python \
    && rm -rf /var/lib/apt/lists/*

# 技能目录（SkillService 默认扫描 /app/skills）；构建阶段已含源码中的 skills/
COPY --from=builder /app/skills ./skills

# 复制构建产物
COPY --from=builder /app/target/minimal-k8s-agent-demo-*.jar app.jar

# 暴露端口
EXPOSE 8080

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]
