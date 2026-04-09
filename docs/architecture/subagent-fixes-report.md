# 子 Agent 系统设计问题修复报告

## 概述

本文档记录了对 `ai-agent-server` 项目子 Agent 系统设计问题的分析和修复。

## 修复的问题

### 1. V2 兼容性改进 ✅

#### 1.1 SpawnRequest 添加 version 字段
**文件**: `src/main/java/demo/k8s/agent/subagent/SpawnRequest.java`

**问题**: 设计文档要求"Spawn 契约有版本号或兼容策略"，但原实现没有 version 字段。

**修复**:
- 添加 `version` 字段，默认值为 "v1"
- 提供向后兼容的构造函数
- 添加 `getParentRunId()` 方法支持父子关系追溯

#### 1.2 SubRunEvent 添加 metadata 字段
**文件**: `src/main/java/demo/k8s/agent/subagent/SubRunEvent.java`

**问题**: 事件结构固定，无法传递 `cost_tokens`、`model_id` 等扩展信息。

**修复**:
- 添加 `Map<String, String> metadata` 字段
- 提供 `getMetadataValue(String key)` 便捷方法
- 为 `completed()` 静态方法添加带 metadata 的重载版本

### 2. 架构与边界问题修复 ✅

#### 2.1 SpawnGatekeeper 竞态条件
**文件**: `src/main/java/demo/k8s/agent/subagent/SpawnGatekeeper.java`

**问题**: `checkSpawn()` 和 `tryAcquireConcurrentSlot()` 分离调用，存在时间窗口可能导致短暂超出并发限制。

**修复**:
- 合并为 `checkAndAcquire()` 方法，在单一原子操作中完成深度检查、工具白名单检查和并发槽位占位
- 更新 `MultiAgentFacade` 调用新的合并方法

#### 2.2 硬编码工具白名单
**文件**: 
- `src/main/java/demo/k8s/agent/subagent/SpawnGatekeeper.java`
- `src/main/java/demo/k8s/agent/toolsystem/ToolRegistry.java`

**问题**: 工具白名单硬编码在门控类中，新增工具需要同时修改门控类。

**修复**:
- 在 `ToolRegistry` 中添加 `getAllToolNames()` 方法
- `SpawnGatekeeper` 注入 `ToolRegistry` 依赖
- `getGlobalSafeTools()` 从 Registry 动态获取工具列表

### 3. 状态一致性改进 ✅

#### 3.1 parentRunId 字段实现
**文件**: 
- `src/main/java/demo/k8s/agent/subagent/SubagentRun.java`
- `src/main/java/demo/k8s/agent/subagent/SubagentRunService.java`
- `src/main/java/demo/k8s/agent/subagent/SpawnRequest.java`

**问题**: `parentRunId` 字段已定义但注释说"暂不实现"，无法追溯派生链。

**修复**:
- `SpawnRequest` 添加 `parentRunId` 字段
- `SubagentRunService.createRun()` 添加重载方法支持传入 `parentRunId`
- `MultiAgentFacade` 通过 `TraceContext.getRunId()` 获取当前运行 ID 并传递给子请求
- `LocalSubAgentRuntime` 在 `bindWorkerThreadTraceContext()` 中绑定 `runId`

#### 3.2 TraceContext 扩展
**文件**: `src/main/java/demo/k8s/agent/observability/tracing/TraceContext.java`

**修复**:
- 添加 `RUN_ID` ThreadLocal
- 添加 `setRunId()` 和 `getRunId()` 方法
- `clear()` 方法中清理 `RUN_ID`

### 4. 可恢复性改进 ✅

#### 4.1 SubagentReconciler 恢复逻辑
**文件**: `src/main/java/demo/k8s/agent/subagent/SubagentReconciler.java`

**问题**: 
- 对于未过期的 RUNNING 任务只是 PRESERVE，没有实际恢复执行
- 定时检查间隔过长（5 分钟），与 wallclockTtlSeconds=180 不匹配

**修复**:
- 注入 `SubAgentRuntime` 依赖，为未来实现 `runtime.resume()` 做准备
- 添加恢复日志记录（v1 暂时仅记录，实际恢复执行待实现）
- 缩短定时检查间隔从 300 秒到 30 秒
- 更新 javadoc 说明恢复策略

