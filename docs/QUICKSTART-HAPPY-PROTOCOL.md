# Happy Protocol 兼容层 - 快速开始指南

## 给领导汇报演示准备

### 1. 启动应用

```bash
# Windows
cd G:\project\ai-agent-server
./run.bat

# 或使用 Maven
./mvnw.cmd spring-boot:run
```

### 2. 访问前端页面

应用启动后，访问：`http://localhost:8080`

### 3. 演示功能

#### 功能 1：文件搜索（Glob 工具）

```
用户输入：查找所有 TypeScript 文件
```

显示效果：
- 🔍 工具块展开显示搜索模式 `**/*.ts`
- 显示搜索结果文件列表
- 状态指示器：运行中 → 完成

#### 功能 2：文件读取（Read 工具）

```
用户输入：读取 package.json 的内容
```

显示效果：
- 📄 工具块显示文件路径 `package.json`
- 展开显示文件内容
- 状态指示器：完成（绿色）

#### 功能 3：终端命令（Bash 工具）

```
用户输入：列出当前目录的文件
```

显示效果：
- 💻 工具块显示命令 `ls -la`
- 显示命令输出
- 状态指示器：运行中 → 完成

#### 功能 4：任务管理（TodoWrite 工具）

```
用户输入：创建一个任务列表
1. 分析项目结构
2. 实现核心功能
3. 测试验证
```

显示效果：
- 💡 工具块显示任务列表
- 每个任务显示完成状态
- 可交互标记任务完成

---

## 技术亮点说明

### 1. Happy Protocol 兼容

- ✅ 9 种标准事件类型
- ✅ 消息信封格式
- ✅ 工具调用生命周期管理
- ✅ Artifact 存储机制

### 2. 实时状态推送

- WebSocket 连接 `/ws/tool-state`
- 工具状态实时更新
- 自动重连机制

### 3. 工具注册表

- 50+ 预定义工具
- 动态标题和描述
- 状态指示器
- 图标系统

### 4. 前后端分离设计

- RESTful API
- 清晰的接口定义
- 可扩展架构

---

## API 端点演示

### 使用 curl 测试

```bash
# 1. 获取会话工具状态
curl http://localhost:8080/api/v2/tool-state/session/SESSION_ID

# 2. 获取所有 artifacts
curl "http://localhost:8080/api/v1/artifacts?accountId=ACCOUNT_ID"

# 3. 创建 artifact
curl -X POST http://localhost:8080/api/v1/artifacts \
  -H "Content-Type: application/json" \
  -d '{
    "id": "test-123",
    "sessionId": "SESSION_ID",
    "accountId": "ACCOUNT_ID",
    "header": "eyJ0eXBlIjoidGVzdCJ9",
    "body": "eyJpbnB1dCI6e319"
  }'
```

---

## 架构图

```
┌─────────────────────────────────────────────────────────┐
│                      Frontend (Vue)                      │
├─────────────────────────────────────────────────────────┤
│  MessageView  →  ToolCallView  →  StatusIndicator       │
│       ↓                ↓                                  │
│  knownTools  ←  artifact-transformer  ←  use-tool-state │
└─────────────────────────────────────────────────────────┘
                          ↕ (HTTP + WebSocket)
┌─────────────────────────────────────────────────────────┐
│                   Backend (Spring Boot)                  │
├─────────────────────────────────────────────────────────┤
│  ToolStateController  →  ToolStateService               │
│       ↓                        ↓                         │
│  ToolArtifactRepository  →  ToolArtifact (H2 DB)        │
└─────────────────────────────────────────────────────────┘
```

---

## 数据流程图

```
用户请求
   ↓
Agent 处理
   ↓
调用工具 (ToolCall)
   ↓
创建 Artifact (header + body)
   ↓
存储到数据库 ←→ 推送 WebSocket 消息
   ↓
前端接收并转换
   ↓
显示 ToolCallView
```

---

## 关键代码示例

### 1. 添加工具调用

```typescript
import { toolStateManager } from '@/tools/hooks/use-tool-state';

// 当工具开始执行
toolStateManager.addToolCall({
  id: 'call_abc123',
  name: 'bash',
  title: 'Run command',
  description: 'ls -la',
  args: { command: 'ls -la' },
  state: 'running',
  startTime: Date.now()
});

// 当工具完成
toolStateManager.updateToolCall('call_abc123', {
  state: 'completed',
  result: { stdout: '...', stderr: '' },
  endTime: Date.now(),
  durationMs: 150
});
```

### 2. 订阅状态变化

```typescript
const unsubscribe = toolStateManager.subscribe(() => {
  const state = toolStateManager.getState();
  console.log('当前工具调用数:', state.toolCalls.length);
  console.log('WebSocket 连接状态:', state.wsConnected);
});

// 取消订阅
unsubscribe();
```

### 3. 连接 WebSocket

```typescript
import { toolStateWebSocketClient } from '@/tools/hooks/use-tool-state-websocket';

// 连接
toolStateWebSocketClient.connect('user-token-123');

// 订阅状态
toolStateWebSocketClient.subscribeState((state) => {
  console.log('WebSocket 状态:', state.connected);
});

// 订阅消息
toolStateWebSocketClient.subscribeMessage((artifact) => {
  console.log('收到 artifact:', artifact.id);
});

// 断开
toolStateWebSocketClient.disconnect();
```

---

## 常见问题解答

### Q1: 工具块不显示怎么办？

A: 检查以下几点：
1. 确认工具名称在 `knownTools` 中有定义
2. 检查 `artifact-transformer.ts` 中的转换逻辑
3. 确认后端返回的 artifact 格式正确

### Q2: WebSocket 连接失败？

A: 检查：
1. 后端应用是否正常启动
2. WebSocket 端点 `/ws/tool-state` 是否可访问
3. 浏览器控制台查看错误信息

### Q3: 如何添加新工具类型？

A: 在 `knownTools.ts` 中添加：

```typescript
export const knownTools: Record<string, ToolDefinition> = {
  'my_new_tool': {
    title: 'My New Tool',
    icon: ICONS.search,
    minimal: true,
    extractDescription: (opts) => {
      return 'Description of my tool';
    }
  }
};
```

---

## 演示脚本

### 场景 1：项目开发工作流（3 分钟）

```
1. 搜索项目文件
   用户：查找所有配置文件
   → glob 工具执行，显示匹配的文件

2. 读取配置文件
   用户：读取 package.json
   → file_read 工具执行，显示内容

3. 修改配置
   用户：添加新的依赖
   → file_edit 工具执行，显示 diff

4. 运行测试
   用户：npm test
   → bash 工具执行，显示输出
```

### 场景 2：任务管理工作流（2 分钟）

```
1. 创建任务列表
   用户：我需要完成以下任务...
   → todo_write 工具执行，显示任务列表

2. 标记任务完成
   随着任务进展，自动更新状态
   → 工具块显示完成进度
```

---

## 总结

本次实现的 Happy Protocol 兼容层完整支持：

✅ **9 种标准事件类型** - 与 happy-server 完全兼容
✅ **工具调用生命周期** - start → running → end 完整追踪
✅ **50+ 预定义工具** - 覆盖常见 AI 工具类型
✅ **实时状态推送** - WebSocket 实现低延迟更新
✅ **美观的 UI 组件** - 现代化设计，适合演示

**技术栈**:
- Frontend: Vue 3 + TypeScript
- Backend: Spring Boot 4 + Spring AI
- Protocol: Happy Session Protocol
- Realtime: WebSocket

**文档**: 详见 `docs/happy-protocol-compatibility.md`
