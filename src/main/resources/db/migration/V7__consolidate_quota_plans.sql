-- V7__consolidate_quota_plans.sql
-- 整合配额计划表 - 将 task_quota 配额整合到 api_key_quota_plan

-- ==================== 1. 扩展 api_key_quota_plan 表 ====================

-- 添加定时任务和 Heartbeat 相关字段
ALTER TABLE api_key_quota_plan ADD COLUMN IF NOT EXISTS max_scheduled_tasks INT DEFAULT 10;
ALTER TABLE api_key_quota_plan ADD COLUMN IF NOT EXISTS max_scheduled_tasks_executing INT DEFAULT 3;
ALTER TABLE api_key_quota_plan ADD COLUMN IF NOT EXISTS max_heartbeat_tasks INT DEFAULT 5;
ALTER TABLE api_key_quota_plan ADD COLUMN IF NOT EXISTS heartbeat_interval_min_ms BIGINT DEFAULT 10000;

-- 添加 Cron 精度配置
ALTER TABLE api_key_quota_plan ADD COLUMN IF NOT EXISTS allowed_cron_precision VARCHAR(16) DEFAULT 'MINUTE';

-- 添加任务执行配额
ALTER TABLE api_key_quota_plan ADD COLUMN IF NOT EXISTS max_task_executions_per_day INT DEFAULT 100;
ALTER TABLE api_key_quota_plan ADD COLUMN IF NOT EXISTS max_task_executions_per_hour INT DEFAULT 20;

-- 添加任务资源配额
ALTER TABLE api_key_quota_plan ADD COLUMN IF NOT EXISTS max_task_duration_ms BIGINT DEFAULT 300000;
ALTER TABLE api_key_quota_plan ADD COLUMN IF NOT EXISTS max_task_payload_size_bytes INT DEFAULT 10240;

-- 添加通知配额
ALTER TABLE api_key_quota_plan ADD COLUMN IF NOT EXISTS max_notifications_per_day INT DEFAULT 50;

-- ==================== 2. 更新现有套餐数据 ====================

-- 更新 default 套餐
UPDATE api_key_quota_plan
SET max_scheduled_tasks = 10,
    max_scheduled_tasks_executing = 3,
    max_heartbeat_tasks = 5,
    heartbeat_interval_min_ms = 10000,
    allowed_cron_precision = 'MINUTE',
    max_task_executions_per_day = requests_per_day / 10,
    max_task_executions_per_hour = rate_limit_per_hour,
    max_task_duration_ms = 300000,
    max_task_payload_size_bytes = 10240,
    max_notifications_per_day = 50
WHERE plan_id = 'default';

-- 更新 developer 套餐
UPDATE api_key_quota_plan
SET max_scheduled_tasks = 20,
    max_scheduled_tasks_executing = 5,
    max_heartbeat_tasks = 10,
    heartbeat_interval_min_ms = 5000,
    allowed_cron_precision = 'MINUTE',
    max_task_executions_per_day = requests_per_day / 10,
    max_task_executions_per_hour = rate_limit_per_hour,
    max_task_duration_ms = 300000,
    max_task_payload_size_bytes = 10240,
    max_notifications_per_day = 200
WHERE plan_id = 'developer';

-- 更新 team 套餐
UPDATE api_key_quota_plan
SET max_scheduled_tasks = 50,
    max_scheduled_tasks_executing = 10,
    max_heartbeat_tasks = 20,
    heartbeat_interval_min_ms = 3000,
    allowed_cron_precision = 'MINUTE',
    max_task_executions_per_day = requests_per_day / 10,
    max_task_executions_per_hour = rate_limit_per_hour,
    max_task_duration_ms = 600000,
    max_task_payload_size_bytes = 51200,
    max_notifications_per_day = 500
WHERE plan_id = 'team';

-- 更新 enterprise 套餐
UPDATE api_key_quota_plan
SET max_scheduled_tasks = 200,
    max_scheduled_tasks_executing = 50,
    max_heartbeat_tasks = 50,
    heartbeat_interval_min_ms = 1000,
    allowed_cron_precision = 'SECOND',
    max_task_executions_per_day = requests_per_day / 10,
    max_task_executions_per_hour = rate_limit_per_hour,
    max_task_duration_ms = 1800000,
    max_task_payload_size_bytes = 102400,
    max_notifications_per_day = 2000
