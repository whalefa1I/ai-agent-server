-- V11__subagent_run_batch_fields_rollback.sql
-- Map-Reduce 批次支持：回滚脚本

-- 删除索引
DROP INDEX IF EXISTS idx_subagent_run_main;
DROP INDEX IF EXISTS idx_subagent_run_batch;

-- 删除列
ALTER TABLE subagent_run DROP COLUMN IF EXISTS main_run_id;
ALTER TABLE subagent_run DROP COLUMN IF EXISTS batch_index;
ALTER TABLE subagent_run DROP COLUMN IF EXISTS batch_total;
ALTER TABLE subagent_run DROP COLUMN IF EXISTS batch_id;

-- 删除注释
COMMENT ON COLUMN subagent_run.batch_id IS NULL;
COMMENT ON COLUMN subagent_run.batch_total IS NULL;
COMMENT ON COLUMN subagent_run.batch_index IS NULL;
COMMENT ON COLUMN subagent_run.main_run_id IS NULL;
