# Python 远程工具执行服务 - 部署指南

## 目录结构

```
remote-tools/
├── main.py              # FastAPI 应用主程序
├── pyproject.toml       # Python 项目配置 (uv)
├── requirements.txt     # Python 依赖 (兼容)
├── Dockerfile           # Railway 部署配置
├── railway.toml         # Railway 配置
└── README.md            # 使用说明
```

## 部署到 Railway

### 步骤 1: 创建 GitHub 仓库

1. 在 GitHub 创建新仓库，例如 `agent-remote-tools`
2. 将 `remote-tools/` 目录的内容推送到该仓库

```bash
cd remote-tools
git init
git add .
git commit -m "Initial commit: Python remote tool executor MVP"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/agent-remote-tools.git
git push -u origin main
```

### 步骤 2: 在 Railway 创建项目

1. 访问 [Railway](https://railway.app)
2. 点击 "New Project"
3. 选择 "Deploy from GitHub repo"
4. 选择 `agent-remote-tools` 仓库
5. Railway 会自动检测 Dockerfile 并部署

### 步骤 3: 配置环境变量

在 Railway 项目设置中添加以下环境变量：

| 变量名 | 值 | 说明 |
|--------|-----|------|
| `WORK_DIR` | `/tmp/agent-tools` | 工作目录（默认值） |
| `TTL_SECONDS` | `86400` | 文件 TTL，默认 24 小时 |
| `PORT` | `8000` | 服务端口 |

### 步骤 4: 获取服务 URL

部署完成后，Railway 会分配一个 URL，例如：
```
https://agent-remote-tools-production.up.railway.app
```

## Spring Boot 配置

在 Spring Boot 的 `application.yml` 或环境变量中添加：

```yaml
remote:
  tools:
    enabled: true
    base-url: https://agent-remote-tools-production.up.railway.app
    api-key: your-api-key-here  # 可选
    timeout-seconds: 60
```

或使用环境变量：
```bash
REMOTE_TOOLS_ENABLED=true
REMOTE_TOOLS_BASE_URL=https://agent-remote-tools-production.up.railway.app
REMOTE_TOOLS_API_KEY=your-api-key-here
REMOTE_TOOLS_TIMEOUT=60
```

## 测试

### 测试 Python 服务

```bash
# 健康检查
curl https://YOUR_RAILWAY_URL.up.railway.app/health

# 执行 Shell 命令
curl -X POST https://YOUR_RAILWAY_URL.up.railway.app/tools/execute \
  -H "X-User-ID: test-user" \
  -H "X-Session-ID: test-session" \
  -H "Content-Type: application/json" \
  -d '{
    "tool_name": "shell",
    "input": {"command": "echo hello world"}
  }'

# 写文件
curl -X POST https://YOUR_RAILWAY_URL.up.railway.app/tools/execute \
  -H "X-User-ID: test-user" \
  -H "Content-Type: application/json" \
  -d '{
    "tool_name": "file_write",
    "input": {
      "path": "test.txt",
      "content": "Hello from Railway!"
    }
  }'

# 读文件
curl -X POST https://YOUR_RAILWAY_URL.up.railway.app/tools/execute \
  -H "X-User-ID: test-user" \
  -H "Content-Type: application/json" \
  -d '{
    "tool_name": "file_read",
    "input": {"path": "test.txt"}
  }'
```

### 测试 Spring Boot 集成

启动 Spring Boot 应用后，通过 Agent 执行工具调用，工具会自动路由到远程服务。

## 本地开发

### 运行 Python 服务

```bash
cd remote-tools
uv sync
uv run python main.py
```

服务会在 `http://localhost:8000` 启动。

### 本地测试 Spring Boot

```yaml
# application-local.yml
remote:
  tools:
    enabled: true
    base-url: http://localhost:8000
```

## 监控和日志

### Railway 日志

在 Railway 控制台查看实时日志：
1. 进入项目
2. 点击 "Deployments"
3. 点击 "View Logs"

### 指标监控

访问 `https://YOUR_URL/up.railway.app/metrics` 获取 Prometheus 格式指标。

## 安全注意事项

1. **网络安全**: 建议配置 `REMOTE_TOOLS_API_KEY` 进行认证
2. **配额管理**: Spring Boot 端负责配额检查，Python 服务不处理
3. **文件隔离**: 每个用户有独立目录 `/tmp/{userId}/{sessionId}/`
4. **TTL 清理**: 启动时自动清理超过 TTL 的文件

## 故障排查

### 服务无法访问

1. 检查 Railway 部署状态
2. 查看 Railway 日志
3. 确认环境变量配置正确

### 工具执行失败

1. 检查 `X-User-ID` 请求头是否传递
2. 查看 Python 服务日志
3. 确认工作目录权限

### 文件无法写入

1. 确认路径在工作目录内
2. 检查磁盘配额（Railway 默认 500MB）
3. 确认 TTL 未过期
