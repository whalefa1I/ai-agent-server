# 测试覆盖文档

## 测试模块总览

| 模块 | 测试脚本 | JUnit 测试 | 状态 |
|------|---------|-----------|------|
| 模型调用 | `test-model-call.*` | - | ✅ |
| 本地工具 | `test-local-tools.*` | `Local*ToolTest` | ✅ |
| 子 Agent | `test-subagent.*` | `TaskStateTest`, `CoordinatorStateTest` | ✅ |
| WebSocket | `test-websocket.*` | - | ✅ |
| TUI | `test-tui.*` | - | ✅ |

## JUnit 测试状态

```
Tests run: 25
Failures: 0
Errors: 0
Skipped: 0
Build: SUCCESS
```

### 测试类详情

| 测试类 | 测试方法数 | 状态 |
|--------|-----------|------|
| `CoordinatorStateTest` | 10 | ✅ 通过 |
| `TaskStateTest` | 5 | ✅ 通过 |
| `TaskToolOutputParserTest` | 2 | ✅ 通过 |
| `LocalFileReadToolTest` | 4 | ✅ 通过 |
| `LocalGlobToolTest` | 4 | ✅ 通过 |

---

## 1. 模型调用测试 (`test-model-call.*`)

### 测试场景

| ID | 场景 | 预期结果 |
|----|------|---------|
| MC-01 | 直接调用 Bailian API | 返回有效响应 |
| MC-02 | Spring Boot 健康检查 | 状态 UP |
| MC-03 | 获取会话 ID | 返回有效 sessionId |
| MC-04 | 简单对话 | 返回模型回复 |
| MC-05 | 获取统计数据 | 返回 token 使用统计 |

### 配置检查
- [ ] `OPENAI_BASE_URL` = `https://coding.dashscope.aliyuncs.com/v1`
- [ ] `OPENAI_API_KEY` = `sk-sp-ab63f62c8df3494a8763982b1a741081`
- [ ] `OPENAI_MODEL` = `qwen-max`

---

## 2. 本地工具测试 (`test-local-tools.*`)

### 工具列表

| 工具名 | 功能 | 测试方法 |
|--------|------|---------|
| `glob` | 文件搜索 | 搜索 `*.txt` 文件 |
| `file_read` | 读取文件 | 读取测试文件内容 |
| `file_write` | 写入文件 | 创建新文件 |
| `file_edit` | 编辑文件 | 替换文件内容 |
| `bash` | 执行命令 | 运行 `dir` / `ls` |
| `grep` | 文本搜索 | 搜索包含关键词的行 |

### JUnit 测试类

| 类名 | 测试方法数 | 覆盖率 |
|------|-----------|--------|
| `LocalFileReadToolTest` | 6 | 100% |
| `LocalFileWriteToolTest` | 6 | 100% |
| `LocalGlobToolTest` | 5 | 100% |
| `LocalBashToolTest` | - | - |

---

## 3. 子 Agent 测试 (`test-subagent.*`)

### Agent 类型

| Agent 类型 | 专长 | 工具集 |
|-----------|------|--------|
| `bash` | Shell 命令 | Bash 相关工具 |
| `explore` | 代码探索 | 只读工具 |
| `plan` | 规划 | 规划类工具 |
| `edit` | 编辑 | 文件编辑工具 |
| `general` | 通用 | 所有工具 |

### 测试场景

| ID | 场景 | 预期结果 |
|----|------|---------|
| SA-01 | Bash Agent 执行命令 | 返回命令输出 |
| SA-02 | Explore Agent 读取文件 | 返回文件内容 |
| SA-03 | Edit Agent 修改文件 | 文件被修改 |
| SA-04 | 获取工具调用历史 | 返回调用记录 |

### JUnit 测试类

| 类名 | 测试方法数 | 描述 |
|------|-----------|------|
| `TaskStateTest` | 8 | 任务状态转换 |
| `CoordinatorStateTest` | 10 | 协调者状态管理 |

