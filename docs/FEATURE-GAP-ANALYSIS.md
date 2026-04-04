# Claude Code 功能对比分析

## 概述

本文档详细对比了 `minimal-k8s-agent-demo` 与 Anthropic 官方 `Claude Code` 项目的功能差异，为后续开发提供方向。

---

## 功能对比矩阵

| 功能类别 | Claude Code | minimal-k8s-agent-demo | 差距等级 |
|---------|-------------|------------------------|----------|
| **核心工具** | | | |
| BashTool | ✅ 完整实现 | ⚠️ K8s 沙盒简化版 | 🔴 高 |
| FileEditTool | ✅ 差异补丁编辑 | ❌ 缺失 | 🔴 高 |
| FileReadTool | ✅ 完整实现 | ❌ 缺失 | 🔴 高 |
| FileWriteTool | ✅ 完整实现 | ❌ 缺失 | 🔴 高 |
| GlobTool | ✅ 完整实现 | ❌ 缺失 | 🟡 中 |
| GrepTool | ✅ 完整实现 | ❌ 缺失 | 🟡 中 |
| WebFetchTool | ✅ 完整实现 | ❌ 缺失 | 🟡 中 |
| WebSearchTool | ✅ 完整实现 | ❌ 缺失 | 🟡 中 |
| NotebookEditTool | ✅ 完整实现 | ❌ 缺失 | ⚪ 低 |
| **Agent 工具** | | | |
| AgentTool | ✅ 多类型子 Agent | ⚠️ 基础 Task 支持 | 🟡 中 |
| TaskCreateTool | ✅ 完整任务管理 | ⚠️ 基础创建 | 🟡 中 |
| TaskGetTool | ✅ 任务详情 | ❌ 缺失 | 🟡 中 |
| TaskListTool | ✅ 任务列表 | ❌ 缺失 | 🟡 中 |
| TaskUpdateTool | ✅ 任务更新 | ❌ 缺失 | 🟡 中 |
| TaskStopTool | ✅ 停止任务 | ⚠️ 基础实现 | 🟢 低 |
| SendMessageTool | ✅ 任务通信 | ❌ 缺失 | 🟡 中 |
| **MCP 支持** | | | |
| MCPTool | ✅ 完整 MCP 支持 | ⚠️ 基础支持 | 🟡 中 |
| ListMcpResourcesTool | ✅ 资源列表 | ❌ 缺失 | 🟢 低 |
| ReadMcpResourceTool | ✅ 资源读取 | ❌ 缺失 | 🟢 低 |
| **高级功能** | | | |
| LSPTool | ✅ LSP 集成 | ❌ 缺失 | 🔴 高 |
| TodoWriteTool | ✅ 待办管理 | ❌ 缺失 | 🟢 低 |
| ConfigTool | ✅ 配置管理 | ❌ 缺失 | 🟢 低 |
| BriefTool | ✅ 简报生成 | ❌ 缺失 | ⚪ 低 |
| Cron 工具 | ✅ 计划任务 | ❌ 缺失 | ⚪ 低 |
| **权限系统** | | | |
| 权限模式 | ✅ 5+ 模式 | ✅ 3 模式 | 🟢 低 |
| 规则来源 | ✅ 6 种来源 | ❌ 单一来源 | 🟡 中 |
| 文件级权限 | ✅ 细粒度规则 | ❌ 缺失 | 🟡 中 |
| 会话级授权 | ✅ 完整实现 | ✅ 已实现 | ✅ 对齐 |
| 持久化授权 | ✅ 文件存储 | ✅ 已实现 | ✅ 对齐 |
| **上下文管理** | | | |
| Compaction | ✅ 微压缩 + 全压缩 | ⚠️ 基础实现 | 🟡 中 |
| Token 追踪 | ✅ 完整统计 | ✅ 已实现 | ✅ 对齐 |
| 消息历史 | ✅ 完整管理 | ✅ 已实现 | ✅ 对齐 |
| 文件快照 | ✅ 完整实现 | ✅ 已实现 | ✅ 对齐 |
| **多 Agent** | | | |
| 子 Agent 类型 | ✅ 5+ 类型 | ⚠️ 基础类型 | 🟡 中 |
| 任务状态机 | ✅ 完整状态 | ✅ 已实现 | ✅ 对齐 |
| Worker 隔离 | ✅ 完整隔离 | ⚠️ 基础隔离 | 🟡 中 |
| **UI/UX** | | | |
| TUI 终端 | ✅ Ink 框架 | ✅ JLine3 | ✅ 对齐 |
| WebSocket | ✅ 内部通信 | ✅ 已实现 | ✅ 对齐 |
| 多用户支持 | ❌ 单用户 | ✅ 已实现 | 🟢 超越 |
| 流式输出 | ✅ 逐 token | ✅ 已实现 | ✅ 对齐 |
| **可观测性** | | | |
| Token 指标 | ✅ 完整追踪 | ✅ 已实现 | ✅ 对齐 |
| 模型指标 | ✅ 延迟/成功 | ✅ 已实现 | ✅ 对齐 |
| 工具指标 | ✅ 执行统计 | ✅ 已实现 | ✅ 对齐 |
| Prometheus | ❌ 内部使用 | ✅ 已导出 | 🟢 超越 |

