-- Core tables for RAG Email Agent
-- Note: you must have permissions to create extensions.

CREATE EXTENSION IF NOT EXISTS vector;

-- Stores embedded chunks of historical emails/conversations.
-- We'll refine schema during later steps.
CREATE TABLE IF NOT EXISTS email_embeddings (
    id BIGSERIAL PRIMARY KEY,
    thread_id TEXT,
    message_id TEXT,
    sender TEXT,
    recipients TEXT,
    subject TEXT,
    content TEXT,
    created_at TIMESTAMPTZ DEFAULT now(),
    metadata JSONB,
    embedding vector(1536)
);

CREATE INDEX IF NOT EXISTS email_embeddings_thread_id_idx ON email_embeddings(thread_id);

-- Business rules: 최신 정책/가격/공지 등, RAG context에 항상 최신을 우선 적용
CREATE TABLE IF NOT EXISTS business_rules (
    id BIGSERIAL PRIMARY KEY,
    rule_key TEXT NOT NULL,
    rule_value TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    effective_from TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(rule_key, version)
);

CREATE INDEX IF NOT EXISTS business_rules_key_effective_idx ON business_rules(rule_key, effective_from DESC);

