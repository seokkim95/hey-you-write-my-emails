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

    -- Chunk text content used for embeddings and prompt context.
    content TEXT,

    -- Flexible metadata for Gmail fields (messageId, threadId, subject, from, labels, etc.)
    metadata JSONB,

    -- Vector embedding (must match spring.ai.vectorstore.pgvector.dimensions)
    embedding vector(1536),

    -- Ingestion timestamp (server-side)
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Useful for recent-first queries
CREATE INDEX IF NOT EXISTS email_embeddings_created_at_idx ON email_embeddings(created_at DESC);

-- Fast thread reconstruction via metadata
CREATE INDEX IF NOT EXISTS email_embeddings_thread_id_idx
    ON email_embeddings ((metadata ->> 'thread_id'));

-- Optional: faster de-dup by Gmail message id from metadata
CREATE INDEX IF NOT EXISTS email_embeddings_message_id_idx
    ON email_embeddings ((metadata ->> 'message_id'));

-- =========================
-- Latest business rules
-- =========================
CREATE TABLE IF NOT EXISTS business_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_key TEXT NOT NULL UNIQUE,
    rule_content TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
