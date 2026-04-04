# Docker 部署指南

## 概述

本文档介绍如何使用 Docker 和 Kubernetes 部署 minimal-k8s-agent-demo。

## 快速开始

### 使用 Docker Compose（推荐）

#### 1. 准备环境变量

创建 `.env` 文件：

```bash
# .env
OPENAI_API_KEY=sk-your-api-key
OPENAI_BASE_URL=https://api.openai.com
OPENAI_MODEL=gpt-4o-mini

# 可选：启用 WebSocket 认证（生产环境）
DEMO_WS_AUTH_ENABLED=true
DEMO_WS_AUTH_PSK=your-secret-key-here

# 可选：启用 K8s 功能
DEMO_K8S_ENABLED=false
DEMO_K8S_NAMESPACE=default
```

#### 2. 构建并启动

```bash
# 构建镜像
docker-compose build

# 启动服务
docker-compose up -d

# 查看日志
docker-compose logs -f agent-server
```

#### 3. 访问服务

```bash
# 测试健康检查
curl http://localhost:8080/actuator/health

# 生成 WebSocket Token（如果启用认证）
curl -X POST http://localhost:8080/api/ws/token
```

#### 4. 使用 TUI 客户端

```bash
# 启动 TUI 客户端（需要交互式终端）
docker-compose --profile tui up tui-client
```

或者在本地运行 TUI 客户端连接服务器：

```bash
java -jar tui-client/target/minimal-k8s-agent-tui-jar-with-dependencies.jar \
    --server ws://localhost:8080/ws/agent
```

### 使用 Docker 直接运行

```bash
# 构建镜像
docker build -t minimal-k8s-agent-demo:latest .

# 运行容器
docker run -d \
  --name agent-server \
  -p 8080:8080 \
  -e OPENAI_API_KEY=sk-your-api-key \
  -e DEMO_WS_AUTH_ENABLED=false \
  -v claude-config:/root/.claude \
  minimal-k8s-agent-demo:latest
```

## Kubernetes 部署

### 1. 准备配置

```bash
# 设置环境变量
export OPENAI_API_KEY=sk-your-api-key
export NAMESPACE=agent-system
```

### 2. 应用配置

```bash
# 创建命名空间和部署
kubectl apply -f k8s/deployment.yaml

# 或者使用环境变量替换
envsubst < k8s/deployment.yaml | kubectl apply -f -
```

### 3. 检查状态

```bash
# 查看 Pod 状态
kubectl get pods -n agent-system

# 查看日志
kubectl logs -n agent-system deployment/agent-server

# 查看服务
kubectl get svc -n agent-system
```

### 4. 端口转发访问

```bash
# 端口转发
kubectl port-forward -n agent-system svc/agent-server 8080:8080

# 在另一个终端访问
curl http://localhost:8080/actuator/health
```

## 配置选项

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `OPENAI_API_KEY` | OpenAI API 密钥 | `changeme` |
| `OPENAI_BASE_URL` | OpenAI API 基础 URL | `https://api.openai.com` |
| `OPENAI_MODEL` | 模型名称 | `gpt-4o-mini` |
| `DEMO_K8S_ENABLED` | 启用 K8s 功能 | `false` |
| `DEMO_K8S_NAMESPACE` | K8s 命名空间 | `default` |
| `DEMO_COORDINATOR_MODE` | 启用多 Agent 模式 | `false` |
| `DEMO_WS_AUTH_ENABLED` | 启用 WebSocket 认证 | `false` |
| `DEMO_WS_AUTH_TOKEN_VALIDITY` | Token 有效期（秒） | `86400` |
| `DEMO_WS_AUTH_PSK` | 预共享密钥 | - |

### 持久化存储

服务端会持久化以下数据到 `/root/.claude`：

- 权限授权（`permission-grants.json`）
- 会话历史（`sessions/`）
- 日志文件

使用 Docker 时，建议挂载卷：

```bash
-v claude-config:/root/.claude
```

## 生产环境建议

### 1. 启用 WebSocket 认证

```yaml
environment:
  - DEMO_WS_AUTH_ENABLED=true
  - DEMO_WS_AUTH_PSK=<strong-secret-key>
```

客户端连接时需要提供 Token：

```bash
java -jar tui-client.jar --server ws://server:8080/ws/agent --token <your-token>
```

### 2. 使用 WSS 加密

通过 Ingress 或反向代理配置 SSL：

```yaml
# Ingress 示例
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: agent-ingress
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt
spec:
  tls:
    - hosts:
        - agent.example.com
      secretName: agent-tls
  rules:
    - host: agent.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: agent-server
                port:
                  number: 8080
```

### 3. 配置资源限制

```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "500m"
```

### 4. 配置健康检查

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 30
```

### 5. 日志收集

```bash
# 配置日志级别
logging:
  level:
    demo.k8s.agent: DEBUG
```

## 监控和告警

### Prometheus 指标

服务端暴露 Prometheus 格式的指标：

```bash
# 访问指标
curl http://localhost:8080/actuator/prometheus

# 主要指标
- http_server_requests_seconds    # HTTP 请求延迟
- agent_model_calls_total         # 模型调用次数
- agent_tool_calls_total          # 工具调用次数
- agent_tokens_total              # Token 使用量
```

### Grafana 仪表板

导入以下面板 ID：
- JVM 监控：4701
- Spring Boot 监控：10280

## 故障排查

### 容器无法启动

```bash
# 查看日志
docker logs agent-server

# 进入容器调试
docker exec -it agent-server sh
```

### 连接失败

```bash
# 检查网络
docker network ls
docker network inspect minimal-k8s-agent-demo_agent-network

# 测试端口
telnet localhost 8080
```

### 权限问题

```bash
# 检查卷权限
docker run --rm -v claude-config:/test ls -la /test
```

## 更新部署

```bash
# 重新构建并更新
docker-compose build
docker-compose up -d --force-recreate

# Kubernetes 滚动更新
kubectl rollout restart deployment/agent-server -n agent-system
```

## 清理

```bash
# Docker Compose
docker-compose down -v

# 删除镜像
docker rmi minimal-k8s-agent-demo:latest

# Kubernetes
kubectl delete -f k8s/deployment.yaml
```
