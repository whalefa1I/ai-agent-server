-- V5__task_quota_and_limits.sql
-- 定时任务和 Heartbeat 配额管理表

-- 用户任务配额表
-- 定义每个用户的定时任务和 Heartbeat 任务的数量/频率限制
CREATE TABLE IF NOT EXISTS user_task_quota (
    user_id VARCHAR(64) PRIMARY KEY,

    -- 定时任务配额
    max_scheduled_tasks INT NOT NULL DEFAULT 10,
    max_scheduled_tasks_executing INT NOT NULL DEFAULT 3,  -- 最大并发执行数

    -- Heartbeat 任务配额
    max_heartbeat_tasks INT NOT NULL DEFAULT 5,
    heartbeat_interval_min_ms BIGINT NOT NULL DEFAULT 10000,  -- 最小心跳间隔 (10 秒)

    -- 执行频率配额
    max_executions_per_day INT NOT NULL DEFAULT 100,
    max_executions_per_hour INT NOT NULL DEFAULT 20,

    -- 资源配额
    max_task_duration_ms BIGINT NOT NULL DEFAULT 300000,  -- 单次任务最大执行时长 (5 分钟)
    max_task_payload_size_bytes INT NOT NULL DEFAULT 10240,  -- 最大 Payload 大小 (10KB)

    -- Cron 精度配额 (决定资源消耗)
    allowed_cron_precision VARCHAR(16) DEFAULT 'MINUTE',  -- MINUTE, HOUR, DAY
    -- MINUTE: 允许分钟级 Cron (如 */1 * * * *)
    -- HOUR: 只允许小时级 Cron (如 0 * * * *)
    -- DAY: 只允许天级 Cron (如 0 0 * * *)

    -- 通知配额
    max_notifications_per_day INT NOT NULL DEFAULT 50,

    -- 使用统计 (实时累计，每日重置)
    tasks_executed_today INT NOT NULL DEFAULT 0,
    notifications_sent_today INT NOT NULL DEFAULT 0,
    last_reset_date DATE,

    -- 套餐关联
    plan_id VARCHAR(32) NOT NULL DEFAULT 'free',

    -- 状态
    enabled BOOLEAN DEFAULT TRUE,
    quota_exceeded_action VARCHAR(32) DEFAULT 'BLOCK',  -- BLOCK, QUEUE, NOTIFY

    -- 元数据
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_quotas_reset_at TIMESTAMP
);

