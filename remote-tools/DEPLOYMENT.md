# Railway 部署指南 - Python 远程工具服务

## 目录结构

```
remote-tools/
├── main.py              # FastAPI 应用
├── pyproject.toml       # uv 项目配置
├── Dockerfile           # Railway 部署配置
├── railway.toml         # Railway 配置
└── README.md            # 使用说明
```

## 部署步骤

### 步骤 1: 在 Railway 创建项目

1. 访问 [Railway](https://railway.app)
2. 点击 **"New Project"**
3. 选择 **"Deploy from GitHub repo"**
4. 选择你的 GitHub 仓库（`ai-agent-server`）
5. 点击 **"Deploy"**

### 步骤 2: 配置 Root Directory

在 Railway 项目设置中:

1. 进入 **"Settings"** 标签
2. 找到 **"Root Directory"**
3. 设置为 `remote-tools`
4. Railway 会自动检测 `Dockerfile` 并部署

### 步骤 3: 配置环境变量（可选）

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `WORK_DIR` | `/tmp/agent-tools` | 用户数据工作目录 |
| `TTL_SECONDS` | `86400` | 会话 TTL（秒），默认 24 小时 |
| `PORT` | `8000` | 服务端口 |

### 步骤 4: 获取服务 URL

部署完成后，Railway 会分配一个公共 URL:
```
https://ai-agent-server-production.up.railway.app
```

## Spring Boot 配置

在 Spring Boot 的 `application.yml` 或环境变量中添加：

```yaml
remote:
  tools:
    enabled: true
    base-url: https://ai-agent-server-production.up.railway.app
    api-key: your-api-key-here  # 可选，如需认证
    timeout-seconds: 60
```

或使用环境变量：

```bash
# 生产环境
REMOTE_TOOLS_ENABLED=true
REMOTE_TOOLS_BASE_URL=https://ai-agent-server-production.up.railway.app
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
    "tool_name": "bash",
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

启动 Spring Boot 应用后，Agent 会自动将危险工具路由到远程服务：

- `bash` → 远程执行
- `file_write` → 远程执行
- `file_read` → 本地执行（只读）
- `glob`/`grep` → 本地执行（只读）

## 监控和日志

### Railway 日志

1. 进入 Railway 项目
2. 点击 **"Deployments"**
3. 点击 **"View Logs"** 查看实时日志

### 指标监控

访问 `https://YOUR_RAILWAY_URL.up.railway.app/metrics` 获取 Prometheus 格式指标：

```
# HELP active_users 活跃用户数
# TYPE active_users gauge
active_users 5
```

## 安全注意事项

1. **API Key 认证**: 建议配置 `REMOTE_TOOLS_API_KEY` 进行认证（待实现）
2. **配额管理**: Spring Boot 端负责配额检查，Python 服务不处理
3. **文件隔离**: 每个用户有独立目录 `/tmp/{userId}/{sessionId}/`
4. **TTL 清理**: 启动时自动清理超过 TTL 的文件（默认 24 小时）

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
2. 检查 Railway 磁盘配额（免费计划默认 500MB）
3. 确认 TTL 未过期
