-- V11__subagent_run_batch_fields.sql
-- Map-Reduce 批次支持：扩展 subagent_run 表

-- 批次 ID：关联同一批次的子任务
ALTER TABLE subagent_run
ADD COLUMN batch_id VARCHAR(64) DEFAULT NULL;

-- 批次总任务数
ALTER TABLE subagent_run
ADD COLUMN batch_total INT DEFAULT 1;

-- 批次内序号（从 0 开始）
ALTER TABLE subagent_run
ADD COLUMN batch_index INT DEFAULT 0;

-- 主 Agent 运行 ID（用于批次完成后唤醒）
ALTER TABLE subagent_run
ADD COLUMN main_run_id VARCHAR(64) DEFAULT NULL;

-- 索引：按批次 ID 查询（Fan-in 场景）
CREATE INDEX IF NOT EXISTS idx_subagent_run_batch ON subagent_run (batch_id);

-- 索引：按主运行 ID 查询（唤醒主 Agent 场景）
CREATE INDEX IF NOT EXISTS idx_subagent_run_main ON subagent_run (main_run_id);

-- 注释说明
COMMENT ON COLUMN subagent_run.batch_id IS '批次 ID，用于关联同一批次的子任务（Map-Reduce 模式）';
COMMENT ON COLUMN subagent_run.batch_total IS '批次总任务数，用于检测批次是否全部完成';
COMMENT ON COLUMN subagent_run.batch_index IS '批次内序号，从 0 开始';
COMMENT ON COLUMN subagent_run.main_run_id IS '主 Agent 运行 ID，批次完成后用于唤醒主线程';
