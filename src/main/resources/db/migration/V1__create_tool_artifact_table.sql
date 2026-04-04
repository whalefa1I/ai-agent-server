-- ToolArtifact 表初始化脚本 (H2 Database)
-- 用于开发环境，生产环境请使用 PostgreSQL/MySQL

CREATE TABLE IF NOT EXISTS tool_artifact (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,

    -- Header: 工具元数据 (JSON)
    header TEXT NOT NULL,
    header_version INT DEFAULT 0,

    -- Body: 工具状态详情 (JSON)
    body TEXT,
    body_version INT DEFAULT 0,

    seq BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- 索引
    INDEX idx_account (account_id),
    INDEX idx_session (session_id),
    INDEX idx_updated (account_id, updated_at DESC)
);

-- 初始化数据（可选，用于测试）
-- INSERT INTO tool_artifact (id, session_id, account_id, header, header_version, body, body_version, seq)
-- VALUES ('test-1', 'session-1', 'user-1',
--         '{"name":"BashTool","type":"tool","status":"todo","version":1}', 1,
--         '{"todo":"Execute ls -la","version":1}', 1, 0);
