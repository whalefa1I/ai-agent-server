# SubagentSuspend 集成点说明

## 概述

`SubagentSuspendService` 提供了 Human-in-the-Loop（人在循环）的挂起/审批能力，允许子 agent 在执行高危操作前暂停并等待用户审批。

## 核心组件

### 1. 数据模型

- `SubagentSuspend` - 挂起审批记录实体
  - `run_id`: 关联的子 agent 运行 ID
  - `session_id`: 会话 ID（用于权限隔离）
  - `tenant_id`: 租户 ID（用于多租户隔离）
  - `status`: 挂起状态（PENDING/APPROVED/REJECTED/CANCELLED）
  - `reason`: 挂起原因
  - `approval_required`: 需要审批的具体内容
  - `approval_result`: 审批结果（APPROVED/REJECTED）
  - `approver_id`: 审批人 ID
  - `approval_comment`: 审批意见

### 2. 服务方法

#### `suspend(SuspendRequest request)`
挂起正在运行的子 agent。

**调用时机**:
- 当子 agent 执行高危工具（如支付、生产库写、破坏性命令）前
- 通过工具执行器的拦截器自动触发
- 或由 agent 主动调用 `suspend` 工具

**前置条件**:
- 子 agent 运行记录必须存在
- 调用者必须有该会话的访问权限

**后置条件**:
- `SubagentRun` 状态变为 `SUSPENDED`
- `SubagentSuspend` 记录创建，状态为 `PENDING`

#### `resume(ResumeRequest request)`
恢复被挂起的子 agent。

**调用时机**:
- 用户通过前端界面审批后
- 调用 REST API: `POST /api/subagent/{runId}/resume`

**前置条件**:
- 挂起记录必须存在且状态为 `PENDING`
- 调用者必须有审批权限

**后置条件**:
- `SubagentSuspend` 状态变为 `APPROVED` 或 `REJECTED`
- `SubagentRun` 状态变为 `RUNNING`（批准）或 `FAILED`（拒绝）

## 集成点

### 集成点 1: 工具执行拦截器

在 `LocalToolExecutor` 中添加高危工具拦截逻辑：

```java
// 伪代码示例
public ToolResult execute(ToolDef tool, Map<String, Object> input) {
    if (isHighRiskTool(tool.name()) && requiresApproval(tool, input)) {
        // 触发挂起
        SuspendRequest request = new SuspendRequest(
            runId,
            sessionId,
            "高危工具执行需要审批",
            "执行 " + tool.name() + " 将影响生产环境"
        );
        suspendService.suspend(request);
        return ToolResult.suspended("等待审批");
    }
    // 正常执行
    ...
}
```

### 集成点 2: REST API 端点

需要添加以下 API 端点供前端调用：

```java
@RestController
@RequestMapping("/api/subagent")
public class SubagentSuspendController {

    @PostMapping("/{runId}/suspend")
    public SuspendResult suspend(@PathVariable String runId, @RequestBody SuspendRequest request) {
        return suspendService.suspend(request);
    }

    @PostMapping("/{runId}/resume")
    public ResumeResult resume(@PathVariable String runId, @RequestBody ResumeRequest request) {
        return suspendService.resume(request);
    }

    @GetMapping("/{runId}/suspend")
    public SubagentSuspend getSuspendStatus(@PathVariable String runId) {
        return suspendService.getSuspendRecord(runId);
    }

    @GetMapping("/suspensions/pending")
    public List<SubagentSuspend> getPendingSuspensions() {
        return suspendService.getAllPendingSuspensions();
    }
}
```

### 集成点 3: WebSocket 推送

当有 pending 的审批时，通过 WebSocket 推送给前端：

```java
@Component
public class SuspendNotificationService {

    @EventListener
    public void onSuspendCreated(SuspendEvent event) {
        // 推送给前端
        websocketTemplate.convertAndSend("/topic/suspensions", event);
    }
}
```

### 集成点 4: 恢复执行钩子

当前 `resume()` 方法只更新数据库状态，没有实际恢复执行。需要添加恢复执行钩子：

```java
@Transactional
public ResumeResult resume(ResumeRequest request) {
    // ... 现有逻辑 ...

    if ("APPROVED".equals(request.getApprovalResult())) {
        runService.updateStatus(request.getRunId(), SubagentRun.RunStatus.RUNNING, "Resumed after approval");

        // TODO: 恢复执行
        // 方案 1: 通过 SubagentReconciler 恢复
        // 方案 2: 直接调用 runtime.resume(request.getRunId())
        // runtime.resume(request.getRunId());
    }
    ...
}
```

## 高危工具示例

以下工具类型应触发挂起审批：

| 工具类型 | 示例 | 审批级别 |
|---------|------|---------|
| 支付相关 | 发起支付、退款 | 必须审批 |
| 数据库写 | DELETE、UPDATE 生产库 | 必须审批 |
| 破坏性命令 | `rm -rf`、`git reset --hard` | 必须审批 |
| 外部 API 写 | 发送短信、邮件 | 建议审批 |
| 文件写 | 修改配置文件 | 可选审批 |

## 流程图

```
用户请求
    │
    ▼
子 Agent 执行
    │
    ├─── 遇到高危工具 ───> 调用 suspend() ───> 状态变为 SUSPENDED
    │                                              │
    │                                              ▼
    │                                      前端显示审批卡片
    │                                              │
    │                                              ▼
    │                                      用户 Approve/Reject
    │                                              │
    │                                              ▼
    │                                      调用 resume()
    │                                              │
    │         ┌────────────────────────────────────┤
    │         │                                    │
    │         ▼                                    ▼
    │    APPROVED ───────────> 状态变为 RUNNING ──> 继续执行
    │
    │    REJECTED ───────────> 状态变为 FAILED ───> 终止
    │
    ▼
正常完成
```

## 待实现功能

1. **工具拦截器**: 在 `LocalToolExecutor` 中添加高危工具识别和自动挂起逻辑
2. **REST API 端点**: 添加完整的 CRUD API
3. **WebSocket 推送**: 实时通知前端有待审批的挂起
4. **恢复执行**: 实现 `resume()` 后的实际执行恢复
5. **超时清理**: 长时间未审批的挂起自动取消（如 24 小时后）
6. **审批历史**: 记录所有审批操作日志用于审计

## 安全考虑

1. **权限校验**: 只有授权用户（如租户管理员）可以审批
2. **会话隔离**: 只能审批自己会话内的挂起
3. **审计日志**: 所有审批操作必须记录日志
4. **最小权限**: 挂起的子 agent 只应能执行必要的后续操作

## 与 V2 双栈架构的衔接

在 V2 双栈架构中，挂起/审批流程应扩展为：

1. **Python 数据面**: 识别需要审批的工具调用，发送 `suspend` 事件给 Java 控制面
2. **Java 控制面**: 接收 `suspend` 事件，创建审批记录，推送前端
3. **审批回调**: 用户审批后，Java 控制面发送 `resume` 事件给 Python 数据面
4. **Python 数据面**: 接收 `resume` 事件，继续执行

此设计保持与当前单体内实现的向后兼容性。