---

## 关键功能差距详解

### 🔴 高优先级差距

#### 1. 文件编辑工具 (FileEditTool)

**Claude Code 实现:**
- 基于差异补丁（diff patch）的精准编辑
- 支持多轮查找替换
- 文件意外修改检测
- 引号风格保持
- 行 ending 类型保持
- Git diff 集成
- LSP 诊断清除
- 团队内存同步检查

**缺失影响:**
- 无法进行精准文件修改
- 大文件编辑效率低
- 无法追踪代码变更

**建议实现:**
```java
// 伪代码示例
public record FileEditInput(
    String filePath,
    String oldText,      // 要替换的原文
    String newText,      // 替换为的新文
    Integer occurrences  // 匹配次数
) {}

public FileEditOutput applyDiff(FileEditInput input) {
    // 1. 读取文件
    // 2. 查找匹配位置
    // 3. 应用补丁
    // 4. 验证变更
    // 5. 保存并返回 diff
}
```

---

#### 2. Bash 工具增强

**Claude Code 实现:**
- PowerShell 独立支持
- 危险命令检测
- 工作目录管理
- 输出流式处理
- 进程组管理
- 超时控制
- 环境变量继承

**当前差距:**
- K8s 沙盒仅限简单命令
- 缺少本地执行能力
- 无进程管理

---

#### 3. LSP 集成

**Claude Code 实现:**
- 多语言服务器管理
- 诊断追踪
- 代码补全
- 跳转定义
- 查找引用
- 重命名符号

**缺失影响:**
- 无法理解代码语义
- 无法提供智能建议
- 代码修改风险高

---

### 🟡 中优先级差距

#### 4. MCP 完整支持

**Claude Code 实现:**
- MCP 服务器连接管理
- 资源发现与枚举
- 资源内容读取
- 工具调用代理

**建议实现:**
- 扩展 `McpToolProvider`
- 添加资源发现 UI
- 支持资源内容缓存

---

#### 5. 权限系统增强

**Claude Code 实现:**
```typescript
type PermissionRuleSource =
  | 'userSettings'      // 用户设置
  | 'projectSettings'   // 项目设置
  | 'localSettings'     // 本地设置
  | 'flagSettings'      // 标志设置
  | 'policySettings'    // 策略设置
  | 'cliArg'            // CLI 参数
  | 'command'           // 命令
  | 'session'           // 会话

type PermissionRule = {
  source: PermissionRuleSource
  ruleBehavior: 'allow' | 'deny' | 'ask'
  ruleValue: {
    toolName: string
    ruleContent?: string  // 支持通配符规则
  }
}
```

**当前差距:**
- 仅支持会话级授权
- 无项目级配置
- 无 CLI 参数覆盖
- 无通配符规则

---

#### 6. Web 搜索和抓取

**Claude Code 实现:**
- Brave Search 集成
- 搜索结果分页
- 网页内容提取
- 链接追踪

**建议实现:**
- 集成搜索引擎 API
- 添加 HTML 解析
- 支持链接跟随

---

### 🟢 低优先级差距

#### 7. TodoWriteTool

**功能:**
- 待办事项创建
- 进度追踪
- 子任务分解

**实现复杂度:** 低

---

#### 8. ConfigTool

**功能:**
- 读取配置
- 修改配置
- 配置验证

---

#### 9. BriefTool

**功能:**
- 会话摘要生成
- 上下文压缩
- 交接文档

---

## 架构差异

### Claude Code 架构特点

