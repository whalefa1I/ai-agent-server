# 定时任务和 Heartbeat 配额与定价方案

## 设计原则

1. **Heartbeat 和定时任务分离**：两种任务类型有不同的资源消耗模式
2. **差异化定价**：不同套餐有不同的配额限制
3. **防止资源滥用**：通过多重配额限制保护系统资源
4. **可升级性**：用户可以随时升级套餐获得更高配额

---

## 套餐设计

### 免费版 (Free)

**目标用户**: 个人开发者、测试环境

| 配额项 | 限制 |
|--------|------|
| 定时任务数量 | 5 个 |
| 并发执行数 | 1 个 |
| Heartbeat 任务 | 2 个 |
| 最小心跳间隔 | 60 秒 |
| 每日执行次数 | 50 次 |
| 每小时执行次数 | 10 次 |
| 单次执行时长 | 1 分钟 |
| Payload 大小 | 5 KB |
| Cron 精度 | 小时级 (HOUR) |
| 每日通知数 | 10 条 |
| **价格** | **¥0/月** |

**使用场景**:
- 每小时同步一次数据
- 每日发送一次提醒
- 测试定时任务功能

---

### 专业版 (Pro)

**目标用户**: 小型团队、生产环境

| 配额项 | 限制 |
|--------|------|
| 定时任务数量 | 50 个 |
| 并发执行数 | 5 个 |
| Heartbeat 任务 | 10 个 |
| 最小心跳间隔 | 10 秒 |
| 每日执行次数 | 1000 次 |
| 每小时执行次数 | 100 次 |
| 单次执行时长 | 5 分钟 |
| Payload 大小 | 10 KB |
| Cron 精度 | 分钟级 (MINUTE) |
| 每日通知数 | 200 条 |
| **价格** | **¥29/月** |

**使用场景**:
- 每分钟检查一次订单状态
- 每 10 秒心跳保活连接
- 多任务并发执行

---

### 企业版 (Enterprise)

**目标用户**: 大规模生产环境、高频率任务

| 配额项 | 限制 |
|--------|------|
| 定时任务数量 | 500 个 |
| 并发执行数 | 50 个 |
| Heartbeat 任务 | 50 个 |
| 最小心跳间隔 | 5 秒 |
| 每日执行次数 | 100,000 次 |
| 每小时执行次数 | 10,000 次 |
| 单次执行时长 | 10 分钟 |
| Payload 大小 | 100 KB |
| Cron 精度 | 秒级 (SECOND) |
| 每日通知数 | 2000 条 |
| **价格** | **¥99/月** |

**使用场景**:
- 秒级实时监控任务
- 大规模并发 Heartbeat
- 高频数据同步

---

## 配额检查点

### 创建任务时检查

1. **任务数量配额**: 检查用户当前任务数是否达到上限
2. **Cron 精度配额**: 检查 Cron 表达式是否符合套餐精度
3. **Payload 大小配额**: 检查任务内容大小

### 执行任务时检查

1. **每日执行次数**: 检查今日已执行次数
2. **每小时执行次数**: 检查当前小时已执行次数
3. **执行时长配额**: 检查任务执行时长是否超限

### Heartbeat 专有检查

1. **心跳间隔**: 检查配置间隔是否小于套餐允许的最小值
2. **Heartbeat 任务数**: 检查 Heartbeat 任务总数

---

## 超限处理策略

用户可配置超限时的行为：

| 策略 | 说明 |
|------|------|
| BLOCK (阻止) | 拒绝新任务/执行，返回错误 |
| QUEUE (排队) | 加入队列，等待配额释放 |
| NOTIFY (通知) | 仅发送通知，继续执行 |

---

## API 设计

### 用户 API

```
GET  /api/scheduler/quota/status           # 获取配额状态
GET  /api/scheduler/quota/check/create     # 检查能否创建任务
GET  /api/scheduler/quota/check/execute    # 检查能否执行任务
GET  /api/scheduler/quota/check/cron       # 检查 Cron 精度
GET  /api/scheduler/quota/check/heartbeat  # 检查心跳间隔
GET  /api/scheduler/quota/check/payload    # 检查 Payload 大小
GET  /api/scheduler/quota/plans            # 获取可用套餐
POST /api/scheduler/quota/upgrade          # 升级套餐
```

### 管理员 API

```
GET /api/admin/scheduler/quota/summary     # 所有用户配额汇总
GET /api/admin/scheduler/quota/user/{id}   # 查询指定用户配额
PUT /api/admin/scheduler/quota/user/{id}   # 修改用户配额 (特权)
GET /api/admin/scheduler/plans             # 管理套餐配置
```

---

## 配额状态响应示例

```json
{
  "userId": "user_123",
  "planId": "pro",
  "currentTasks": 12,
  "maxTasks": 50,
  "executionsToday": 150,
  "maxExecutionsPerDay": 1000,
  "executionsThisHour": 25,
  "maxExecutionsPerHour": 100,
  "notificationsToday": 30,
  "maxNotificationsPerDay": 200,
  "enabled": true,
  "availablePlans": [
    {
      "planId": "free",
      "planName": "免费版",
      "priceCents": 0
    },
    {
      "planId": "pro",
      "planName": "专业版",
      "priceCents": 2900
    },
    {
      "planId": "enterprise",
      "planName": "企业版",
      "priceCents": 9900
    }
  ],
  "tasksUsagePercent": 24.0,
  "executionsUsagePercent": 15.0
}
```

---

## 数据库表关系

```
user_subscription (用户订阅)
    ↓ (plan_id)
plan_task_quota (套餐配额配置)
    ↓ (一对一)
user_task_quota (用户任务配额)
    ↓ (一对多)
scheduled_task (定时任务)
heartbeat_task (Heartbeat 任务)
    ↓ (一对多)
task_execution_history (执行历史)
task_quota_usage_daily (每日使用统计)
```

---

## 扩展建议

1. **按需付费**: 超出配额后按执行次数计费
2. **配额包**: 单独购买额外执行次数包
3. **企业定制**: 针对大客户定制配额方案
4. **自动降级**: 欠费自动降级到低配额套餐
5. **配额预警**: 使用率达到 80% 时发送预警通知