WHERE plan_id = 'enterprise';

-- ==================== 3. 从 plan_task_quota 迁移数据（如果存在） ====================

-- 如果 plan_task_quota 表有数据，可以选择性迁移
-- 这里仅做注释，实际执行时根据需要 uncomment
/*
INSERT INTO api_key_quota_plan (plan_id, plan_name, description,
    requests_per_day, tokens_per_day, compute_minutes_per_day,
    rate_limit_per_second, rate_limit_per_minute, rate_limit_per_hour,
    max_concurrent_requests,
    max_scheduled_tasks, max_scheduled_tasks_executing,
    max_heartbeat_tasks, heartbeat_interval_min_ms,
    max_task_executions_per_day, max_task_executions_per_hour,
    max_task_duration_ms, max_task_payload_size_bytes,
    allowed_cron_precision, max_notifications_per_day,
    price_cents, currency, billing_cycle, is_active)
SELECT
    ptq.plan_id,
    ptq.plan_name,
    ptq.description,
    10000,  -- 默认 requests_per_day
    100000, -- 默认 tokens_per_day
    60,     -- 默认 compute_minutes_per_day
    10,     -- 默认 rate_limit_per_second
    100,    -- 默认 rate_limit_per_minute
    1000,   -- 默认 rate_limit_per_hour
    5,      -- 默认 max_concurrent_requests
    ptq.max_scheduled_tasks,
    ptq.max_scheduled_tasks_executing,
    ptq.max_heartbeat_tasks,
    ptq.heartbeat_interval_min_ms,
    ptq.max_executions_per_day,
    ptq.max_executions_per_hour,
    ptq.max_task_duration_ms,
    ptq.max_task_payload_size_bytes,
    ptq.allowed_cron_precision,
    ptq.max_notifications_per_day,
    ptq.price_cents,
    ptq.currency,
    ptq.billing_cycle,
    ptq.is_active
FROM plan_task_quota ptq
WHERE NOT EXISTS (
    SELECT 1 FROM api_key_quota_plan akqp WHERE akqp.plan_id = ptq.plan_id
);
*/

-- ==================== 4. 更新 user_task_quota 表关联 ====================

-- 添加外键关联到 api_key_quota_plan（可选，取决于是否需要强制参照完整性）
-- ALTER TABLE user_task_quota ADD CONSTRAINT fk_user_task_quota_plan
--     FOREIGN KEY (plan_id) REFERENCES api_key_quota_plan(plan_id);

-- ==================== 5. 创建配额整合视图 ====================

-- 创建统一的配额计划视图，方便查询
CREATE OR REPLACE VIEW quota_plan_summary AS
SELECT
    plan_id,
    plan_name,
    description,
    price_cents,
    currency,
    billing_cycle,

    -- API Key 配额
    requests_per_day,
    tokens_per_day,
    compute_minutes_per_day,
    rate_limit_per_second,
    rate_limit_per_minute,
    rate_limit_per_hour,
    max_concurrent_requests,

    -- 定时任务配额
    max_scheduled_tasks,
    max_scheduled_tasks_executing,
    max_heartbeat_tasks,
    heartbeat_interval_min_ms,
    allowed_cron_precision,
    max_task_executions_per_day,
    max_task_executions_per_hour,
    max_task_duration_ms,
    max_task_payload_size_bytes,
    max_notifications_per_day,

    -- 功能权限
    allowed_features,
    blocked_features,
    premium_features,

    -- 状态
    is_active,
    is_premium,
    created_at,
    updated_at
FROM api_key_quota_plan
WHERE is_active = TRUE;

-- ==================== 6. 可选：删除或保留旧的 plan_task_quota 表 ====================

-- 如果需要保留向后兼容性，可以保留表但不推荐使用
-- 如果要删除，请 uncomment 以下语句:
-- DROP TABLE IF EXISTS plan_task_quota;

-- 注释说明：
-- plan_task_quota 表已整合到 api_key_quota_plan
-- 请使用 api_key_quota_plan 或 quota_plan_summary 视图进行配额查询
