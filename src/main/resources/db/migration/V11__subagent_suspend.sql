-- 子 Agent 挂起审批表（v1 Human-in-the-Loop）
-- 用于存储需要人工审批的挂起任务
CREATE TABLE subagent_suspend (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id VARCHAR(96) NOT NULL UNIQUE,
    session_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    reason TEXT,
    approval_required TEXT,
    approver_id VARCHAR(64),
    approval_result VARCHAR(32),
    approval_comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    approved_at TIMESTAMP NULL,
    INDEX idx_session_status (session_id, status),
    INDEX idx_tenant_status (tenant_id, status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
