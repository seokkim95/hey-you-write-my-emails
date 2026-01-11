-- Core tables for RAG Email Agent
--
-- 이 마이그레이션은 "프로젝트 초기 실험"과 "Step 1 요구사항"이 함께 들어가 있습니다.
-- 그래서 유사 목적 테이블이 2쌍 존재합니다:
-- - email_embeddings (초기 스캐폴딩/실험용) vs email_history (요구사항 엔티티용)
-- - business_rules (버전/유효기간 모델) vs business_rule (요구사항 엔티티용, 단순 최신값)
--
-- 권장 정리 방향(추후 Phase):
-- 1) 실제 ingestion/search에 사용할 테이블을 하나로 확정
--    - Spring AI pgvector store의 요구 스키마와 맞추려면 email_embeddings 쪽 형태가 더 유리할 수 있음
-- 2) business_rule을 계속 쓸지, business_rules(버전 관리)로 승격할지 결정
--
-- Note:
-- - `CREATE EXTENSION vector;` 는 DB 권한이 필요합니다.
-- - 로컬/운영 환경에서 권한이 없으면 Flyway 단계에서 실패할 수 있으니 사전에 DBA/infra에서 준비하세요.

CREATE EXTENSION IF NOT EXISTS vector;

-- =========================
-- Step 0 (기존 스캐폴딩)
-- =========================
-- Stores embedded chunks of historical emails/conversations.
-- We'll refine schema during later steps.
--
-- 컬럼 설명
-- - thread_id/message_id: Gmail 식별자용
-- - metadata JSONB: 확장 필드(라벨, 태그, 파서 결과 등)
-- - embedding vector(1536): 임베딩 벡터
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

-- Business rules: 최신 정책/가격/공지 등, RAG context에서 항상 최신을 우선 적용
--
-- 이 테이블은 "버전/유효기간"을 가진 모델을 염두에 둔 스캐폴딩입니다.
-- - (rule_key, version) 유니크
-- - effective_from으로 "언제부터 적용되는지"를 표현
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

-- =========================
-- Step 1 (요구사항 엔티티)
-- =========================
-- EmailHistory: thread_id, snippet, full_content, embedding(vector)
--
-- 단순히 "스레드 대화/원문"을 저장하는 테이블.
-- - 실제 RAG 운영에서는 메시지를 chunking 해서 여러 row로 적재하는 방식이 흔합니다.
-- - 그 경우 email_embeddings 같은 구조가 더 적합하므로, 추후 통합 설계를 추천합니다.
CREATE TABLE IF NOT EXISTS email_history (
    id BIGSERIAL PRIMARY KEY,
    thread_id TEXT NOT NULL,
    snippet TEXT,
    full_content TEXT,
    embedding vector(1536)
);

CREATE INDEX IF NOT EXISTS email_history_thread_id_idx ON email_history(thread_id);

-- BusinessRule: rule_key, rule_content, updated_at
--
-- 단일 최신값 모델(요구사항 충족용).
-- - key당 1 row
-- - 업데이트 시 기존 row를 UPDATE 하는 사용 패턴
CREATE TABLE IF NOT EXISTS business_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_key TEXT NOT NULL UNIQUE,
    rule_content TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
