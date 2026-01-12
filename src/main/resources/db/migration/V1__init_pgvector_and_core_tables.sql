-- Core tables for RAG Email Agent (single-source schema)
--
-- This project uses Postgres + pgvector and treats `email_embeddings` as the single source of truth.
--
-- Tables
-- - email_embeddings: VectorStore backing table (documents + embeddings + metadata)
-- - business_rule: single-latest business rules (source of truth for drafting)
--
-- Notes
-- - `CREATE EXTENSION vector;` requires DB privileges.
-- - If the environment user cannot create extensions, Flyway may fail.

CREATE EXTENSION IF NOT EXISTS vector;

-- =========================
-- VectorStore backing table
-- =========================
CREATE TABLE IF NOT EXISTS email_embeddings (
    -- VectorStore document id.
    -- Spring AI PgVectorStore (1.0.0-M6) treats ids as UUIDs by default.
    id UUID PRIMARY KEY,
    thread_id TEXT,
    sender TEXT,
    recipients TEXT,
    subject TEXT,
    content TEXT,
    created_at TIMESTAMPTZ DEFAULT now(),
    metadata JSONB,
    embedding vector(1536)
);

-- Thread reconstruction
CREATE INDEX IF NOT EXISTS email_embeddings_thread_id_idx ON email_embeddings(thread_id);

-- Useful for recent-first queries
CREATE INDEX IF NOT EXISTS email_embeddings_created_at_idx ON email_embeddings(created_at DESC);

-- =========================
-- Latest business rules
-- =========================
CREATE TABLE IF NOT EXISTS business_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_key TEXT NOT NULL UNIQUE,
    rule_content TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
