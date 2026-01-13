# emailagent

RAG-based AI email drafting agent.

**Core idea**
- Read historical Gmail messages → embed → store in Postgres/pgvector (`email_embeddings`).
- When new email arrives, retrieve:
  1) current thread context (reconstructed from `email_embeddings`)
  2) similar past chunks via vector search
  3) latest business rules from `business_rule`
- Generate a reply draft via Spring AI + OpenAI and save it to Gmail Drafts.

> This is a **batch-style** Spring Boot app: it runs once and exits.
> Run it on a schedule using AWS EventBridge (or any external scheduler), not `@Scheduled`.

---

## Tech stack

- Java 17
- Spring Boot 3.4.x
- Spring AI (OpenAI) + pgvector VectorStore
- PostgreSQL (Neon recommended for early stage - Well at least I used Neon as I am running it as a hourly cron job using the Eventbridge. If you intend to have it running jobs more frequently, consider RDS (Aurora) or other options. pgvector extension enabled.)
- Gmail API (OAuth via refresh token)
- Flyway for schema migrations

---

## Storage design (current)

Single source of truth:
- **VectorStore backing table: `email_embeddings`** (Postgres + pgvector)
  - Used for both:
    - thread reconstruction (via `metadata.thread_id`)
    - similarity search (RAG retrieval)
- **`business_rule`** table
  - Stores latest business rules (price, policy, etc.)
  - Always has higher priority than historical email content.

### Schema (Flyway)
Migrations live in:
- `src/main/resources/db/migration/V1__init_pgvector_and_core_tables.sql`

It creates:
- `email_embeddings(id UUID PK, content TEXT, metadata JSONB, embedding vector(N), created_at TIMESTAMPTZ)`
- `business_rule(id, rule_key UNIQUE, rule_content, updated_at)`

> Important: `email_embeddings.id` **must be UUID** for Spring AI PgVectorStore (1.0.0-M6).

---

## Running modes (profiles)

This project is profile-driven.

### 1) Draft automation mode (default production behavior)

Profile: `automation`

What it does:
- Finds candidate inbound emails in the last **24 hours** (robust to scheduler delays)
- Groups by `threadId` and drafts **only for the latest message in each thread**
- For the LLM prompt, fetches the entire thread and includes the inbound messages (best effort)
- Saves the generated draft to Gmail Drafts

Main class:
- `com.vibe.emailagent.run.EmailAutomationRunner`

### 2) Initial ingestion mode

Profile: `ingest`

What it does:
- Fetches Gmail messages (inbox + sent by default)
- Cleans the body → chunks it → `vectorStore.add(...)`
- De-dupes by checking `email_embeddings.metadata.message_id` before embedding

Main class:
- `com.vibe.emailagent.run.InitialIngestionRunner`

---

## Configuration
- `src/main/resources/application.yml` (defaults)

### OpenAI
Required:
- `OPENAI_API_KEY`

Optional:
- `OPENAI_CHAT_MODEL` (default: `gpt-4o-mini`)
- `OPENAI_EMBEDDING_MODEL` (default in local: `text-embedding-3-small`)
- `OPENAI_EMBEDDING_DIMENSIONS` (default: `1536`)

> If you switch embedding model to `text-embedding-3-large`, update dimensions to `3072` and migrate schema.

### Gmail OAuth
Gmail beans are created only if:
- `gmail.enabled=true`

This project uses **refresh token** based OAuth (recommended for server/batch workloads).

Required properties:
- `gmail.oauth.client-id`
- `gmail.oauth.client-secret`
- `gmail.oauth.refresh-token`
- `gmail.oauth.redirect-uri` (used when you generate the refresh token; not used at runtime after that)

> Implementation: `com.vibe.emailagent.gmail.GoogleCredentialsGmailAuthProvider`

---

## Database setup

### Option A: Neon (recommended)

Neon is standard Postgres. You can migrate to AWS RDS later with minimal changes.

1) Create a Neon Postgres database.
2) Ensure pgvector is enabled (`CREATE EXTENSION vector;`).
3) Set DB connection.

Local profile supports `DB_URL` directly (recommended):

```bash
-Dspring.profiles.active=local \
-DDB_URL="jdbc:postgresql://<neon-host>/<db>?sslmode=require" \
-DDB_USER=<user> \
-DDB_PASSWORD=<password>
```

### Option B: Local Postgres (For early stage development)

```bash
-Dspring.profiles.active=local \
-DDB_URL="jdbc:postgresql://localhost:5432/emailagent" \
-DDB_USER=postgres \
-DDB_PASSWORD=postgres
```

Flyway migrations run automatically on startup.

---

## How to run (IntelliJ VM options examples)

### 1) Draft automation

```bash
-Dspring.profiles.active=automation \
-DOPENAI_API_KEY=... \
-DGMAIL_ENABLED=true \
-DGMAIL_CLIENT_ID=... \
-DGMAIL_CLIENT_SECRET=... \
-DGMAIL_REFRESH_TOKEN=... \
-DGMAIL_REDIRECT_URI=...
```

### 2) Initial ingestion

```bash
-Dspring.profiles.active=ingest \
-DOPENAI_API_KEY=... \
-DGMAIL_ENABLED=true \
-DGMAIL_CLIENT_ID=... \
-DGMAIL_CLIENT_SECRET=... \
-DGMAIL_REFRESH_TOKEN=... \
-DGMAIL_REDIRECT_URI=...
```

---

## Flyway notes

- `flyway-maven-plugin` does **not** read Spring Boot `application*.yml` automatically.
- If you use `mvn flyway:*`, you must pass:
  - `-Dflyway.url=... -Dflyway.user=... -Dflyway.password=...`

---

## Debugging

### OpenAI request payload debugging
Enable temporarily in `application-local.yml`:

- `org.springframework.ai.openai: DEBUG`
- `org.springframework.web.reactive.function.client.ExchangeFunctions: TRACE`

This helps when you see errors like:
- `"'$.input' is invalid"`

### Gmail troubleshooting
Common errors:
- `invalid_grant`: refresh token revoked/expired or client settings mismatch

---

## Project structure (high level)

- `com.vibe.emailagent.run.*`
  - `EmailAutomationRunner` (draft automation)
  - `InitialIngestionRunner` (ingestion)
- `com.vibe.emailagent.gmail.*`
  - `GmailApiClient` (Gmail API)
  - `GoogleCredentialsGmailAuthProvider` (OAuth refresh token)
- `com.vibe.emailagent.service.*`
  - `EmailIngestionService` (Gmail → chunk → vectorStore)
  - `EmailContextService` (thread reconstruction + similarity search + business rules)
  - `EmailAgentService` (prompt engineering + LLM call)

---

## Next steps

- I have no immediate plan to add more features yet.