```
┌─────────────────────────────────────────────────────────┐
│                    Claude Code                          │
├─────────────────────────────────────────────────────────┤
│  UI Layer (Ink/React)                                   │
│  ├── REPL Screen                                        │
│  ├── Messages                                           │
│  └── Permission Dialogs                                 │
├─────────────────────────────────────────────────────────┤
│  Query Engine (query.ts)                                │
│  ├── Message Management                                 │
│  ├── Compaction Pipeline                                │
│  ├── Token Budget                                       │
│  └── Hook System                                        │
├─────────────────────────────────────────────────────────┤
│  Tool Orchestration                                     │
│  ├── 30+ Built-in Tools                                 │
│  ├── MCP Integration                                    │
│  └── Skill System                                       │
├─────────────────────────────────────────────────────────┤
│  Services                                               │
│  ├── API (Anthropic/AWS/Vertex)                         │
│  ├── LSP                                                │
│  ├── Git                                                │
│  └── File Watcher                                       │
└─────────────────────────────────────────────────────────┘
```

### minimal-k8s-agent-demo 架构

```
┌─────────────────────────────────────────────────────────┐
│              minimal-k8s-agent-demo                     │
├─────────────────────────────────────────────────────────┤
│  UI Layer                                               │
│  ├── TUI Client (JLine3)                                │
│  └── WebSocket                                          │
├─────────────────────────────────────────────────────────┤
│  Query Loop (EnhancedAgenticQueryLoop)                  │
│  ├── Permission Check                                   │
│  ├── Token Tracking                                     │
│  └── Compaction                                         │
├─────────────────────────────────────────────────────────┤
│  Tools                                                  │
│  ├── k8s_sandbox_run                                    │
│  ├── SkillsTool                                         │
│  └── TaskTool                                           │
├─────────────────────────────────────────────────────────┤
│  Services                                               │
│  ├── Spring AI                                          │
│  ├── Kubernetes                                         │
│  └── Observability                                      │
└─────────────────────────────────────────────────────────┘
```

### 核心架构差距

| 方面 | Claude Code | minimal-k8s-agent-demo |
|------|-------------|------------------------|
| 工具数量 | 30+ | ~5 |
| Hook 系统 | ✅ 完整 | ❌ 缺失 |
| 技能系统 | ✅ 完整 | ⚠️ 基础 |
| Git 集成 | ✅ 深度 | ❌ 无 |
| 文件监听 | ✅ 实时 | ❌ 无 |
| 诊断追踪 | ✅ LSP | ❌ 无 |

---

## 开发路线图

### 第一阶段：核心工具补齐（2-3 周）

1. **FileEditTool** - 差异补丁编辑
2. **FileReadTool** - 文件读取
3. **FileWriteTool** - 文件写入
4. **GrepTool** - 内容搜索
5. **GlobTool** - 文件匹配

### 第二阶段：权限系统增强（1 周）

1. 项目级权限配置
2. 通配符规则支持
3. CLI 参数覆盖
4. 权限规则来源追踪

### 第三阶段：Agent 能力增强（2 周）

1. 完整 Task 管理（Get/List/Update）
2. SendMessage 工具
3. 多 Agent 类型支持
4. Worker 专属工具集

### 第四阶段：高级集成（3-4 周）

1. MCP 资源发现与读取
2. Web 搜索集成
3. LSP 基础支持
4. Git 深度集成

### 第五阶段：UX 优化（持续）

1. TodoWrite 工具
2. ConfigTool
3. BriefTool
4. TUI 命令增强

---

## 独特优势

### minimal-k8s-agent-demo 超越 Claude Code 的功能

1. **多用户支持**
   - WebSocket 多会话隔离
   - 独立 TUI 客户端部署
   - 服务器集中式管理

2. **K8s 沙盒**
   - 隔离的命令执行环境
   - 资源限制
   - 安全性更高

3. **Prometheus 指标导出**
   - 原生监控集成
   - Grafana 仪表板
   - 告警支持

4. **Spring 生态系统**
   - 企业级集成能力
   - 成熟的依赖注入
   - 完善的测试框架

---

## 总结

### 当前实现状态

- **核心功能**: ~40% 对齐
- **工具系统**: ~20% 对齐
- **权限系统**: ~60% 对齐
- **可观测性**: ~90% 对齐
- **多用户**: 超越

### 优先补齐

1. FileEditTool（高）
2. BashTool 增强（高）
3. LSP 集成（高）
4. 权限规则系统（中）
5. Task 管理工具（中）

### 预计工作量

- **最小可用**: 2-3 周（补齐核心工具）
- **功能完整**: 8-10 周（全部补齐）
- **差异化**: 持续（发挥 K8s 优势）
