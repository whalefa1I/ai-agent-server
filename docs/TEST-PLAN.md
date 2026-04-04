# minimal-k8s-agent-demo 测试计划

## 项目概述

基于 Spring Boot 4 + Spring AI 2.0.0-M3 的智能代理 demo，包含：
- 本地工具系统（6 个核心工具：glob, file_read, file_write, file_edit, bash, grep）
- 统一执行器（支持 LOCAL/REMOTE/AUTO 模式）
- 多 Agent 协作架构
- WebSocket TUI 支持
- 权限管理系统
- 可观测性基础设施

## 环境配置状态

### 已配置
- ✅ JDK 17.0.18 (Eclipse Adoptium)
- ✅ Maven 3.9.14
- ✅ Maven 仓库：D:\.m2\repository

### 待配置
- [ ] OPENAI_API_KEY 设置（使用百炼平台）
- [ ] OPENAI_BASE_URL: https://coding.dashscope.aliyuncs.com/v1

## 测试阶段

### 阶段 1：编译验证
**目标**: 确保项目可以成功编译

**步骤**:
1. 安装 spring-ai-agent-utils 依赖
2. 编译 minimal-k8s-agent-demo
3. 运行单元测试

**命令**:
```bash
# 安装依赖
cd spring-ai-agent-utils
mvn install -DskipTests

# 编译主项目
cd minimal-k8s-agent-demo
mvn clean compile

# 运行测试
mvn test
```

**验收标准**:
- [ ] spring-ai-agent-utils 安装成功
- [ ] minimal-k8s-agent-demo 编译成功（无错误）
- [ ] 现有测试通过（TaskToolOutputParserTest 等）

---

### 阶段 2：本地工具测试
**目标**: 验证 6 个本地工具的功能

#### 2.1 GlobTool 测试
**测试内容**:
- 文件模式匹配（*.java, **/*.txt）
- 空模式处理
- 不存在路径处理

**测试脚本**:
```bash
# 创建测试目录
mkdir test-glob
echo "test" > test-glob/test1.java
echo "test" > test-glob/test2.java
echo "test" > test-glob/readme.md

# 调用工具测试
curl -X POST http://localhost:8080/api/tools/execute \
  -H "Content-Type: application/json" \
  -d '{"tool":"glob","input":{"pattern":"**/*.java","path":"test-glob"}}'
```

**验收标准**:
- [ ] 正确匹配 Java 文件
- [ ] 排除非 Java 文件
- [ ] 错误处理正确

#### 2.2 FileReadTool 测试
**测试内容**:
- 完整文件读取
- 范围读取（offset/limit）
- 大文件保护

**测试脚本**:
```bash
# 创建测试文件
echo -e "Line1\nLine2\nLine3\nLine4\nLine5" > test.txt

# 测试读取
curl -X POST http://localhost:8080/api/tools/execute \
  -H "Content-Type: application/json" \
  -d '{"tool":"file_read","input":{"path":"test.txt","offset":1,"limit":2}}'
```

**验收标准**:
- [ ] 正确读取指定范围
- [ ] 显示总行数
- [ ] 错误处理正确

#### 2.3 FileWriteTool 测试
**测试内容**:
- 新文件写入
- 覆盖现有文件
- 原子操作验证

**测试脚本**:
```bash
curl -X POST http://localhost:8080/api/tools/execute \
  -H "Content-Type: application/json" \
  -d '{"tool":"file_write","input":{"path":"output.txt","content":"Hello World"}}'
```

**验收标准**:
- [ ] 文件成功创建
- [ ] 内容正确
- [ ] 原子操作（temp 文件 + move）

#### 2.4 FileEditTool 测试
**测试内容**:
- 字符串替换
- 匹配位置追踪
- 不匹配处理

**测试脚本**:
```bash
# 先创建文件
echo "Hello World" > edit.txt

# 测试编辑
curl -X POST http://localhost:8080/api/tools/execute \
  -H "Content-Type: application/json" \
  -d '{"tool":"file_edit","input":{"path":"edit.txt","oldText":"World","newText":"China"}}'
```

**验收标准**:
- [ ] 替换成功
- [ ] 显示行号
- [ ] 不匹配时返回错误

#### 2.5 BashTool 测试
**测试内容**:
- 简单命令执行
- 危险命令拦截
- 超时处理

**测试脚本**:
```bash
# 正常命令
curl -X POST http://localhost:8080/api/tools/execute \
  -H "Content-Type: application/json" \
  -d '{"tool":"bash","input":{"command":"echo hello"}}'

# 危险命令（应被拦截）
curl -X POST http://localhost:8080/api/tools/execute \
  -H "Content-Type: application/json" \
  -d '{"tool":"bash","input":{"command":"rm -rf /"}}'
