-- =============================================================
-- 寻晤智能agent系统 - 扩展初始化
-- 在 PostgreSQL 中启用 pgvector 向量检索扩展
-- =============================================================

CREATE EXTENSION IF NOT EXISTS vector;

-- 校验扩展安装
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        RAISE EXCEPTION 'pgvector 扩展安装失败';
    END IF;
END $$;
