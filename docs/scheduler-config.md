# 定时任务配置示例

## application.yml 配置

```yaml
# 定时任务配置
scheduler:
  thread-pool:
    size: 10  # 调度线程池大小

# 可选：工作区目录（用于加载工作区 Hook）
demo:
  hooks:
    workspaceDir: ${WORKSPACE_DIR:}
```

## 环境变量配置

```bash
# Railway 环境变量
SCHEDULER_THREAD_POOL_SIZE=10
```

## Cron 表达式示例

```
# 每小时执行一次
0 * * * *

# 每 15 分钟执行一次
*/15 * * * *

# 每天早上 9 点执行
0 9 * * *

# 每周一上午 9 点执行
0 9 * * 1

# 每月 1 号凌晨执行
0 0 1 * *

# 工作日每天早上 8 点半执行
30 8 * * 1-5
```

## 任务 Payload 示例

### CHAT 类型任务
```json
{
  "taskType": "CHAT",
  "taskPayload": {
    "message": "早上好！这是您的每日提醒。",
    "sessionId": "session-123"
  }
}
```

### TOOL 类型任务
```json
{
  "taskType": "TOOL",
  "taskPayload": {
    "tool_name": "bash",
    "command": "echo '执行定时任务'"
  }
}
```

### API 类型任务
```json
{
  "taskType": "API",
  "taskPayload": {
    "endpoint": "https://api.example.com/webhook",
    "method": "POST",
    "body": {
      "event": "scheduled_task"
    }
  }
}
```

## 数据库表结构

### scheduled_task 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(64) | 主键 |
| user_id | VARCHAR(64) | 用户 ID |
| session_id | VARCHAR(64) | 会话 ID |
| task_name | VARCHAR(128) | 任务名称 |
| task_description | VARCHAR(512) | 任务描述 |
| task_type | VARCHAR(32) | 任务类型 (CHAT/TOOL/API/CUSTOM) |
| cron_expression | VARCHAR(64) | Cron 表达式 |
| fixed_delay_ms | BIGINT | 固定延迟 (毫秒) |
| fixed_rate_ms | BIGINT | 固定频率 (毫秒) |
| task_payload | JSON | 任务内容 |
| timezone | VARCHAR(64) | 时区 |
| start_at | TIMESTAMP | 开始时间 |
| end_at | TIMESTAMP | 结束时间 |
| max_executions | INT | 最大执行次数 |
| execution_count | INT | 已执行次数 |
| concurrent_execution | BOOLEAN | 是否允许并发执行 |
| enabled | BOOLEAN | 是否启用 |
| status | VARCHAR(16) | 状态 |
| error_handling | VARCHAR(32) | 错误处理策略 |
| max_retries | INT | 最大重试次数 |
| retry_delay_ms | INT | 重试延迟 (毫秒) |
| notify_on_success | BOOLEAN | 成功时通知 |
| notify_on_failure | BOOLEAN | 失败时通知 |
| notification_channels | JSON | 通知渠道 |
| metadata | JSON | 元数据 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |
| created_by | VARCHAR(64) | 创建者 |

### task_execution_history 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(64) | 主键 |
| task_id | VARCHAR(64) | 任务 ID |
| user_id | VARCHAR(64) | 用户 ID |
| scheduled_time | TIMESTAMP | 计划执行时间 |
| actual_start_time | TIMESTAMP | 实际开始时间 |
| actual_end_time | TIMESTAMP | 实际结束时间 |
| status | VARCHAR(32) | 执行状态 |
| error_message | TEXT | 错误消息 |
| error_stack_trace | TEXT | 错误堆栈 |
| result | JSON | 执行结果 |
| duration_ms | BIGINT | 执行时长 (毫秒) |
| is_retry | BOOLEAN | 是否重试 |
| retry_count | INT | 重试次数 |
| retry_of_id | VARCHAR(64) | 原执行记录 ID |
| execution_context | JSON | 执行上下文 |
| created_at | TIMESTAMP | 创建时间 |

### task_execution_log 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(64) | 主键 |
| execution_id | VARCHAR(64) | 执行 ID |
| task_id | VARCHAR(64) | 任务 ID |
| user_id | VARCHAR(64) | 用户 ID |
| log_level | VARCHAR(16) | 日志级别 |
| log_message | TEXT | 日志消息 |
| log_data | JSON | 日志数据 |
| logged_at | TIMESTAMP | 记录时间 |
