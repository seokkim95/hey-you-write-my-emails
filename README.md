# emailagent

Automatic email generation system based on given rules and historical conversation. RAG based approach. Can be run on local or on cloud servers.

more to come..

## Storage design (current)

Single source of truth:
- **VectorStore table: `email_embeddings`** (Postgres + pgvector)
  - Used for both:
    - thread reconstruction (by `thread_id`)
    - similarity search (RAG retrieval)
- `business_rule` table is used for latest business rules.

## Database setup

### Option A: Neon (recommended for now)

Neon is just Postgres, so you can use it exactly like RDS later.

1) Create a Neon Postgres database.
2) Ensure pgvector is available/enabled.
3) Configure JDBC connection via environment variables.

Example VM args (IntelliJ)

```bash
-Dspring.profiles.active=local \
-DDB_HOST=<neon-host> \
-DDB_PORT=5432 \
-DDB_NAME=<neon-db> \
-DDB_USER=<neon-user> \
-DDB_PASSWORD=<neon-password>
```

If your Neon JDBC URL requires SSL, prefer setting `DB_HOST` to the correct host and configure SSL in the URL.
(We can refine this once you share your Neon connection string.)

### Option B: Local Postgres

```bash
-Dspring.profiles.active=local \
-DDB_HOST=localhost \
-DDB_PORT=5432 \
-DDB_NAME=emailagent \
-DDB_USER=postgres \
-DDB_PASSWORD=postgres
```

Flyway migrations run automatically on startup.

## Running modes

### 1) Default mode (draft automation)

Runs once and exits.

```bash
-Dspring.profiles.active=local
```

### 2) Ingestion mode

Ingests Gmail messages and stores embeddings into `email_embeddings`.

```bash
-Dspring.profiles.active=local,ingest
```

## Gmail

Gmail calls are enabled only when `gmail.enabled=true`.
Provide credentials via VM args/env.

```bash
-Dgmail.enabled=true \
-Dgmail.oauth.client-id=... \
-Dgmail.oauth.client-secret=... \
-Dgmail.oauth.redirect-uri=... \
-Dgmail.oauth.refresh-token=...
```
