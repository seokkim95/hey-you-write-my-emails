# emailagent

RAG 기반 AI 이메일 자동 드래프트 에이전트 (스캐폴딩)

## 스택
- Java 17
- Spring Boot
- Spring AI
- PostgreSQL + pgvector
- Gmail API

## 로컬 개발 메모
- DB 연결은 `application.properties`의 환경변수(`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`)로 오버라이드할 수 있습니다.
- Gmail OAuth 값은 환경변수(`GMAIL_CLIENT_ID` 등)로 주입하도록 기본 키만 마련되어 있습니다.

## 포함된 것
- Flyway 마이그레이션: `V1__init_pgvector_and_core_tables.sql`
  - `vector` extension
  - `email_embeddings` (vector(1536))
  - `business_rules`
- 스모크 엔드포인트: `GET /api/health`

다음 단계에서 Gmail ingestion, RAG 검색/룰 우선순위, Draft 저장 플로우를 하나씩 추가하면 됩니다.

