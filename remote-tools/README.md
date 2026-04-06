# AI Agent Remote Tools

Python 远程工具执行服务 - MVP

## 快速开始

### 本地运行

```bash
pip install -r requirements.txt
python main.py
```

### 测试

```bash
# 健康检查
curl http://localhost:8000/health

# 执行 Shell 命令
curl -X POST http://localhost:8000/tools/execute \
  -H "X-User-ID: test-user" \
  -H "Content-Type: application/json" \
  -d '{"tool_name": "shell", "input": {"command": "echo hello"}}'
```

## 部署到 Railway

1. 推送代码到 GitHub
2. Railway → New Project → Deploy from GitHub
3. 选择本仓库
4. Railway 会自动检测 `remote-tools/` 子目录

或者在 Railway 设置中指定:
- Root Directory: `remote-tools`

## 支持的工具

- `shell` - 执行 Shell 命令
- `file_read` - 读取文件
- `file_write` - 写入文件
- `file_list` - 列出目录

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `WORK_DIR` | `/tmp/agent-tools` | 工作目录 |
| `TTL_SECONDS` | `86400` | 文件 TTL(秒) |
| `PORT` | `8000` | 服务端口 |
