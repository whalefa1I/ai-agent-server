# API Key 权限管理系统 - 设计文档

## 概述

基于配额模式的 API Key 权限管理系统，对核心收益点进行权限收拢，保留管理员权限控制整体成本和并发，防止恶意调用。

## 设计原则

1. **一个用户一个 API Key**: 每个用户可拥有多个 API Key，每个 Key 有独立的配额和权限
2. **核心收益点收拢**: AI 模型调用、远程工具执行、向量搜索等核心功能单独计量计费
3. **多层防护**: 速率限制 + 并发控制 + 配额检查 + IP 限制
4. **管理员特权**: 管理员可强制禁用/启用、配额覆盖、服务熔断

---

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                      HTTP Request                            │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   ApiKeyAuthFilter                           │
│  - API Key 验证                                              │
│  - 速率限制检查                                              │
│  - 并发控制检查                                              │
│  - 端点权限检查                                              │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                  PremiumFeatureService                       │
│  - 核心功能权限检查                                          │
│  - 计量计费                                                  │
│  - 配额扣减                                                  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Target Controller                         │
└─────────────────────────────────────────────────────────────┘
```

---

## 数据库表结构

### api_key (API Key 主表)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(64) | 主键 |
| user_id | VARCHAR(64) | 用户 ID |
| key_hash | VARCHAR(256) | BCrypt 加密的 Key |
| key_prefix | VARCHAR(16) | Key 前缀 (用于展示) |
| key_type | VARCHAR(32) | USER/SERVICE/ADMIN |
| key_scope | VARCHAR(32) | PERSONAL/ORGANIZATION/GLOBAL |
| enabled | BOOLEAN | 是否启用 |
| status | VARCHAR(16) | ACTIVE/REVOKED/EXPIRED/SUSPENDED |
| quota_plan_id | VARCHAR(32) | 配额计划 ID |
| rate_limit_* | INT | 速率限制 |
| max_concurrent_requests | INT | 最大并发请求数 |
| permissions | JSON | 权限列表 |
| allowed/blocked_endpoints | JSON | 端点白/黑名单 |
| allowed/blocked_ip_ranges | JSON | IP 白/黑名单 |

### api_key_quota_plan (配额计划表)
| 字段 | 类型 | 说明 |
|------|------|------|
| plan_id | VARCHAR(32) | 主键 |
| plan_name | VARCHAR(64) | 计划名称 |
| requests_per_day | INT | 每日请求限制 |
| tokens_per_day | BIGINT | 每日 Token 限制 |
| rate_limit_* | INT | 速率限制 |
| max_concurrent_requests | INT | 最大并发 |
| premium_features | JSON | 高级功能权限 |
| price_cents | INT | 价格 (分) |

### api_key_usage_log (使用日志表)
记录每次 API 调用的详细信息。

### rate_limit_bucket (速率限制桶表)
用于分布式限流。

### user_premium_usage (核心功能使用统计表)
记录用户对核心收益点的使用情况。

### admin_quota_override (管理员配额覆盖表)
管理员针对特定用户的特殊配额设置。

### service_circuit_breaker (服务熔断表)
保护系统免受恶意攻击。

### suspicious_activity (可疑活动表)
记录疑似恶意行为的 Key。

---

## 核心收益点

| 功能 ID | 功能名称 | 计费方式 | 单价 | 默认限制 |
|---------|----------|----------|------|----------|
| ai_model_call | AI 模型调用 | PER_TOKEN | 1 分/token | 10,000 |
| remote_tool_execution | 远程工具执行 | PER_REQUEST | 10 分/次 | 100 |
| vector_search | 向量搜索 | PER_REQUEST | 5 分/次 | 100 |
| scheduled_task | 定时任务 | PER_EXECUTION | 1 分/次 | 100 |
| heartbeat_service | 心跳服务 | PER_MINUTE | 1 分/分钟 | 60 |
| file_storage | 文件存储 | PER_GB_MONTH | 500 分/GB·月 | 1 GB |

---

## API 端点

### 用户 API

```
POST   /api/apikeys                        # 创建 API Key
GET    /api/apikeys                        # 获取用户所有 Key
GET    /api/apikeys/{keyId}                # 获取 Key 详情
POST   /api/apikeys/{keyId}/revoke         # 吊销 Key
POST   /api/apikeys/{keyId}/restore        # 恢复 Key
POST   /api/apikeys/{keyId}/upgrade        # 升级配额计划
GET    /api/apikeys/plans                  # 获取配额计划列表
GET    /api/apikeys/premium-features       # 获取核心功能列表
GET    /api/apikeys/premium-features/check # 检查功能权限
```

### 管理员 API

```
GET    /api/admin/apikey/stats/summary     # 系统整体统计
GET    /api/admin/apikey/high-usage        # 高使用量 Key (防滥用)
GET    /api/admin/apikey/expiring          # 即将过期的 Key
POST   /api/admin/apikey/{keyId}/disable   # 强制禁用 Key
POST   /api/admin/apikey/{keyId}/enable    # 强制启用 Key
GET    /api/admin/apikey/suspicious        # 可疑活动列表
POST   /api/admin/apikey/suspicious/{id}/resolve  # 处理可疑活动
GET    /api/admin/apikey/premium/stats     # 核心功能使用统计
POST   /api/admin/apikey/quota-override    # 管理员配额覆盖
GET    /api/admin/apikey/circuit-breaker   # 熔断状态
POST   /api/admin/apikey/circuit-breaker/trip  # 手动触发熔断
```

---

## 安全防护机制

### 1. 速率限制 (多层)
- 每秒限制 (防突发流量)
- 每分钟限制
- 每小时限制
- 每日限制

### 2. 并发控制
- 限制每个 Key 的最大并发请求数
- 防止资源耗尽攻击

### 3. IP 限制
- IP 白名单：只允许特定 IP 访问
- IP 黑名单：阻止恶意 IP

### 4. 端点权限
- 端点白名单：只允许访问特定端点
- 端点黑名单：阻止访问特定端点

### 5. 配额检查
- 每日请求配额
- 每日 Token 配额
- 核心功能配额

### 6. 服务熔断
- 失败率超阈值自动熔断
- 慢调用率超阈值自动熔断
- 管理员手动熔断

### 7. 可疑行为检测
- 速率限制频繁触发
- 异常时间访问模式
- 异常大量请求

---

## 使用示例

### 创建 API Key

```bash
curl -X POST http://localhost:8080/api/apikeys \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "keyName": "生产环境 Key",
    "keyType": "USER",
    "quotaPlanId": "developer",
    "expiresAt": "2027-12-31T23:59:59Z"
  }'
