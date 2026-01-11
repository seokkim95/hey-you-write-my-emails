# emailagent

RAG 기반 AI 이메일 자동 드래프트 에이전트 (스캐폴딩)

## 핵심 요약
이 프로젝트는 **애플리케이션 내부 스케줄러(@Scheduled)** 대신,
**AWS EventBridge 같은 외부 크론 스케줄러가 주기적으로 애플리케이션을 실행**하는 방식을 전제로 합니다.

즉,
- 앱이 실행되면 **한 번 작업을 수행하고 종료**합니다.
- 실행 모드는 **Spring Profile**로 분리되어 있습니다.

## 실행 모드(Profiles)

### 1) 기본 모드 (default) — 이메일 드래프트 자동 생성
- 프로파일을 아무것도 지정하지 않으면 기본 모드로 실행됩니다.
- 현재 스캐폴딩 기준 동작:
  - Gmail에서 "처리 대상" 메일을 조회
  - RAG 컨텍스트를 모아(OpenAI/VectorStore/룰)
  - 답장 초안을 생성
  - Gmail Drafts에 저장

> 참고: 현재는 "write" 라벨/드래프트 기반 조회를 완전히 구현하기 전이라,
> 임시로 `findUnrepliedMessagesSince(...)` 방식의 skeleton을 사용하고 있습니다.

### 2) ingest 모드 — 초기 데이터 적재(Initial Ingestion)
- `ingest` 프로파일로 실행하면 초기 적재 모드로 실행됩니다.
- 목적:
  - Gmail 과거 이메일 전체를 가져와
  - 임베딩 후 pgvector(VectorStore)에 저장

> 현재는 "골격 + TODO"만 있는 상태입니다.

---

## 로컬 실행 방법

### (A) Maven으로 실행

#### 기본 모드(default)
```bash
./mvnw spring-boot:run
```

#### ingest 모드
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=ingest
```

추가 옵션을 주고 싶다면(예: 최근 6시간만 처리):
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--emailagent.runner.lookback-hours=6"
```

### (B) jar 빌드 후 실행

빌드:
```bash
./mvnw -DskipTests package
```

기본 모드(default):
```bash
java -jar target/emailagent-0.0.1-SNAPSHOT.jar
```

ingest 모드:
```bash
java -jar target/emailagent-0.0.1-SNAPSHOT.jar --spring.profiles.active=ingest
```

기본 모드 + 옵션(최근 N시간):
```bash
java -jar target/emailagent-0.0.1-SNAPSHOT.jar --emailagent.runner.lookback-hours=6
```

---

## 운영 실행 예시 (AWS EventBridge)
EventBridge에서 cron schedule로 컨테이너/EC2를 실행할 때,
위의 jar 실행 커맨드를 그대로 사용하면 됩니다.

- 기본 모드(주기 실행):
  - 예: 1시간마다 실행 → EventBridge의 cron으로 app을 실행
- ingest 모드(1회 실행):
  - 앱을 한 번만 실행하고 종료

---

## 주요 설정/환경변수

### 1) DB (PostgreSQL + pgvector)
`application.yml`은 아래 환경변수로 오버라이드 가능하게 되어 있습니다.
- `DB_HOST` (default: localhost)
- `DB_PORT` (default: 5432)
- `DB_NAME` (default: emailagent)
- `DB_USER` (default: postgres)
- `DB_PASSWORD` (default: postgres)

### 2) OpenAI (Spring AI)
- `OPENAI_API_KEY`

> 현재는 OpenAI를 먼저 쓰는 방향으로 시작하면,
> `spring.ai.openai.api-key`만 설정해도 ChatClient가 동작하도록 구성되어 있습니다.

### 3) Gmail API
현재 Gmail 연동은 "뼈대"이며, OAuth 구현은 `GmailAuthProvider` 구현체로 추후 추가합니다.

- Gmail API 호출 활성화 플래그
  - `GMAIL_ENABLED` (default: false)

- OAuth 관련(추후 구현에서 사용)
  - `GMAIL_CLIENT_ID`
  - `GMAIL_CLIENT_SECRET`
  - `GMAIL_REDIRECT_URI`
  - `GMAIL_REFRESH_TOKEN`

---

## 현재 포함된 주요 구성요소
- Flyway 마이그레이션: `V1__init_pgvector_and_core_tables.sql`
- Health endpoint: `GET /api/health`
- RAG 컨텍스트 수집: `EmailContextService`
- 답장 초안 생성: `EmailAgentService` (Spring AI ChatClient)
- 1회 실행 러너:
  - 기본 모드: `EmailAutomationRunner`
  - ingest 모드: `InitialIngestionRunner`

---

## 다음 단계(구현 예정)
- Gmail "write" 라벨/드래프트 기반 후보 조회 구현
- Gmail Draft 생성 시 RFC822(MIME) raw 생성 + thread reply 헤더(In-Reply-To/References) 구성
- Initial ingestion: 전체 메일 페이징 + chunking + embedding + VectorStore upsert

## 로컬에서 Gmail OAuth를 VM args로 주입하기 (권장)

> 주의: `client-secret`, `refresh-token`은 민감정보라서 yml 파일에 박지 않는 걸 권장합니다.

### 1) gmail-test 러너로 "최근 5개 이메일" 출력 테스트
Spring Profile `gmail-test`를 켜면 `GmailFetchTestRunner`가 실행되어 최근 이메일 5개를 가져와 로그로 출력합니다.

#### Maven 실행 예시
```bash
./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=local,gmail-test \
  -Dspring-boot.run.jvmArguments="\
    -Dgmail.enabled=true \
    -Dgmail.oauth.client-id=YOUR_CLIENT_ID \
    -Dgmail.oauth.client-secret=YOUR_CLIENT_SECRET \
    -Dgmail.oauth.refresh-token=YOUR_REFRESH_TOKEN \
    -Dgmail.oauth.redirect-uri=https://developers.google.com/oauthplayground"
```

#### jar 실행 예시
```bash
java \
  -Dgmail.enabled=true \
  -Dgmail.oauth.client-id=YOUR_CLIENT_ID \
  -Dgmail.oauth.client-secret=YOUR_CLIENT_SECRET \
  -Dgmail.oauth.refresh-token=YOUR_REFRESH_TOKEN \
  -Dgmail.oauth.redirect-uri=https://developers.google.com/oauthplayground \
  -jar target/emailagent-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=local,gmail-test
```

> 참고: `gmail-test` 프로파일은 DB/JPA/Flyway 뿐 아니라 Spring AI(OpenAI/Anthropic/VectorStore) 자동구성도 비활성화합니다.
> 그래서 OpenAI API Key 없이도 Gmail 연결 테스트만 실행할 수 있습니다.

### 2) 환경변수로도 가능
VM args 대신 환경변수로 주입하면:
- `GMAIL_ENABLED=true`
- `GMAIL_CLIENT_ID=...`
- `GMAIL_CLIENT_SECRET=...`
- `GMAIL_REFRESH_TOKEN=...`
- `GMAIL_REDIRECT_URI=...`

## 실행 형태 (웹서버 없음 / 1회 실행 후 종료)
이 애플리케이션은 기본적으로 `spring.main.web-application-type=none`으로 설정되어 있어
내장 웹서버(Tomcat 등)를 띄우지 않습니다.

- 실행되면 Runner가 작업을 수행하고
- 작업이 끝나면 ApplicationContext를 close 하여 프로세스가 종료됩니다.

(필요하면 나중에 `web` 프로파일을 별도로 만들어 health endpoint 등을 다시 활성화할 수 있습니다.)
