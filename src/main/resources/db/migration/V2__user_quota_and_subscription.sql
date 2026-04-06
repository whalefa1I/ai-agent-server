-- V2__user_quota_and_subscription.sql
-- 用户配额和订阅管理表

-- 用户配额表
-- 用于管理每个用户的请求配额、Token 配额、并发会话数等
CREATE TABLE IF NOT EXISTS user_quota (
    user_id VARCHAR(64) PRIMARY KEY,
    max_requests_per_day INT NOT NULL DEFAULT 1000,
    max_tokens_per_day INT NOT NULL DEFAULT 100000,
    max_concurrent_sessions INT NOT NULL DEFAULT 5,
    max_file_size_bytes BIGINT NOT NULL DEFAULT 10485760, -- 10MB
    quota_reset_at TIMESTAMP,
    requests_used_today INT NOT NULL DEFAULT 0,
    tokens_used_today INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 用户订阅表
-- 用于管理用户的订阅计划、Stripe 集成等
CREATE TABLE IF NOT EXISTS user_subscription (
    user_id VARCHAR(64) PRIMARY KEY,
    plan_id VARCHAR(32) NOT NULL DEFAULT 'free', -- free, pro, enterprise
    plan_name VARCHAR(64),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    stripe_customer_id VARCHAR(128),
    stripe_subscription_id VARCHAR(128),
    auto_renew BOOLEAN DEFAULT TRUE,
    cancelled_at TIMESTAMP,
    cancel_reason VARCHAR(256),
    metadata JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 用户套餐配置表
-- 定义不同套餐的配额和限制
CREATE TABLE IF NOT EXISTS subscription_plan (
    plan_id VARCHAR(32) PRIMARY KEY,
    plan_name VARCHAR(64) NOT NULL,
    description VARCHAR(256),
    max_requests_per_day INT NOT NULL DEFAULT 1000,
    max_tokens_per_day INT NOT NULL DEFAULT 100000,
    max_concurrent_sessions INT NOT NULL DEFAULT 5,
    max_file_size_bytes BIGINT NOT NULL DEFAULT 10485760,
    max_skills INT NOT NULL DEFAULT 10,
    price_cents INT NOT NULL DEFAULT 0,
    currency VARCHAR(3) DEFAULT 'USD',
    billing_cycle VARCHAR(16) DEFAULT 'monthly', -- monthly, yearly
    features JSON,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 插入默认套餐配置
INSERT INTO subscription_plan (plan_id, plan_name, description, max_requests_per_day, max_tokens_per_day, max_concurrent_sessions, max_file_size_bytes, max_skills, price_cents) VALUES
('free', '免费版', '适合个人开发和测试', 1000, 100000, 3, 10485760, 5, 0),
('pro', '专业版', '适合小型团队使用', 10000, 1000000, 10, 52428800, 20, 2900),
('enterprise', '企业版', '适合大规模生产环境', 100000, 10000000, 50, 104857600, 100, 9900)
ON DUPLICATE KEY UPDATE plan_name = VALUES(plan_name);

-- 配额使用历史记录表
-- 用于审计和分析用户的配额使用情况
CREATE TABLE IF NOT EXISTS quota_usage_history (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    usage_date DATE NOT NULL,
    requests_used INT NOT NULL DEFAULT 0,
    tokens_used BIGINT NOT NULL DEFAULT 0,
    skill_executions INT NOT NULL DEFAULT 0,
    file_operations INT NOT NULL DEFAULT 0,
    peak_concurrent_sessions INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_date (user_id, usage_date),
    INDEX idx_date (usage_date)
);

-- 创建索引加速查询
CREATE INDEX IF NOT EXISTS idx_subscription_user ON user_subscription(user_id);
CREATE INDEX IF NOT EXISTS idx_subscription_plan ON user_subscription(plan_id);
CREATE INDEX IF NOT EXISTS idx_quota_updated ON user_quota(updated_at);

-- 创建触发器自动更新 updated_at
CREATE TRIGGER IF NOT EXISTS update_user_quota_timestamp
BEFORE UPDATE ON user_quota
FOR EACH ROW
SET NEW.updated_at = CURRENT_TIMESTAMP;

CREATE TRIGGER IF NOT EXISTS update_user_subscription_timestamp
BEFORE UPDATE ON user_subscription
FOR EACH ROW
SET NEW.updated_at = CURRENT_TIMESTAMP;
