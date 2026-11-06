-- =============================================================
-- 寻晤智能agent系统 - 数据库表结构
-- 数据库: xunwu_agent
-- =============================================================

-- ---------- 智能体 ----------
CREATE TABLE IF NOT EXISTS agent (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(128) NOT NULL,
    description   VARCHAR(512),
    system_prompt TEXT,
    avatar        VARCHAR(255),
    model_id      VARCHAR(64)  NOT NULL DEFAULT 'qwen2.5:7b',
    tool_ids      JSONB        NOT NULL DEFAULT '[]',
    max_steps     INT          NOT NULL DEFAULT 8,
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE agent IS '智能体配置';
COMMENT ON COLUMN agent.tool_ids IS '启用的工具名称列表，JSON 数组';
COMMENT ON COLUMN agent.max_steps IS '单次任务最大执行步数（防止无限循环）';

-- ---------- 会话 ----------
CREATE TABLE IF NOT EXISTS conversation (
    id          BIGSERIAL PRIMARY KEY,
    agent_id    BIGINT       NOT NULL REFERENCES agent(id) ON DELETE CASCADE,
    title       VARCHAR(255) NOT NULL DEFAULT '新会话',
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_conversation_agent ON conversation(agent_id);
CREATE INDEX IF NOT EXISTS idx_conversation_created ON conversation(created_at DESC);

-- ---------- 消息 ----------
CREATE TABLE IF NOT EXISTS message (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT       NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
    role            VARCHAR(16)  NOT NULL,   -- user / assistant / system / tool
    content         TEXT         NOT NULL,
    model           VARCHAR(64),
    tool_calls      JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_message_conversation ON message(conversation_id, id);
COMMENT ON COLUMN message.tool_calls IS '工具调用记录，JSON 数组';

-- ---------- 知识库文档 ----------
CREATE TABLE IF NOT EXISTS knowledge_document (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    content       TEXT         NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PROCESSING',  -- PROCESSING / READY / FAILED
    segment_count INT          NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE knowledge_document IS '知识库原始文档';

-- ---------- 知识库分段（向量） ----------
CREATE TABLE IF NOT EXISTS knowledge_segment (
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT       NOT NULL REFERENCES knowledge_document(id) ON DELETE CASCADE,
    chunk_index INT          NOT NULL,
    content     TEXT         NOT NULL,
    embedding   vector(1024) NOT NULL,          -- bge-m3 输出维度
    metadata    JSONB,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_knowledge_segment_doc ON knowledge_segment(document_id);

-- HNSW 索引：加速大规模向量相似度检索（余弦距离）
CREATE INDEX IF NOT EXISTS idx_knowledge_segment_embedding_hnsw
    ON knowledge_segment USING hnsw (embedding vector_cosine_ops);

COMMENT ON TABLE knowledge_segment IS '知识库文本分段及向量，使用 HNSW 索引加速检索';
