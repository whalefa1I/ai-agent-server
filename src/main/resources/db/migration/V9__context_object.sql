-- V9__context_object.sql
-- 外置超长工具产物 / 压缩上下文指针（DB-first，配合 read_context_object 工具）
-- PostgreSQL 与 H2 通用语法：标准类型 + 独立 CREATE INDEX

CREATE TABLE IF NOT EXISTS context_object (
    id VARCHAR(96) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    conversation_id VARCHAR(64) NOT NULL,
    run_id VARCHAR(64),
    producer_kind VARCHAR(32) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    content_type VARCHAR(64) NOT NULL DEFAULT 'text/plain',
    content TEXT,
    storage_uri VARCHAR(512),
    token_estimate INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_context_object_expires_at ON context_object (expires_at);
CREATE INDEX IF NOT EXISTS idx_context_object_conv_tenant ON context_object (conversation_id, tenant_id);
