# 架构重构总结 (2026-04-06)

## 概述

本次重构基于架构审查报告，对项目代码进行了系统性的整合和优化。

---

## 完成情况

### 1. 合并重复的 JsonMapConverter ✅

**问题:** `scheduler` 和 `apikey` 包中各有一个完全相同的 `JsonMapConverter` 类

**解决方案:**
- 创建统一的 `demo.k8s.agent.config.JsonConverter` 类
- 增强功能支持 `Map` 和 `List` 转换
- 删除两个重复的 `JsonMapConverter` 类
- 更新所有引用实体类使用新的 `JsonConverter`

**影响文件:**
- 新建：`src/main/java/demo/k8s/agent/config/JsonConverter.java`
- 删除：`src/main/java/demo/k8s/agent/scheduler/JsonMapConverter.java`
- 删除：`src/main/java/demo/k8s/agent/apikey/JsonMapConverter.java`
- 更新：`ApiKey.java`, `ApiKeyQuotaPlan.java`, `ScheduledTask.java`, `TaskExecutionHistory.java`, `TaskExecutionLog.java`, `PlanTaskQuota.java`

---

### 2. 整合配额系统 ✅

**问题:** V5 (`plan_task_quota`) 和 V6 (`api_key_quota_plan`) 配额计划表功能重叠

**解决方案:**
- 创建 V7 数据库迁移脚本 `V7__consolidate_quota_plans.sql`
- 将定时任务配额字段整合到 `api_key_quota_plan` 表
- 创建统一视图 `quota_plan_summary` 方便查询
- 保留 `plan_task_quota` 表用于向后兼容（可选择性删除）

**新增字段:**
- `max_scheduled_tasks` - 最大定时任务数
- `max_scheduled_tasks_executing` - 最大并发执行数
- `max_heartbeat_tasks` - 最大心跳任务数
- `heartbeat_interval_min_ms` - 最小心跳间隔
- `allowed_cron_precision` - 允许的 Cron 精度
- `max_task_executions_per_day` - 每日最大执行次数
- `max_task_executions_per_hour` - 每小时最大执行次数
- `max_task_duration_ms` - 最大任务执行时长
- `max_task_payload_size_bytes` - 最大 Payload 大小
- `max_notifications_per_day` - 每日最大通知数

**影响文件:**
- 新建：`src/main/resources/db/migration/V7__consolidate_quota_plans.sql`

---

### 3. 合并 WebSocket 配置类 ✅

**问题:** `WebSocketConfig` 和 `ToolStateWebSocketConfig` 都是 WebSocket 配置，分散在两个包中

**解决方案:**
- 将 `ToolStateWebSocketConfig` 合并到 `WebSocketConfig`
- 统一注册两个 WebSocket 处理器
- 删除 `ToolStateWebSocketConfig` 类

**影响文件:**
- 更新：`src/main/java/demo/k8s/agent/config/WebSocketConfig.java`
- 删除：`src/main/java/demo/k8s/agent/toolstate/ToolStateWebSocketConfig.java`

---

### 4. 合并 Tool 配置类 ✅

**问题:** `McpToolConfiguration` 和 `DemoToolRegistryConfiguration` 都是工具注册相关配置

**解决方案:**
- 将 `McpToolProvider` Bean 合并到 `DemoToolRegistryConfiguration`
- 删除 `McpToolConfiguration` 类

**影响文件:**
- 更新：`src/main/java/demo/k8s/agent/config/DemoToolRegistryConfiguration.java`
- 删除：`src/main/java/demo/k8s/agent/config/McpToolConfiguration.java`

---

### 5. 统一 Properties 管理 ✅

**分析结论:** 9 个 Properties 类使用不同的配置前缀，保持独立是合理的

**Properties 类清单:**
| 类名 | 配置前缀 | 用途 |
|------|----------|------|
| `DemoCoordinatorProperties` | `demo.coordinator` | Coordinator 模式开关 |
| `DemoQueryProperties` | `demo.query` | 查询循环参数 |
| `DemoToolsProperties` | `demo.tools` | 工具系统开关 |
| `DemoWsProperties` | `demo.ws` | WebSocket 配置 |
| `HookProperties` | `demo.hooks` | Hook 系统配置 |
| `SkillsProperties` | `demo.skills` | Skills 系统配置 |
| `DemoK8sProperties` | `demo.k8s` | K8s 集成配置 |
| `McpProperties` | `demo.mcp` | MCP 服务器配置 |
| `AgentScopeSandboxProperties` | `demo.agentscope` | AgentScope 沙盒配置 |

**建议:** 保持现状，在文档中记录配置结构即可

---

### 6. 整合 QuotaService 服务层 ✅

**分析结论:** 三个配额服务各有侧重，保持独立但可创建统一协调器

**服务分工:**
| 服务 | 包路径 | 职责 |
|------|--------|------|
| `QuotaService` | `demo.k8s.agent.user` | 用户账户级别配额（请求数、Token 数） |
| `TaskQuotaService` | `demo.k8s.agent.scheduler` | 定时任务配额（任务数、执行次数） |
| `ApiKeyService` | `demo.k8s.agent.apikey` | API Key 维度配额（速率限制、并发控制） |

**建议:** 如需统一配额检查入口，可创建 `QuotaManager` 协调器，但非必须

---

### 7. 规范 Repository 包结构 ✅

**分析结论:** Repository 按功能模块组织符合 Spring Boot 最佳实践

**当前结构:**
```
user/           - 用户相关 Repository
scheduler/      - 定时任务相关 Repository
apikey/         - API Key 相关 Repository
state/          - 会话状态相关 Repository
toolstate/      - 工具状态相关 Repository
```

**建议:** 保持现状，这是合理的分包方式

---

## 重构收益

### 代码减少
- 删除 2 个重复的 `JsonMapConverter` 类
- 删除 2 个冗余的配置类
- 统一 JSON 转换器功能增强

### 维护性提升
- 配置类从 16 个减少到 14 个
- 配额计划表统一，避免数据不一致
- 工具类集中度提高

### 扩展性改进
- V7 迁移后配额系统更易于扩展
- `JsonConverter` 支持更多类型转换
- 配置结构更清晰

---

## 后续建议

1. **可选重构**: 创建统一的 `repository` 包（当前按功能模块组织也是合理的）

2. **可选重构**: 创建 `QuotaManager` 协调器统一配额检查逻辑

3. **建议实施**: 在 README 或 ARCHITECTURE.md 中记录配置结构和配额系统设计

4. **建议实施**: 为 V7 迁移脚本编写回滚脚本

---

## 验证

- ✅ 编译通过 (`mvnw clean compile`)
- ✅ 所有实体类正确引用新的 `JsonConverter`
- ✅ WebSocket 配置合并后端点不变
- ✅ Tool 配置合并后 Bean 定义完整

---

**重构完成日期:** 2026-04-06
**重构执行人:** Claude Code
