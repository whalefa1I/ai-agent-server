# 定时任务系统 API 文档

## 概述

定时任务系统允许用户创建、管理和执行定时任务。支持 Cron 表达式、固定延迟/频率等多种调度方式。

## 用户 API 端点

### 创建定时任务

```
POST /api/scheduler/tasks
Content-Type: application/json
Authorization: Bearer {token}
```

**请求体示例：**
```json
{
  "taskName": "每日数据备份",
  "taskDescription": "每天凌晨备份重要数据",
  "taskType": "CHAT",
  "cronExpression": "0 2 * * *",
  "taskPayload": {
    "message": "执行数据备份任务"
  },
  "timezone": "Asia/Shanghai",
  "enabled": true,
  "maxExecutions": -1,
  "errorHandling": "RETRY",
  "maxRetries": 3,
  "notifyOnFailure": true
}
```

### 查询任务列表

```
GET /api/scheduler/tasks?page=0&size=20&sortBy=createdAt&direction=desc
Authorization: Bearer {token}
```

### 查询任务详情

```
GET /api/scheduler/tasks/{taskId}
Authorization: Bearer {token}
```

### 更新任务

```
PUT /api/scheduler/tasks/{taskId}
Content-Type: application/json
Authorization: Bearer {token}
```

### 删除任务

```
DELETE /api/scheduler/tasks/{taskId}
Authorization: Bearer {token}
```

### 启用任务

```
POST /api/scheduler/tasks/{taskId}/enable
Authorization: Bearer {token}
```

### 暂停任务

```
POST /api/scheduler/tasks/{taskId}/pause
Authorization: Bearer {token}
```

### 恢复任务

```
POST /api/scheduler/tasks/{taskId}/resume
Authorization: Bearer {token}
```

### 取消任务

```
POST /api/scheduler/tasks/{taskId}/cancel
Authorization: Bearer {token}
```

### 试运行任务

```
POST /api/scheduler/tasks/{taskId}/dry-run
Authorization: Bearer {token}
```

### 查询任务执行历史

```
GET /api/scheduler/tasks/{taskId}/history?page=0&size=20
Authorization: Bearer {token}
```

### 查询用户执行历史

```
GET /api/scheduler/history?page=0&size=20
Authorization: Bearer {token}
```

### 查询执行日志

```
GET /api/scheduler/history/{executionId}/logs
```

### 获取用户任务统计

```
GET /api/scheduler/tasks/stats
Authorization: Bearer {token}
```

**响应示例：**
```json
{
  "userId": "user123",
  "totalTasks": 10,
  "activeTasks": 7,
  "pausedTasks": 2,
  "totalExecutions": 150,
  "successRate": 95.5
}
```

## 管理员 API 端点

### 获取所有用户任务汇总统计

```
GET /api/admin/scheduler/stats/aggregate
Authorization: Bearer {token}
```

**响应示例：**
```json
{
  "totalTasks": 500,
  "enabledTasks": 450,
  "totalExecutions": 10000,
  "tasksByStatus": {
    "ACTIVE": 400,
    "PAUSED": 50,
    "COMPLETED": 40,
    "CANCELLED": 10
  },
  "tasksByType": {
    "CHAT": 300,
    "TOOL": 150,
    "API": 50
  },
  "tasksByUser": {
    "user1": { "ACTIVE": 10, "PAUSED": 2 },
    "user2": { "ACTIVE": 15, "PAUSED": 1 }
  }
}
```

### 按用户 ID 查询任务统计

```
GET /api/admin/scheduler/stats/user/{userId}
Authorization: Bearer {token}
```

### 查询所有用户的执行历史

```
GET /api/admin/scheduler/history?page=0&size=50&userId=xxx&status=SUCCESS
Authorization: Bearer {token}
```

## 任务类型

| 类型 | 说明 |
|------|------|
| CHAT | 定时发送消息 |
| TOOL | 定时执行工具 |
| API | 定时调用 API |
| CUSTOM | 自定义任务 |

## Cron 表达式格式

支持 5 字段 Cron 表达式：

```
分 时 日 月 周
```

**示例：**
- `0 2 * * *` - 每天凌晨 2 点
- `*/5 * * * *` - 每 5 分钟
- `0 0 * * 1` - 每周一零点
- `0 0 1 * *` - 每月 1 号零点

## 错误处理策略

| 策略 | 说明 |
|------|------|
| CONTINUE | 继续执行下一次 |
| STOP | 停止任务 |
| RETRY | 重试 |

## 任务状态

| 状态 | 说明 |
|------|------|
| ACTIVE | 活跃状态 |
| PAUSED | 已暂停 |
| COMPLETED | 已完成 |
| CANCELLED | 已取消 |
| ERROR | 错误状态 |

## 执行历史状态

| 状态 | 说明 |
|------|------|
| PENDING | 等待执行 |
| RUNNING | 执行中 |
| SUCCESS | 执行成功 |
| FAILED | 执行失败 |
| CANCELLED | 已取消 |
| TIMEOUT | 超时 |