-- 套餐任务配额配置表
-- 定义不同套餐的配额限制
CREATE TABLE IF NOT EXISTS plan_task_quota (
    plan_id VARCHAR(32) PRIMARY KEY,
    plan_name VARCHAR(64) NOT NULL,

    -- 定时任务配额
    max_scheduled_tasks INT NOT NULL DEFAULT 10,
    max_scheduled_tasks_executing INT NOT NULL DEFAULT 3,

    -- Heartbeat 任务配额
    max_heartbeat_tasks INT NOT NULL DEFAULT 5,
    heartbeat_interval_min_ms BIGINT NOT NULL DEFAULT 10000,

    -- 执行频率配额
    max_executions_per_day INT NOT NULL DEFAULT 100,
    max_executions_per_hour INT NOT NULL DEFAULT 20,

    -- 资源配额
    max_task_duration_ms BIGINT NOT NULL DEFAULT 300000,
    max_task_payload_size_bytes INT NOT NULL DEFAULT 10240,

    -- Cron 精度配额
    allowed_cron_precision VARCHAR(16) DEFAULT 'MINUTE',

    -- 通知配额
    max_notifications_per_day INT NOT NULL DEFAULT 50,

    -- 价格 (分)
    price_cents INT NOT NULL DEFAULT 0,
    currency VARCHAR(3) DEFAULT 'CNY',
    billing_cycle VARCHAR(16) DEFAULT 'monthly',

    -- 描述
    description VARCHAR(512),
    features JSON,

    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 任务配额使用历史表
-- 记录每日配额使用情况，用于计费和审计
CREATE TABLE IF NOT EXISTS task_quota_usage_daily (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    usage_date DATE NOT NULL,

    -- 任务使用统计
    tasks_created INT NOT NULL DEFAULT 0,
    tasks_executed INT NOT NULL DEFAULT 0,
    tasks_failed INT NOT NULL DEFAULT 0,

    -- 类型统计
    scheduled_tasks_created INT NOT NULL DEFAULT 0,
    heartbeat_tasks_created INT NOT NULL DEFAULT 0,

    -- 资源使用统计
    total_execution_duration_ms BIGINT NOT NULL DEFAULT 0,
    total_payload_size_bytes BIGINT NOT NULL DEFAULT 0,

    -- 通知统计
    notifications_sent INT NOT NULL DEFAULT 0,

    -- 配额超限事件
    quota_exceeded_events INT NOT NULL DEFAULT 0,

    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_user_date UNIQUE (user_id, usage_date)
);

-- 配额超限事件表
-- 记录每次配额超限事件
CREATE TABLE IF NOT EXISTS quota_exceeded_event (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,  -- TASKS_LIMIT, EXECUTIONS_LIMIT, DURATION_LIMIT, etc.
    event_data JSON,

    current_value INT NOT NULL,
    limit_value INT NOT NULL,

    action_taken VARCHAR(32),  -- BLOCKED, QUEUED, NOTIFIED

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_user_task_quota_plan ON user_task_quota(plan_id);
CREATE INDEX IF NOT EXISTS idx_user_task_quota_enabled ON user_task_quota(enabled);

CREATE INDEX IF NOT EXISTS idx_quota_usage_daily_user ON task_quota_usage_daily(user_id);
CREATE INDEX IF NOT EXISTS idx_quota_usage_daily_date ON task_quota_usage_daily(usage_date);

CREATE INDEX IF NOT EXISTS idx_quota_exceeded_event_user ON quota_exceeded_event(user_id);
CREATE INDEX IF NOT EXISTS idx_quota_exceeded_event_type ON quota_exceeded_event(event_type);
CREATE INDEX IF NOT EXISTS idx_quota_exceeded_event_created_at ON quota_exceeded_event(created_at);

-- 插入默认套餐配置
INSERT INTO plan_task_quota (plan_id, plan_name, description,
    max_scheduled_tasks, max_scheduled_tasks_executing,
    max_heartbeat_tasks, heartbeat_interval_min_ms,
    max_executions_per_day, max_executions_per_hour,
    max_task_duration_ms, max_task_payload_size_bytes,
    allowed_cron_precision, max_notifications_per_day,
    price_cents) VALUES

('free', '免费版', '适合个人开发和测试，基础定时任务功能',
    5, 1,     -- 最多 5 个任务，1 个并发
    2, 60000, -- 最多 2 个心跳，最小 60 秒间隔
    50, 10,   -- 每天 50 次执行，每小时 10 次
    60000, 5120,  -- 1 分钟执行时长，5KB payload
    'HOUR', 10,    -- 只允许小时级 Cron，每天 10 条通知
    0),

('pro', '专业版', '适合小型团队，更高的配额和精度',
    50, 5,     -- 最多 50 个任务，5 个并发
    10, 10000, -- 最多 10 个心跳，最小 10 秒间隔
    1000, 100, -- 每天 1000 次执行，每小时 100 次
    300000, 10240, -- 5 分钟执行时长，10KB payload
    'MINUTE', 200,  -- 允许分钟级 Cron，每天 200 条通知
    2900),

('enterprise', '企业版', '适合大规模生产环境，最高配额',
    500, 50,    -- 最多 500 个任务，50 个并发
    50, 5000,   -- 最多 50 个心跳，最小 5 秒间隔
    100000, 10000, -- 每天 10 万次执行，每小时 1 万次
    600000, 102400, -- 10 分钟执行时长，100KB payload
    'SECOND', 2000, -- 允许秒级 Cron，每天 2000 条通知
    9900);

-- 用户任务配额从套餐初始化触发器
DELIMITER $$
CREATE TRIGGER IF NOT EXISTS init_user_task_quota_on_subscription
AFTER INSERT ON user_subscription
FOR EACH ROW
BEGIN
    DECLARE v_plan_id VARCHAR(32);
    SET v_plan_id = NEW.plan_id;

    INSERT INTO user_task_quota (user_id, plan_id)
    SELECT NEW.user_id, v_plan_id
    WHERE NOT EXISTS (
        SELECT 1 FROM user_task_quota WHERE user_id = NEW.user_id
    );
END$$
DELIMITER ;

-- 创建触发器自动更新 updated_at
CREATE TRIGGER IF NOT EXISTS update_user_task_quota_timestamp
BEFORE UPDATE ON user_task_quota
FOR EACH ROW
SET NEW.updated_at = CURRENT_TIMESTAMP;
