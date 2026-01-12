# emailagent

 Automatic email generation system based on given rules and historical conversation. RAG based approach. Can be run on local or on cloud servers.

## What this repo currently contains
- Java 17 + Spring Boot (batch-style, no embedded web server)
- Gmail API connectivity + test runner
- RAG scaffolding (Context collection, Draft generation)
- Ingestion mode (Gmail -> Embedding -> VectorStore + DB)

## Run modes (Spring Profiles)
This application is designed to run **once and exit**.

- Default mode: `EmailAutomationRunner` (draft generation)
- Ingestion mode: `InitialIngestionRunner` (ingest Gmail history)
- Gmail connectivity test: `GmailFetchTestRunner`

### 1) Gmail connectivity test (no DB required)
Profile: `gmail-test`

Example (jar):
```bash
java \
  -Dgmail.enabled=true \
  -Dgmail.oauth.client-id=YOUR_CLIENT_ID \
  -Dgmail.oauth.client-secret=YOUR_CLIENT_SECRET \
  -Dgmail.oauth.refresh-token=YOUR_REFRESH_TOKEN \
  -jar target/emailagent-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=local,gmail-test
```

### 1b) Gmail test ingestion (writes to DB / VectorStore)

Profile: `gmail-test` (requires DB connection this time)

This runs a small ingestion job (default: 100 messages) and writes to `email_embeddings`.

Example (jar):
```bash
java \
  -Dspring.profiles.active=local,gmail-test \
  -Dgmail.enabled=true \
  -Dgmail.oauth.client-id=YOUR_CLIENT_ID \
  -Dgmail.oauth.client-secret=YOUR_CLIENT_SECRET \
  -Dgmail.oauth.refresh-token=YOUR_REFRESH_TOKEN \
  -DOPENAI_API_KEY=YOUR_OPENAI_KEY \
  -DDB_HOST=... -DDB_PORT=5432 -DDB_NAME=... -DDB_USER=... -DDB_PASSWORD=... \
  -jar target/emailagent-0.0.1-SNAPSHOT.jar \
  --gmailTestIngest=true \
  --gmailTestMaxMessages=100 \
  --gmailTestPageSize=20
```

### 2) Initial / incremental ingestion
Profile: `ingest`

Config:
- `emailagent.ingestion.lookback-hours`
  - `<= 0`: ingest "all" (inbox)
  - `> 0`: ingest only recent N hours (`newer_than:Nh`)
- `emailagent.ingestion.max-messages`: safety limit per run
- `emailagent.ingestion.page-size`: Gmail API list page size

Example (jar):
```bash
java \
  -Dgmail.enabled=true \
  -Dgmail.oauth.client-id=YOUR_CLIENT_ID \
  -Dgmail.oauth.client-secret=YOUR_CLIENT_SECRET \
  -Dgmail.oauth.refresh-token=YOUR_REFRESH_TOKEN \
  -DOPENAI_API_KEY=YOUR_OPENAI_KEY \
  -jar target/emailagent-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=local,ingest \
  --emailagent.ingestion.lookback-hours=24 \
  --emailagent.ingestion.max-messages=500
```

## Vector store recommendation (AWS)
If you want to stay on AWS, these are practical options:

1) **Amazon Aurora PostgreSQL + pgvector**
- Pros: managed Postgres, SQL + pgvector in one place, simple operations, works well with Spring AI pgvector store
- Cons: not a dedicated vector DB, but good enough for many RAG workloads

2) **Amazon OpenSearch Service (Vector Search)**
- Pros: scalable, good for hybrid search (keyword + vector) and filtering
- Cons: integration is different from pgvector; operational tuning required

3) **Amazon Bedrock Knowledge Bases / Agents (managed RAG)**
- Pros: fully managed ingestion + retrieval pipeline
- Cons: less control / vendor-specific, may not match "store everything yourself" approach

For this repo today, the lowest friction is **Aurora Postgres + pgvector** because your current stack already uses Postgres and pgvector.

more to come..
