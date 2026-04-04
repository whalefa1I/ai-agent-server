# 测试套件使用指南

## 快速开始

### 1. 启动应用

```bash
# Windows
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local

# Linux/Mac
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### 2. 验证应用运行

```bash
curl http://localhost:8080/api/health
```

预期输出：`{"timestamp":"...","status":"UP"}`

---

## 测试脚本

所有测试脚本位于 `tests/` 目录：

| 脚本名 | 功能 | 运行命令 (Windows) |
|--------|------|-------------------|
| `test-model-call.ps1` | 模型调用测试 | `.\tests\test-model-call.ps1` |
| `test-local-tools.ps1` | 本地工具测试 | `.\tests\test-local-tools.ps1` |
| `test-subagent.ps1` | 子 Agent 测试 | `.\tests\test-subagent.ps1` |
| `test-websocket.ps1` | WebSocket 测试 | `.\tests\test-websocket.ps1` |
| `test-tui.ps1` | TUI 客户端测试 | `.\tests\test-tui.ps1` |

---

## JUnit 单元测试

### 运行所有测试

```bash
mvnw.cmd test
```

### 运行特定测试类

```bash
# 协调器状态测试
mvnw.cmd test -Dtest=CoordinatorStateTest

# 任务状态测试
mvnw.cmd test -Dtest=TaskStateTest

# 工具测试
mvnw.cmd test -Dtest=LocalFileReadToolTest,LocalGlobToolTest

# 运行多个测试类
mvnw.cmd test -Dtest="*StateTest,*ToolTest"
```

### 当前测试覆盖

```
Tests run: 25
Failures: 0
Errors: 0
Skipped: 0
```

---

## 模型调用测试详解

### 测试内容

1. **API 连通性** - 直接调用 Bailian API
2. **健康检查** - Spring Boot 应用状态
3. **会话管理** - 获取会话 ID
4. **对话功能** - 简单对话测试
5. **统计查询** - Token 使用统计

### 配置检查

编辑 `src/main/resources/application-local.yml`:

```yaml
spring:
  ai:
    openai:
      api-key: sk-sp-ab63f62c8df3494a8763982b1a741081
      base-url: https://coding.dashscope.aliyuncs.com/v1
      chat:
        options:
          model: qwen-max
```

---

## 本地工具测试详解

### 工具列表

| 工具 | 功能 | 测试方法 |
|------|------|---------|
| `glob` | 文件搜索 | 搜索 `*.txt` |
| `file_read` | 读取文件 | 读取测试文件 |
| `file_write` | 写入文件 | 创建新文件 |
| `file_edit` | 编辑文件 | 替换内容 |
| `bash` | 执行命令 | 运行 `dir` |
| `grep` | 文本搜索 | 搜索关键词 |

### 手动测试示例

```bash
# 使用 curl 测试 glob 工具
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "搜索当前目录下的所有.java 文件"}'
```

---

## 子 Agent 测试详解

### Agent 类型

| 类型 | 专长 | 工具集 |
|------|------|--------|
| `bash` | Shell 命令 | Bash 工具 |
| `explore` | 代码探索 | 只读工具 |
| `plan` | 规划 | 规划工具 |
| `edit` | 编辑 | 文件编辑工具 |
| `general` | 通用 | 所有工具 |

### 测试示例

```bash
# 测试 Bash Agent
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "创建一个 Bash Agent 执行 echo Hello 命令"}'
```

---

## WebSocket 测试详解

### 连接信息

- **URL**: `ws://localhost:8080/ws/agent/{token}`
- **协议**: WebSocket
- **认证**: Token（可选）

### 使用 wscat 测试

```bash
# 安装
npm install -g wscat

# 连接
wscat -c "ws://localhost:8080/ws/agent/test-token"

# 发送消息
> {"type":"user","content":"Hello"}
```

---

## TUI 测试详解

### 构建 TUI 客户端

```bash
cd tui-client
mvn package -DskipTests
```

### 启动 TUI

```bash
# 获取 Token
curl -X POST http://localhost:8080/api/ws/token \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "test"}'

# 启动
java -jar tui-client/target/minimal-k8s-agent-tui-jar-with-dependencies.jar \
  --server ws://localhost:8080/ws/agent/{token}
```

---

## 故障排查

### 应用启动失败

```bash
# 检查 Java 版本
java -version

# 应为 Java 21
# 如不是，设置 JAVA_HOME
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot
```

### 模型调用返回错误

1. 检查 API Key 是否有效
2. 检查 Base URL 是否正确
3. 检查网络连接

### 测试失败

查看 surefire 报告：

```bash
type target\surefire-reports\*.txt
```

---

## 持续集成

### GitHub Actions 示例

```yaml
name: Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Run tests
        run: mvn test
```

---

## 测试报告

运行测试后生成报告：

```bash
# HTML 报告
mvnw.cmd test -Dsurefire.reportFormat=html

# 查看报告
start target\surefire-reports\index.html
```

---

## 联系与支持

- 项目文档：`docs/`
- 测试指南：`tests/README.md`
- 测试覆盖：`docs/TEST-COVERAGE.md`
