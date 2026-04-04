# 测试指南

本文档介绍如何运行 minimal-k8s-agent-demo 项目的各类测试。

## 前置条件

1. **启动应用**: 
   ```bash
   # Windows
   mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
   
   # Linux/Mac
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
   ```

2. **验证应用运行**:
   ```bash
   curl http://localhost:8080/api/health
   # 预期输出：{"status":"UP",...}
   ```

---

## 测试脚本

### 1. 模型调用测试

测试 Bailian/Qwen 平台的模型调用功能。

**PowerShell (Windows)**:
```powershell
.\tests\test-model-call.ps1
```

**Bash (Linux/Mac/WSL)**:
```bash
./tests/test-model-call.sh
```

**测试内容**:
- [ ] 直接 API 调用（验证 API Key 和网络）
- [ ] Spring Boot 应用健康检查
- [ ] 会话 ID 获取
- [ ] 简单对话测试
- [ ] 模型调用统计获取

---

### 2. 本地工具测试

测试 6 个基本本地工具的功能。

**PowerShell (Windows)**:
```powershell
.\tests\test-local-tools.ps1
```

**Bash (Linux/Mac/WSL)**:
```bash
./tests/test-local-tools.sh
```

**测试内容**:
- [ ] `glob` - 文件搜索
- [ ] `file_read` - 读取文件
- [ ] `file_write` - 写入文件
- [ ] `file_edit` - 编辑文件
- [ ] `bash` - 执行 Shell 命令
- [ ] `grep` - 文本搜索

---

### 3. 子 Agent 测试

测试 WorkerAgentExecutor 和子 Agent 功能。

**PowerShell (Windows)**:
```powershell
.\tests\test-subagent.ps1
```

**Bash (Linux/Mac/WSL)**:
```bash
./tests/test-subagent.sh
```

**测试内容**:
- [ ] Bash Agent（专门执行命令）
- [ ] Explore Agent（只读探索）
- [ ] Edit Agent（文件编辑）
- [ ] 工具调用历史查询

---

### 4. WebSocket 测试

测试 WebSocket 连接和实时消息通信。

**PowerShell (Windows)**:
```powershell
.\tests\test-websocket.ps1
```

**Bash (Linux/Mac/WSL)**:
```bash
./tests/test-websocket.sh
```

**测试内容**:
- [ ] WebSocket Token 获取
- [ ] WebSocket 连接（需手动使用浏览器或 wscat）
- [ ] HTTP 长轮询备选测试

**手动 WebSocket 测试**:
```bash
# 安装 wscat
npm install -g wscat

# 连接
wscat -c "ws://localhost:8080/ws/agent/test-token"

# 发送消息
> {"type":"user","content":"Hello"}
```

---

### 5. TUI 客户端测试

测试 TUI（终端用户界面）客户端。

**PowerShell (Windows)**:
```powershell
.\tests\test-tui.ps1
```

**Bash (Linux/Mac/WSL)**:
```bash
./tests/test-tui.sh
```

**前置条件**:
```bash
# 构建 TUI 客户端
cd tui-client
mvn package -DskipTests
```

**测试内容**:
- [ ] TUI 客户端启动
- [ ] 发送简单消息
- [ ] 文件读取
- [ ] 文件搜索
- [ ] 命令执行
- [ ] 子 Agent 委派

---

## JUnit 单元测试

运行所有单元测试：

```bash
# Windows
mvnw.cmd test

# Linux/Mac
./mvnw test
```

### 测试类列表

| 测试类 | 描述 |
|--------|------|
| `TaskStateTest` | 任务状态转换测试 |
| `CoordinatorStateTest` | 协调者状态管理测试 |
| `LocalFileReadToolTest` | 文件读取工具测试 |
| `LocalFileWriteToolTest` | 文件写入工具测试 |
| `LocalGlobToolTest` | 文件搜索工具测试 |
| `LocalBashToolTest` | Bash 工具测试 |

运行单个测试类：

```bash
# Windows
mvnw.cmd test -Dtest=TaskStateTest

# Linux/Mac
./mvnw test -Dtest=TaskStateTest
```

---

## 测试检查清单

### 模型调用
- [ ] API Key 配置正确
- [ ] 网络连接正常
- [ ] 模型返回有效响应
- [ ] Token 统计正常

### 本地工具
- [ ] glob 能找到匹配文件
- [ ] file_read 能读取文件内容
- [ ] file_write 能创建/覆盖文件
- [ ] file_edit 能修改文件内容
- [ ] bash 能执行系统命令
- [ ] grep 能搜索文本

### 子 Agent
- [ ] Bash Agent 执行命令
- [ ] Explore Agent 读取文件
- [ ] Edit Agent 修改文件
- [ ] 工具调用记录正确

### WebSocket
- [ ] 连接成功
- [ ] 消息发送/接收正常
- [ ] 错误处理正确

### TUI
- [ ] 客户端启动
- [ ] 消息显示正常
- [ ] 工具调用可视化
- [ ] 会话统计显示

---

## 常见问题

### Q: 应用启动失败
**A**: 检查日志，常见问题：
- 端口 8080 被占用
- JAVA_HOME 未配置为 Java 21
- Maven 依赖未安装（先运行 `mvn install -DskipTests`）

### Q: 模型调用返回 404
**A**: 检查配置：
- `application-local.yml` 中 `base-url` 是否正确
- `api-key` 是否有效
- 模型名称是否被支持

### Q: 工具执行失败
**A**: 检查：
- 文件路径权限
- Shell 命令是否合法
- 输入参数格式是否正确

### Q: WebSocket 连接失败
**A**: 尝试：
- 检查防火墙设置
- 确认 Token 是否有效
- 使用 HTTP 端点先测试应用状态

---

## 测试报告模板

运行测试后，填写以下报告：

```markdown
## 测试报告

**日期**: YYYY-MM-DD
**环境**: Windows 11 / Java 21 / Spring Boot 4.0.0

### 模型调用测试
- [ ] 通过 / 失败
- 备注：_______

### 本地工具测试
- [ ] 通过 / 失败
- 备注：_______

### 子 Agent 测试
- [ ] 通过 / 失败
- 备注：_______

### WebSocket 测试
- [ ] 通过 / 失败
- 备注：_______

### TUI 测试
- [ ] 通过 / 失败
- 备注：_______

### 单元测试
- 通过率：XX%
- 失败用例：_______
```
