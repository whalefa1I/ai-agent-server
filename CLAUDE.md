# CLAUDE.md - Minimal K8s Agent Demo

基于 **Spring Boot 4** + **Spring AI** 的最小 Agent 演示项目。

## 快速开始

### 编译项目

```bash
# 方式 1: 使用一键编译脚本（推荐）
./build.bat

# 方式 2: 手动使用 Maven Wrapper
./mvnw.cmd clean compile

# 方式 3: 设置 JAVA_HOME 后编译
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot
./mvnw.cmd clean compile
```

### 运行应用

```bash
# 设置环境变量（可选）
export OPENAI_API_KEY=your_key

# 运行
./run.sh
# 或 Windows
./run.bat
```

## 环境配置

**Java 21 路径:** `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot\`

**Maven:** 使用项目内置的 Maven Wrapper（`./mvnw.cmd`）

详细说明见 `JAVA_ENV.md`

## 核心功能

### 1. 工具系统
- 本地文件操作（读写、复制、移动、删除）
- Git 操作
- Shell 执行
- 记忆搜索（语义搜索跨会话记忆）
- Skills（插件系统）

### 2. 记忆系统
- 向量嵌入（Spring AI EmbeddingModel）
- 语义搜索
- MEMORY.md 支持（`~/.claude/MEMORY.md`）

### 3. Plugin Hooks
- BEFORE/AFTER/AROUND Hook
- JavaScript Hook 支持（`.openclaw/hooks/`）
- 工具调用拦截

### 4. 飞书频道（可选）
- HTTP 端点：`/api/channels/feishu/event`
- 消息机器人集成

## API 端点

```bash
POST /api/chat                 # 有状态对话
GET  /api/permissions/pending  # 获取待确认权限请求
POST /api/permissions/respond  # 提交用户响应
GET  /api/observability/stats  # 会话统计
GET  /api/channels/feishu/health  # 飞书健康检查
```

## 配置项

```yaml
demo:
  hooks:
    enabled: true
  channels:
    feishu:
      enabled: false  # 改为 true 并配置 appId/appSecret 启用飞书
```

## 测试

```bash
# 测试本地工具
./test-local-tools.bat
```

## 文档

- [Phase 2/3/4 实现总结](docs/phase-2-3-4-implementation-summary.md)
- [Java 环境配置](JAVA_ENV.md)