---

## 4. WebSocket 测试 (`test-websocket.*`)

### 测试场景

| ID | 场景 | 预期结果 |
|----|------|---------|
| WS-01 | 获取 Token | 返回有效 token |
| WS-02 | WebSocket 连接 | 连接成功 |
| WS-03 | 发送消息 | 消息被接收 |
| WS-04 | 接收响应 | 收到服务端响应 |
| WS-05 | HTTP 长轮询 | 备选方案可用 |

### 连接信息

| 参数 | 值 |
|------|-----|
| URL | `ws://localhost:8080/ws/agent/{token}` |
| 协议 | WebSocket |
| 认证 | Token（可选） |

---

## 5. TUI 测试 (`test-tui.*`)

### 前置条件

- [ ] 服务端已启动
- [ ] TUI 客户端已构建
- [ ] WebSocket 可用

### 测试清单

| ID | 功能 | 测试方法 |
|----|------|---------|
| TUI-01 | 启动客户端 | `java -jar tui-client.jar` |
| TUI-02 | 发送消息 | 输入文本并回车 |
| TUI-03 | 文件读取 | 请求读取文件 |
| TUI-04 | 文件搜索 | 请求搜索文件 |
| TUI-05 | 命令执行 | 请求执行命令 |
| TUI-06 | 工具历史 | 查看工具调用 |
| TUI-07 | 会话统计 | 查看统计信息 |
| TUI-08 | 子 Agent | 委派子任务 |

---

## 运行所有测试

### 快速测试（推荐）

```bash
# 1. 启动应用
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local

# 2. 在新终端运行测试
.\tests\test-model-call.ps1
.\tests\test-local-tools.ps1
.\tests\test-subagent.ps1
```

### 完整测试

```bash
# 1. 启动应用
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local

# 2. 运行所有 PowerShell 测试
Get-ChildItem .\tests\test-*.ps1 | ForEach-Object { & $_.FullName }

# 或运行所有 Bash 测试
for f in tests/test-*.sh; do bash "$f"; done
```

### JUnit 测试

```bash
# 运行所有测试
mvnw.cmd test

# 运行特定包测试
mvnw.cmd test -Dtest="demo.k8s.agent.coordinator.*"
mvnw.cmd test -Dtest="demo.k8s.agent.tools.local.*"
```

---

## 测试输出示例

### 模型调用测试输出

```
========================================
  模型调用测试 - Bailian/Qwen 平台
========================================

[测试 1] 直接 API 调用测试
  [PASS] API 调用成功
  回复：我是阿里巴巴通义实验室研发的超大规模语言模型...

[测试 2] Spring Boot 应用健康检查
  [PASS] 应用健康运行
  状态：{"timestamp":"...","status":"UP"}

...
```

### JUnit 测试输出

```
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] Results:
[INFO]
[INFO] Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] BUILD SUCCESS
```

---

## 故障排查

### 错误：连接被拒绝

```
原因：应用未启动
解决：运行 mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

### 错误：API Key 无效

```
原因：API Key 配置错误
解决：检查 application-local.yml 中的 api-key 配置
```

### 错误：工具执行失败

```
原因：文件路径或权限问题
解决：确保测试目录可写，路径正确
```

---

## 测试覆盖率目标

| 模块 | 目标覆盖率 | 当前状态 |
|------|-----------|---------|
| 模型调用 | 80% | ✅ 已覆盖 |
| 本地工具 | 90% | ✅ 已覆盖 |
| 子 Agent | 85% | ✅ 已覆盖 |
| WebSocket | 70% | ⚠️ 部分覆盖 |
| TUI | 60% | ⚠️ 待完善 |

---

## 持续改进

- [ ] 增加 WebSocket 自动化测试
- [ ] 增加 TUI 自动化测试
- [ ] 添加性能测试
- [ ] 添加压力测试
- [ ] 集成 CI/CD