```

响应:
```json
{
  "id": "key-123",
  "keyPrefix": "sk-abc12345",
  "rawKey": "sk-abc12345...xyz",  // ⚠️ 仅显示一次
  "keyName": "生产环境 Key",
  "keyType": "USER",
  "quotaPlanId": "developer",
  "createdAt": "2026-04-06T12:00:00Z",
  "warning": "请安全保存此 API Key，它将不会再显示！"
}
```

### 使用 API Key 调用受保护接口

```bash
curl -X POST http://localhost:8080/api/scheduler/tasks \
  -H "X-API-Key: sk-abc12345...xyz" \
  -H "Content-Type: application/json" \
  -d '{
    "taskName": "每日备份",
    "cronExpression": "0 2 * * *"
  }'
```

### 管理员查看高使用量 Key

```bash
curl -X GET "http://localhost:8080/api/admin/apikey/high-usage?threshold=1000" \
  -H "Authorization: Bearer $ADMIN_JWT"
```

### 管理员紧急熔断服务

```bash
curl -X POST http://localhost:8080/api/admin/apikey/circuit-breaker/trip \
  -H "Authorization: Bearer $ADMIN_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "serviceName": "ai-model",
    "reason": "检测到异常流量，临时熔断保护系统"
  }'
```

---

## 配额计划

| 计划 ID | 名称 | 价格 | 每日请求 | 并发 | 速率限制 |
|---------|------|------|----------|------|----------|
| default | 默认 | ¥0 | 1,000 | 5 | 10/s |
| developer | 开发者 | ¥29/月 | 10,000 | 10 | 20/s |
| team | 团队 | ¥99/月 | 100,000 | 50 | 50/s |
| enterprise | 企业 | ¥299/月 | 1,000,000 | 200 | 100/s |

---

## 最佳实践

### 用户侧
1. **妥善保存 API Key**: 只在创建时显示一次
2. **定期轮换 Key**: 建议每 90 天更换一次
3. **最小权限原则**: 只申请需要的权限
4. **监控使用量**: 定期检查配额使用情况

### 管理员侧
1. **设置合理配额**: 根据用户等级设置不同配额
2. **监控异常流量**: 定期检查高使用量 Key
3. **及时响应告警**: 可疑活动及时处理
4. **定期审计**: 审核配额覆盖和特殊权限

---

## 安全建议

1. **HTTPS 传输**: API Key 必须通过 HTTPS 传输
2. **Key 加密存储**: 使用 BCrypt 加密存储
3. **定期轮换**: 建议用户定期更换 API Key
4. **IP 绑定**: 生产环境建议绑定固定 IP
5. **审计日志**: 所有 API Key 操作记录日志
6. **告警通知**: 异常活动时通知用户和管理员