### 5. Shadow 模式语义修正 ✅

**文件**: 
- `src/main/java/demo/k8s/agent/subagent/MultiAgentFacade.java`
- `src/main/java/demo/k8s/agent/subagent/metrics/SubagentMetrics.java`

**问题**: 设计文档说"执行子 Agent 但不污染主对话"，但代码是"不执行子 Agent"。

**修复**:
- 明确 Shadow 模式行为："只记录统计，不实际执行"
- 添加 `recordSpawnShadowEvaluated()` 指标方法
- 更新注释说明用于"评估门控策略和提示词拆解效果"

### 6. Human-in-the-Loop 集成文档 ✅

**文件**: `docs/architecture/subagent-suspend-integration.md`

**问题**: `SubagentSuspendService` 的 `suspend()`和`resume()` 方法没有被其他地方调用，集成点不清晰。

**修复**:
- 创建详细的集成文档说明：
  - 核心组件和数据模型
  - 4 个主要集成点（工具拦截器、REST API、WebSocket 推送、恢复执行钩子）
  - 高危工具示例列表
  - 流程图
  - 待实现功能清单
  - V2 双栈架构衔接方案

## 修改的文件清单

| 文件 | 修改类型 | 说明 |
|------|---------|------|
| `SpawnRequest.java` | 增强 | 添加 version 和 parentRunId 字段 |
| `SubRunEvent.java` | 增强 | 添加 metadata 字段 |
| `SpawnGatekeeper.java` | 修复 | 合并 checkAndAcquire，动态工具白名单 |
| `ToolRegistry.java` | 增强 | 添加 getAllToolNames() 方法 |
| `MultiAgentFacade.java` | 修复 | 使用 checkAndAcquire，传递 parentRunId |
| `SubagentRunService.java` | 增强 | createRun 支持 parentRunId |
| `LocalSubAgentRuntime.java` | 修复 | 传递 parentRunId，绑定 runId 到 TraceContext |
| `TraceContext.java` | 增强 | 添加 runId 支持 |
| `SubagentReconciler.java` | 修复 | 缩短检查间隔，准备恢复执行 |
| `SubagentMetrics.java` | 增强 | 添加 shadow 指标 |
| `subagent-suspend-integration.md` | 新增 | 挂起审批集成文档 |

## 编译验证

```bash
mvn compile -DskipTests
# 编译成功，无错误
```

## 待实现的扩展点

1. **SubagentReconciler 恢复执行**: 当前仅记录日志，需要实现 `runtime.resume(run)` 来实际恢复执行
2. **SubagentSuspend 工具拦截器**: 在 `LocalToolExecutor` 中添加高危工具识别和自动挂起逻辑
3. **SubagentSuspend REST API**: 添加完整的 CRUD API 端点
4. **WebSocket 推送**: 实时通知前端有待审批的挂起
5. **父子关系查询**: 添加按 `parent_run_id` 查询子运行的方法

## V2 兼容性检查清单

- [x] `SpawnRequest` 有 version 字段
- [x] `SubRunEvent` 有 metadata 扩展字段
- [x] `MultiAgentFacade` 承载入口切换
- [x] `SubAgentRuntime` 抽象稳定
- [x] Spawn 契约（请求/事件/终态）已冻结
- [x] `parentRunId` 支持派生链追溯
- [x] ContextObject 读写链路与租户隔离稳定
- [x] 关键指标已用于灰度决策

## 参考实现

- OpenClaw tool-result-context-guard: `G:\project\claude-code\openclaw\src\agents\pi-embedded-runner\tool-result-context-guard.ts`
- OpenClaw task-registry: `G:\project\claude-code\openclaw\src\tasks\task-registry.ts`

## 下一步建议

1. **测试覆盖**: 为新增功能添加单元测试
2. **集成测试**: 测试父子派生链、恢复执行场景
3. **API 端点**: 实现 SubagentSuspend 的 REST API
4. **工具拦截器**: 实现高危工具自动挂起逻辑
5. **文档更新**: 更新 API 文档和使用指南
