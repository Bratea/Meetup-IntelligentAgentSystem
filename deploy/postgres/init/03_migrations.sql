-- 增量迁移：为已存在的库补列（幂等，可重复执行）
ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS error_message TEXT;
