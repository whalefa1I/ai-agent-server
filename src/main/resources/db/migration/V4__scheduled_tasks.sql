-- V4__scheduled_tasks.sql
-- 定时任务管理表

-- 定时任务主表
-- 存储所有用户的定时任务配置
CREATE TABLE IF NOT EXISTS scheduled_task (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64),

    -- 任务基本信息
    task_name VARCHAR(128) NOT NULL,
    task_description VARCHAR(512),
    task_type VARCHAR(32) NOT NULL DEFAULT 'CHAT', -- CHAT, TOOL, API

    -- Cron 表达式或固定延迟配置
    cron_expression VARCHAR(64) NOT NULL,
    fixed_delay_ms BIGINT, -- 可选，固定延迟（毫秒）
    fixed_rate_ms BIGINT, -- 可选，固定频率（毫秒）

    -- 任务内容
    task_payload JSON NOT NULL, -- 任务具体内容（如：聊天消息、工具调用等）

    -- 调度配置
    timezone VARCHAR(64) DEFAULT 'UTC',
    start_at TIMESTAMP, -- 可选，任务开始时间
    end_at TIMESTAMP, -- 可选，任务结束时间

    -- 执行控制
    max_executions INT DEFAULT -1, -- 最大执行次数，-1 表示无限
    execution_count INT DEFAULT 0, -- 已执行次数
    concurrent_execution BOOLEAN DEFAULT FALSE, -- 是否允许并发执行

    -- 状态管理
    enabled BOOLEAN DEFAULT TRUE, -- 是否启用
    status VARCHAR(16) DEFAULT 'ACTIVE', -- ACTIVE, PAUSED, COMPLETED, CANCELLED

    -- 错误处理
    error_handling VARCHAR(32) DEFAULT 'CONTINUE', -- CONTINUE, STOP, RETRY
    max_retries INT DEFAULT 3,
    retry_delay_ms INT DEFAULT 1000,

    -- 通知配置
    notify_on_success BOOLEAN DEFAULT FALSE,
    notify_on_failure BOOLEAN DEFAULT TRUE,
    notification_channels JSON, -- 如：["email", "slack"]

    -- 元数据
    metadata JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64)
);

-- 任务执行历史表
-- 记录每次任务执行的详细信息
CREATE TABLE IF NOT EXISTS task_execution_history (
    id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,

    -- 执行时间
    scheduled_time TIMESTAMP, -- 计划执行时间
    actual_start_time TIMESTAMP, -- 实际开始时间
    actual_end_time TIMESTAMP, -- 实际结束时间

    -- 执行状态
    status VARCHAR(32) NOT NULL, -- PENDING, RUNNING, SUCCESS, FAILED, CANCELLED, TIMEOUT
    error_message TEXT,
    error_stack_trace TEXT,

    -- 执行结果
    result JSON, -- 执行结果
    duration_ms BIGINT, -- 执行时长（毫秒）

    -- 重试信息
    is_retry BOOLEAN DEFAULT FALSE,
    retry_count INT DEFAULT 0,
    retry_of_id VARCHAR(64), -- 如果是重试，指向原执行记录

    -- 执行上下文
    execution_context JSON, -- 执行时的上下文信息

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 任务日志表
-- 记录任务执行过程中的详细日志
CREATE TABLE IF NOT EXISTS task_execution_log (
    id VARCHAR(64) PRIMARY KEY,
    execution_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,

    -- 日志内容
    log_level VARCHAR(16) DEFAULT 'INFO', -- DEBUG, INFO, WARN, ERROR
    log_message TEXT NOT NULL,
    log_data JSON,

    -- 时间戳
    logged_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_scheduled_task_user_id ON scheduled_task(user_id);
CREATE INDEX IF NOT EXISTS idx_scheduled_task_status ON scheduled_task(status);
CREATE INDEX IF NOT EXISTS idx_scheduled_task_enabled ON scheduled_task(enabled);
CREATE INDEX IF NOT EXISTS idx_scheduled_task_next_execution ON scheduled_task(status, enabled, start_at);
CREATE INDEX IF NOT EXISTS idx_scheduled_task_user_status ON scheduled_task(user_id, status);

CREATE INDEX IF NOT EXISTS idx_execution_history_task_id ON task_execution_history(task_id);
CREATE INDEX IF NOT EXISTS idx_execution_history_user_id ON task_execution_history(user_id);
CREATE INDEX IF NOT EXISTS idx_execution_history_status ON task_execution_history(status);
CREATE INDEX IF NOT EXISTS idx_execution_history_scheduled_time ON task_execution_history(scheduled_time);
CREATE INDEX IF NOT EXISTS idx_execution_history_actual_start_time ON task_execution_history(actual_start_time);
CREATE INDEX IF NOT EXISTS idx_execution_history_user_status ON task_execution_history(user_id, status);

CREATE INDEX IF NOT EXISTS idx_execution_log_execution_id ON task_execution_log(execution_id);
CREATE INDEX IF NOT EXISTS idx_execution_log_task_id ON task_execution_log(task_id);
CREATE INDEX IF NOT EXISTS idx_execution_log_user_id ON task_execution_log(user_id);
CREATE INDEX IF NOT EXISTS idx_execution_log_logged_at ON task_execution_log(logged_at);

-- 创建触发器自动更新 updated_at
CREATE TRIGGER IF NOT EXISTS update_scheduled_task_timestamp
BEFORE UPDATE ON scheduled_task
FOR EACH ROW
SET NEW.updated_at = CURRENT_TIMESTAMP;
