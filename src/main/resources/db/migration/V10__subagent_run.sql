-- V10__subagent_run.sql
-- 子 Agent 运行记录表（DB-first，配合 reconcile 恢复）
-- PostgreSQL 与 H2 通用语法

CREATE TABLE IF NOT EXISTS subagent_run (
    run_id VARCHAR(96) PRIMARY KEY,
    parent_run_id VARCHAR(96),
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    session_id VARCHAR(64) NOT NULL,
    app_id VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    goal TEXT,
    spec_json TEXT,
    result TEXT,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deadline_at TIMESTAMP,
    ended_at TIMESTAMP,
    depth INT NOT NULL DEFAULT 0,
    token_budget INT NOT NULL DEFAULT 8000,
    allowed_tools TEXT,
    retry_count INT NOT NULL DEFAULT 0
);

-- 索引：按会话和状态查询（恢复场景）
CREATE INDEX IF NOT EXISTS idx_subagent_run_session_status ON subagent_run (session_id, status);

-- 索引：按租户和状态查询（多租户隔离）
CREATE INDEX IF NOT EXISTS idx_subagent_run_tenant_status ON subagent_run (tenant_id, status);

-- 索引：按截止时间查询（超时检测）
CREATE INDEX IF NOT EXISTS idx_subagent_run_deadline ON subagent_run (deadline_at);

-- 索引：按父运行 ID 查询（父子关系追踪）
CREATE INDEX IF NOT EXISTS idx_subagent_run_parent ON subagent_run (parent_run_id);
