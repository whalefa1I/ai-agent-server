-- V3__user_session_table.sql
-- 用户会话管理表

-- 用户会话表
-- 记录用户的所有会话，用于统计、审计和配额管理
CREATE TABLE IF NOT EXISTS user_session (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    session_name VARCHAR(128), -- 可选的会话名称
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, ENDED, EXPIRED, TERMINATED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP,
    last_activity_at TIMESTAMP,

    -- 统计信息
    total_input_tokens BIGINT NOT NULL DEFAULT 0,
    total_output_tokens BIGINT NOT NULL DEFAULT 0,
    total_cache_read_tokens BIGINT NOT NULL DEFAULT 0,
    total_cache_write_tokens BIGINT NOT NULL DEFAULT 0,
    request_count INT NOT NULL DEFAULT 0,
    tool_call_count INT NOT NULL DEFAULT 0,
    skill_execution_count INT NOT NULL DEFAULT 0,

    -- 性能指标
    avg_latency_ms DOUBLE,
    max_latency_ms INT,
    total_duration_ms BIGINT,

    -- 元数据
    user_agent VARCHAR(256), -- 客户端标识
    ip_address_hash VARCHAR(64), -- IP 地址哈希（脱敏）
    region VARCHAR(32), -- 地理区域
    metadata JSON,

    -- 索引优化
    INDEX idx_user_status (user_id, status),
    INDEX idx_user_created (user_id, created_at),
    INDEX idx_status (status),
    INDEX idx_created (created_at),
    INDEX idx_last_activity (last_activity_at)
);

-- 会话消息历史表
-- 记录会话中的消息历史，用于训练数据收集和审计
CREATE TABLE IF NOT EXISTS session_message (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    message_order INT NOT NULL, -- 消息在会话中的顺序
    role VARCHAR(32) NOT NULL, -- user, assistant, system, tool

    -- 消息内容
    content TEXT,
    content_hash VARCHAR(64), -- 用于去重

    -- Token 统计
    input_tokens INT NOT NULL DEFAULT 0,
    output_tokens INT NOT NULL DEFAULT 0,

    -- 工具调用信息
    tool_calls JSON, -- 工具调用列表
    tool_call_results JSON, -- 工具调用结果

    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- 索引
    INDEX idx_session_order (session_id, message_order),
    INDEX idx_session (session_id),
    INDEX idx_created (created_at)
);

-- 会话工具调用记录表
-- 详细记录每次工具调用的输入输出，用于训练数据
CREATE TABLE IF NOT EXISTS session_tool_call (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(64), -- 关联的消息 ID

    -- 工具信息
    tool_name VARCHAR(128) NOT NULL,
    tool_type VARCHAR(32), -- local, mcp, skill

    -- 输入输出
    input_json JSON NOT NULL,
    output_json JSON,
    output_success BOOLEAN DEFAULT TRUE,

    -- 性能指标
    latency_ms INT NOT NULL,
    input_tokens INT NOT NULL DEFAULT 0,
    output_tokens INT NOT NULL DEFAULT 0,

    -- 错误信息
    error_message TEXT,
    error_code VARCHAR(64),

    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- 索引
    INDEX idx_session (session_id),
    INDEX idx_message (message_id),
    INDEX idx_tool_name (tool_name),
    INDEX idx_created (created_at)
);

-- 会话技能执行记录表
-- 专门记录技能执行的详细信息
CREATE TABLE IF NOT EXISTS session_skill_execution (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    tool_call_id VARCHAR(64), -- 关联的工具调用 ID

    -- 技能信息
    skill_name VARCHAR(128) NOT NULL,
    skill_type VARCHAR(32), -- PYTHON, NODEJS, BUILTIN
    skill_version VARCHAR(32),

    -- 执行信息
    script_path VARCHAR(256),
    execution_command VARCHAR(512),
    exit_code INT,

    -- 输入输出
    input_args JSON,
    output_result TEXT,
    error_output TEXT,

    -- 性能指标
    execution_time_ms INT NOT NULL,

    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- 索引
    INDEX idx_session (session_id),
    INDEX idx_skill_name (skill_name),
    INDEX idx_created (created_at)
);

-- 会话统计汇总表（物化视图替代）
-- 用于快速查询用户和会话统计
CREATE TABLE IF NOT EXISTS session_stats_summary (
    user_id VARCHAR(64) PRIMARY KEY,
    total_sessions INT NOT NULL DEFAULT 0,
    active_sessions INT NOT NULL DEFAULT 0,
    total_requests BIGINT NOT NULL DEFAULT 0,
    total_input_tokens BIGINT NOT NULL DEFAULT 0,
    total_output_tokens BIGINT NOT NULL DEFAULT 0,
    total_tool_calls BIGINT NOT NULL DEFAULT 0,
    total_skill_executions BIGINT NOT NULL DEFAULT 0,
    avg_session_duration_ms BIGINT,
    last_session_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- 索引
    INDEX idx_active (active_sessions),
    INDEX idx_last_session (last_session_at)
);

-- 触发器：自动更新会话统计
CREATE TRIGGER IF NOT EXISTS update_session_stats_on_end
AFTER UPDATE ON user_session
FOR EACH ROW
WHEN OLD.status = 'ACTIVE' AND NEW.status != 'ACTIVE'
BEGIN ATOMIC
    UPDATE session_stats_summary
    SET active_sessions = GREATEST(0, active_sessions - 1),
        updated_at = CURRENT_TIMESTAMP
    WHERE user_id = NEW.user_id;
END;

-- 创建默认套餐的触发器：新用户自动分配免费套餐
-- 这个需要在应用层实现，因为 H2 不支持 INSERT INTO ... ON DUPLICATE KEY
