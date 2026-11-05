-- =============================================================
-- 寻晤智能agent系统 - 扩展初始化
-- 在 PostgreSQL 中启用 pgvector 向量检索扩展
-- =============================================================

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;   -- 可选：模糊文本检索辅助

-- 校验扩展安装
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        RAISE EXCEPTION 'pgvector 扩展安装失败';
    END IF;
END $$;
