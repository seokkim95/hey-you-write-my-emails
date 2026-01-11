package com.vibe.emailagent.run;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * ingestion 모드용 1회성 러너.
 *
 * 목적
 * - 초기 1회 실행 시 Gmail의 과거 이메일 전체를 가져와서
 *   임베딩 후 Vector Store(pgvector)에 저장하는 작업.
 *
 * 현재 Phase(boilerplate)
 * - Gmail 전체 수집/페이징, 임베딩 생성, VectorStore upsert 등은 이후 단계에서 구현합니다.
 * - 지금은 profile 분리/실행 흐름의 골격만 제공합니다.
 */
@Component
@Profile({"ingest", "!gmail-test"})
public class InitialIngestionRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialIngestionRunner.class);

    private final ConfigurableApplicationContext applicationContext;

    public InitialIngestionRunner(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("[Ingestion] Initial ingestion job started.");

            // TODO(next):
            // 1) Gmail API로 전체 메일(또는 기간/라벨 범위)을 페이지네이션으로 수집
            // 2) 메일 본문을 chunking + 정제
            // 3) EmbeddingModel로 임베딩 생성
            // 4) VectorStore(pgvector)에 upsert(add)

            log.info("[Ingestion] Initial ingestion job finished (skeleton).");
        } finally {
            applicationContext.close();
        }
    }
}