```

**验收标准**:
- [ ] 正常命令返回输出
- [ ] 危险命令被拦截
- [ ] 错误信息清晰

#### 2.6 GrepTool 测试
**测试内容**:
- 正则搜索
- 上下文显示
- 文件过滤

**测试脚本**:
```bash
curl -X POST http://localhost:8080/api/tools/execute \
  -H "Content-Type: application/json" \
  -d '{"tool":"grep","input":{"pattern":"TODO","path":".","include":"**/*.java","contextLines":2}}'
```

**验收标准**:
- [ ] 正确匹配模式
- [ ] 显示上下文
- [ ] 支持文件过滤

---

### 阶段 3：API 端点测试
**目标**: 验证 HTTP API 端点

#### 3.1 健康检查
```bash
curl -s http://localhost:8080/actuator/health | jq
```

#### 3.2 对话 API
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"请用 glob 工具查找当前目录的 Java 文件"}'
```

#### 3.3 流式对话
```bash
curl -N -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"message":"Hello"}'
```

#### 3.4 可观测性
```bash
# 会话统计
curl http://localhost:8080/api/observability/stats

# Prometheus 指标
curl http://localhost:8080/api/observability/metrics
```

---

### 阶段 4：TUI 客户端测试
**目标**: 验证 WebSocket TUI 客户端

**步骤**:
1. 启动服务端
2. 构建 TUI 客户端
3. 连接并测试命令

**命令**:
```bash
# 构建 TUI
cd tui-client
mvn package -DskipTests

# 运行 TUI
java -jar target/minimal-k8s-agent-tui-jar-with-dependencies.jar \
  --server ws://localhost:8080/ws/agent
```

**测试命令**:
- `/help` - 显示帮助
- `/clear` - 清屏
- `/history` - 查看历史
- `/stats` - 查看统计
- `/quit` - 退出

---

### 阶段 5：集成功能测试
**目标**: 验证完整功能流程

#### 5.1 权限确认流程
```bash
# 触发需要权限的操作
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"请删除根目录下的所有文件"}'

# 查看待确认请求
curl http://localhost:8080/api/permissions/pending

# 提交响应
curl -X POST http://localhost:8080/api/permissions/respond \
  -H "Content-Type: application/json" \
  -d '{"requestId":"xxx","choice":"DENY"}'
```

#### 5.2 多 Agent 协作
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"请用 task 委派一个子任务来分析项目结构"}'

# 查看活跃任务
curl http://localhost:8080/api/coordinator/tasks/active
```

---

### 阶段 6：Docker 部署测试（可选）
**目标**: 验证容器化部署

**命令**:
```bash
# 构建镜像
docker-compose build

# 启动服务
docker-compose up -d

# 查看日志
docker-compose logs -f

# 清理
docker-compose down
```

---

## 问题排查

### 常见问题

1. **编译失败 - Unsupported class file major version**
   - 原因：JDK 版本过低
   - 解决：确保使用 JDK 17+
   ```bash
   java -version
   ```

2. **依赖找不到 - Could not find artifact spring-ai-agent-utils**
   - 原因：未先安装依赖
   - 解决：
   ```bash
   cd spring-ai-agent-utils
   mvn install -DskipTests
   ```

3. **API Key 无效**
   - 原因：环境变量未设置或 key 过期
   - 解决：
   ```bash
   export OPENAI_API_KEY=sk-xxx
   export OPENAI_BASE_URL=https://coding.dashscope.aliyuncs.com/v1
   ```

4. **C 盘空间不足**
   - 原因：Maven 仓库默认在 C 盘
   - 解决：已配置 Maven settings.xml 到 D:\.m2\repository

---

## 测试报告模板

### 执行摘要

| 阶段 | 测试项 | 状态 | 备注 |
|------|--------|------|------|
| 阶段 1 | 编译验证 | ⬜ 待执行 | |
| 阶段 2 | 本地工具测试 | ⬜ 待执行 | |
| 阶段 3 | API 端点测试 | ⬜ 待执行 | |
| 阶段 4 | TUI 客户端 | ⬜ 待执行 | |
| 阶段 5 | 集成功能 | ⬜ 待执行 | |

### 详细结果

#### 阶段 1：编译验证
- [ ] spring-ai-agent-utils 安装成功
- [ ] minimal-k8s-agent-demo 编译成功
- [ ] 单元测试通过

#### 阶段 2：本地工具测试
- [ ] GlobTool 通过
- [ ] FileReadTool 通过
- [ ] FileWriteTool 通过
- [ ] FileEditTool 通过
- [ ] BashTool 通过
- [ ] GrepTool 通过

---

## 下一步

1. 完成环境准备后，按阶段逐项执行测试
2. 记录每个测试项的结果
3. 发现问题时创建修复任务
4. 所有测试通过后，输出最终报告
