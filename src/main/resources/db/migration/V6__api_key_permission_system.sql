-- V6__api_key_permission_system.sql
-- API Key 权限管理系统 - 基于配额模式的核心收益点权限收拢

-- ==================== API Key 主表 ====================

-- API Key 表
-- 每个用户可以拥有多个 API Key，每个 Key 有独立的配额和权限
CREATE TABLE IF NOT EXISTS api_key (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,

    -- Key 信息
    key_hash VARCHAR(256) NOT NULL,  -- BCrypt 加密后的 Key
    key_prefix VARCHAR(16) NOT NULL, -- Key 前缀 (用于展示，如：sk-abc123...)
    key_name VARCHAR(128),           -- Key 名称/备注

    -- 密钥类型
    key_type VARCHAR(32) NOT NULL DEFAULT 'USER',  -- USER, SERVICE, ADMIN
    key_scope VARCHAR(32) NOT NULL DEFAULT 'PERSONAL',  -- PERSONAL, ORGANIZATION, GLOBAL

    -- 状态管理
    enabled BOOLEAN DEFAULT TRUE,
    status VARCHAR(16) DEFAULT 'ACTIVE',  -- ACTIVE, REVOKED, EXPIRED, SUSPENDED

    -- 时间控制
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,  -- 过期时间 (NULL 表示永不过期)
    last_used_at TIMESTAMP,
    revoked_at TIMESTAMP,
    revoke_reason VARCHAR(256),

    -- 使用统计 (实时累计)
    total_requests BIGINT DEFAULT 0,
    requests_today INT DEFAULT 0,
    tokens_used_today BIGINT DEFAULT 0,
    last_reset_date DATE,

    -- 配额关联
    quota_plan_id VARCHAR(32) DEFAULT 'default',

    -- 权限控制
    permissions JSON,  -- 权限列表
    allowed_endpoints JSON,  -- 允许的端点列表
    blocked_endpoints JSON,  -- 阻止的端点列表

    -- 速率限制
    rate_limit_per_second INT DEFAULT 10,
    rate_limit_per_minute INT DEFAULT 100,
    rate_limit_per_hour INT DEFAULT 1000,
    rate_limit_per_day INT DEFAULT 10000,

    -- 并发控制
    max_concurrent_requests INT DEFAULT 5,

    -- IP 限制
    allowed_ip_ranges JSON,  -- 允许的 IP 范围
    blocked_ip_ranges JSON,  -- 阻止的 IP 范围

    -- 元数据
    metadata JSON,
    created_by VARCHAR(64),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==================== API Key 使用日志 ====================

-- API Key 使用日志表
-- 记录每次 API 调用的详细信息
CREATE TABLE IF NOT EXISTS api_key_usage_log (
    id VARCHAR(64) PRIMARY KEY,
    api_key_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,

    -- 请求信息
    endpoint VARCHAR(256) NOT NULL,
    method VARCHAR(16) NOT NULL,
    request_size_bytes INT,
    response_size_bytes INT,

    -- 性能指标
    response_time_ms INT,
    status_code INT,

    -- 资源消耗
    tokens_consumed INT DEFAULT 0,
    compute_time_ms INT DEFAULT 0,

    -- 来源信息
    ip_address VARCHAR(64),
    user_agent VARCHAR(512),
    referer VARCHAR(512),

    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==================== 速率限制桶 ====================

-- 速率限制桶表 (用于分布式限流)
CREATE TABLE IF NOT EXISTS rate_limit_bucket (
    id VARCHAR(128) PRIMARY KEY,  -- key: userId:bucketType:bucketWindow
    api_key_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,

    bucket_type VARCHAR(16) NOT NULL,  -- SECOND, MINUTE, HOUR, DAY
    bucket_window VARCHAR(32) NOT NULL,  -- 时间窗口标识

    request_count INT DEFAULT 0,
    tokens_consumed BIGINT DEFAULT 0,

    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==================== API Key 配额计划 ====================

-- API Key 配额计划表
-- 定义不同类型的配额套餐
CREATE TABLE IF NOT EXISTS api_key_quota_plan (
    plan_id VARCHAR(32) PRIMARY KEY,
    plan_name VARCHAR(64) NOT NULL,
    description VARCHAR(512),

    -- 基础配额
    requests_per_day INT NOT NULL DEFAULT 1000,
    tokens_per_day BIGINT NOT NULL DEFAULT 100000,
    compute_minutes_per_day INT NOT NULL DEFAULT 60,

    -- 速率限制
    rate_limit_per_second INT DEFAULT 10,
    rate_limit_per_minute INT DEFAULT 100,
    rate_limit_per_hour INT DEFAULT 1000,

    -- 并发限制
    max_concurrent_requests INT DEFAULT 5,

    -- 功能权限
    allowed_features JSON,  -- 允许的功能列表
    blocked_features JSON,  -- 阻止的功能列表

    -- 核心收益点权限 (需要单独收费的功能)
    premium_features JSON,  -- 高级功能
    premium_feature_limit JSON,  -- 高级功能限制

    -- 价格
    price_cents INT DEFAULT 0,
    currency VARCHAR(3) DEFAULT 'CNY',
    billing_cycle VARCHAR(16) DEFAULT 'monthly',

    -- 状态
    is_active BOOLEAN DEFAULT TRUE,
    is_premium BOOLEAN DEFAULT FALSE,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==================== 核心收益点权限配置 ====================

-- 核心收益点配置表
-- 定义需要单独收费/限制的核心功能
CREATE TABLE IF NOT EXISTS premium_feature_config (
    feature_id VARCHAR(64) PRIMARY KEY,
    feature_name VARCHAR(128) NOT NULL,
    feature_category VARCHAR(64),  -- AI_MODEL, FILE_OPERATION, REMOTE_TOOL, etc.

    -- 计费方式
    billing_type VARCHAR(32) NOT NULL,  -- PER_REQUEST, PER_TOKEN, PER_MINUTE, FLAT
    price_per_unit BIGINT NOT NULL,  -- 单价 (分)
    unit_name VARCHAR(32),  -- 单位名称

    -- 限制配置
    default_limit INT,  -- 默认限制
    max_limit INT,  -- 最大限制

    -- 描述
    description VARCHAR(512),

    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 用户核心收益点使用统计
CREATE TABLE IF NOT EXISTS user_premium_usage (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    api_key_id VARCHAR(64) NOT NULL,
    feature_id VARCHAR(64) NOT NULL,

    usage_date DATE NOT NULL,
    usage_count INT DEFAULT 0,
    usage_amount BIGINT DEFAULT 0,  -- Token 数/时长等

    total_cost_cents BIGINT DEFAULT 0,  -- 总费用 (分)

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_user_feature_date UNIQUE (user_id, feature_id, usage_date)
);

-- ==================== 管理员控制表 ====================

-- 管理员配额覆盖表
-- 管理员可以针对特定用户设置特殊配额
CREATE TABLE IF NOT EXISTS admin_quota_override (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    api_key_id VARCHAR(64),  -- NULL 表示对应用户所有 Key 生效

    -- 覆盖的配额
    override_requests_per_day INT,
    override_tokens_per_day BIGINT,
    override_rate_limit INT,
    override_max_concurrent INT,

    -- 控制开关
    force_enabled BOOLEAN DEFAULT FALSE,  -- 强制启用 (无视其他限制)
    force_disabled BOOLEAN DEFAULT FALSE,  -- 强制禁用 (优先于其他限制)

    -- 原因和审计
    reason VARCHAR(512),
    admin_user_id VARCHAR(64) NOT NULL,

    valid_from TIMESTAMP,
    valid_until TIMESTAMP,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 服务熔断配置表
-- 当检测到异常流量时自动熔断
CREATE TABLE IF NOT EXISTS service_circuit_breaker (
    id VARCHAR(64) PRIMARY KEY,
    service_name VARCHAR(128) NOT NULL,
    endpoint_pattern VARCHAR(256),

    -- 熔断条件
    failure_threshold INT DEFAULT 10,  -- 失败次数阈值
    failure_rate_threshold DECIMAL(5,2) DEFAULT 50.00,  -- 失败率阈值 (%)
    slow_call_threshold_ms INT DEFAULT 5000,  -- 慢调用阈值
    slow_call_rate_threshold DECIMAL(5,2) DEFAULT 80.00,  -- 慢调用率阈值

    -- 熔断状态
    status VARCHAR(16) DEFAULT 'CLOSED',  -- CLOSED, OPEN, HALF_OPEN
    failure_count INT DEFAULT 0,
    last_failure_at TIMESTAMP,

    -- 恢复配置
    auto_recovery_enabled BOOLEAN DEFAULT TRUE,
    recovery_timeout_seconds INT DEFAULT 60,

    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 恶意行为检测表
-- 记录疑似恶意行为的 Key
CREATE TABLE IF NOT EXISTS suspicious_activity (
    id VARCHAR(64) PRIMARY KEY,
    api_key_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,

    activity_type VARCHAR(64) NOT NULL,  -- RATE_LIMIT_EXCEEDED, UNUSUAL_PATTERN, etc.
    severity VARCHAR(16) DEFAULT 'MEDIUM',  -- LOW, MEDIUM, HIGH, CRITICAL

    evidence JSON,  -- 证据数据
    description VARCHAR(512),

    -- 处理状态
    status VARCHAR(16) DEFAULT 'PENDING',  -- PENDING, REVIEWING, RESOLVED, FALSE_POSITIVE
    action_taken VARCHAR(64),  -- WARNED, LIMITED, SUSPENDED, REVOKED

    reviewed_by VARCHAR(64),
    reviewed_at TIMESTAMP,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==================== 创建索引 ====================

-- API Key 索引
CREATE INDEX IF NOT EXISTS idx_api_key_user_id ON api_key(user_id);
CREATE INDEX IF NOT EXISTS idx_api_key_key_hash ON api_key(key_hash);
CREATE INDEX IF NOT EXISTS idx_api_key_key_prefix ON api_key(key_prefix);
CREATE INDEX IF NOT EXISTS idx_api_key_status ON api_key(status);
CREATE INDEX IF NOT EXISTS idx_api_key_enabled ON api_key(enabled);
CREATE INDEX IF NOT EXISTS idx_api_key_expires_at ON api_key(expires_at);
CREATE UNIQUE INDEX IF NOT EXISTS idx_api_key_key_hash_unique ON api_key(key_hash);

-- API Key 使用日志索引
CREATE INDEX IF NOT EXISTS idx_api_key_usage_log_api_key_id ON api_key_usage_log(api_key_id);
CREATE INDEX IF NOT EXISTS idx_api_key_usage_log_user_id ON api_key_usage_log(user_id);
CREATE INDEX IF NOT EXISTS idx_api_key_usage_log_endpoint ON api_key_usage_log(endpoint);
CREATE INDEX IF NOT EXISTS idx_api_key_usage_log_created_at ON api_key_usage_log(created_at);
CREATE INDEX IF NOT EXISTS idx_api_key_usage_log_status_code ON api_key_usage_log(status_code);

-- 速率限制桶索引
CREATE INDEX IF NOT EXISTS idx_rate_limit_bucket_api_key_id ON rate_limit_bucket(api_key_id);
CREATE INDEX IF NOT EXISTS idx_rate_limit_bucket_expires_at ON rate_limit_bucket(expires_at);

-- API Key 配额计划索引
CREATE INDEX IF NOT EXISTS idx_api_key_quota_plan_active ON api_key_quota_plan(is_active);
CREATE INDEX IF NOT EXISTS idx_api_key_quota_plan_premium ON api_key_quota_plan(is_premium);

-- 核心收益点配置索引
CREATE INDEX IF NOT EXISTS idx_premium_feature_config_category ON premium_feature_config(feature_category);
CREATE INDEX IF NOT EXISTS idx_premium_feature_config_active ON premium_feature_config(is_active);

-- 用户核心收益点使用统计索引
CREATE INDEX IF NOT EXISTS idx_user_premium_usage_user_id ON user_premium_usage(user_id);
CREATE INDEX IF NOT EXISTS idx_user_premium_usage_feature_id ON user_premium_usage(feature_id);
CREATE INDEX IF NOT EXISTS idx_user_premium_usage_date ON user_premium_usage(usage_date);

-- 管理员配额覆盖索引
CREATE INDEX IF NOT EXISTS idx_admin_quota_override_user_id ON admin_quota_override(user_id);
CREATE INDEX IF NOT EXISTS idx_admin_quota_override_admin_user_id ON admin_quota_override(admin_user_id);

-- 服务熔断配置索引
CREATE INDEX IF NOT EXISTS idx_service_circuit_breaker_service_name ON service_circuit_breaker(service_name);
CREATE INDEX IF NOT EXISTS idx_service_circuit_breaker_status ON service_circuit_breaker(status);

-- 恶意行为检测索引
CREATE INDEX IF NOT EXISTS idx_suspicious_activity_api_key_id ON suspicious_activity(api_key_id);
CREATE INDEX IF NOT EXISTS idx_suspicious_activity_user_id ON suspicious_activity(user_id);
CREATE INDEX IF NOT EXISTS idx_suspicious_activity_status ON suspicious_activity(status);
CREATE INDEX IF NOT EXISTS idx_suspicious_activity_severity ON suspicious_activity(severity);
CREATE INDEX IF NOT EXISTS idx_suspicious_activity_created_at ON suspicious_activity(created_at);

-- ==================== 初始化数据 ====================

-- 插入默认 API Key 配额计划
INSERT INTO api_key_quota_plan (plan_id, plan_name, description,
    requests_per_day, tokens_per_day, compute_minutes_per_day,
    rate_limit_per_second, rate_limit_per_minute, rate_limit_per_hour,
    max_concurrent_requests, price_cents) VALUES

('default', '默认计划', '新用户默认配额',
    1000, 100000, 60,
    10, 100, 1000,
    5, 0),

('developer', '开发者计划', '适合个人开发者',
    10000, 1000000, 300,
    20, 500, 5000,
    10, 2900),

('team', '团队计划', '适合小团队使用',
    100000, 10000000, 1800,
    50, 2000, 20000,
    50, 9900),

('enterprise', '企业计划', '大规模生产环境',
    1000000, 100000000, 10800,
    100, 5000, 50000,
    200, 29900);

-- 插入核心收益点配置
INSERT INTO premium_feature_config (feature_id, feature_name, feature_category,
    billing_type, price_per_unit, unit_name, default_limit, max_limit, description) VALUES

('ai_model_call', 'AI 模型调用', 'AI_MODEL',
    'PER_TOKEN', 1, 'token', 10000, 1000000, '调用 AI 大模型进行对话或分析'),

('remote_tool_execution', '远程工具执行', 'REMOTE_TOOL',
    'PER_REQUEST', 10, 'request', 100, 10000, '在远程沙盒中执行工具'),

('file_storage', '文件存储', 'STORAGE',
    'PER_GB_MONTH', 500, 'GB·月', 1, 100, '云文件存储空间'),

('vector_search', '向量搜索', 'AI_MODEL',
    'PER_REQUEST', 5, 'request', 100, 1000, '语义搜索和向量匹配'),

('scheduled_task', '定时任务', 'SCHEDULER',
    'PER_EXECUTION', 1, 'execution', 100, 10000, '定时任务执行'),

('heartbeat_service', '心跳服务', 'SCHEDULER',
    'PER_MINUTE', 1, 'minute', 60, 1440, '心跳保活服务');

-- 创建触发器自动更新 updated_at
CREATE TRIGGER IF NOT EXISTS update_api_key_timestamp
BEFORE UPDATE ON api_key
FOR EACH ROW
SET NEW.updated_at = CURRENT_TIMESTAMP;

CREATE TRIGGER IF NOT EXISTS update_api_key_quota_plan_timestamp
BEFORE UPDATE ON api_key_quota_plan
FOR EACH ROW
SET NEW.updated_at = CURRENT_TIMESTAMP;
