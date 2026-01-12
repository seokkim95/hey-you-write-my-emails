package com.vibe.emailagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ingest 모드(초기/증분 적재) 설정.
 *
 * emailagent.ingestion.*
 */
@ConfigurationProperties(prefix = "emailagent.ingestion")
public record  IngestionProperties(
        /**
         * 몇 시간 이내의 이메일만 읽을지.
         * - 0 또는 음수면 "전체"를 의미합니다.
         */
        int lookbackHours,

        /**
         * 한 번 실행에서 최대 몇 개의 메시지를 처리할지.
         * 대량 적재 시 안전장치.
         */
        int maxMessages,

        /**
         * Gmail API list 호출 시 page size.
         */
        int pageSize,

        /**
         * 최대 chunk 당 문자 수.
         *
         * 문자 기반의 이유는?
         * - 토큰 수는 제공자/모델에 따라 다릅니다.
         * - 문자 기반 청크 분할은 간단하고, 제공자에 구애받지 않는 첫 번째 단계입니다.
         */
        int chunkSize,

        /**
         * 경계 간 컨텍스트 유지를 위한 청크 간 중첩(문자 수).
         */
        int chunkOverlap
) {
}
