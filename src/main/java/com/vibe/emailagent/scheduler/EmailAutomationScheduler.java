package com.vibe.emailagent.scheduler;

/**
 * (Deprecated)
 *
 * 기존에는 앱 내부에서 @Scheduled로 1시간마다 동작시키려 했지만,
 * 현재는 AWS EventBridge 같은 외부 스케줄러가 주기적으로 애플리케이션을 실행하는 방식을 사용합니다.
 *
 * 따라서 실제 처리는 ApplicationRunner 기반의
 * - com.vibe.emailagent.run.EmailAutomationRunner (기본 모드)
 * - com.vibe.emailagent.run.InitialIngestionRunner (ingest 프로파일)
 * 에서 수행합니다.
 *
 * TODO: 안정화 후 이 클래스를 삭제해도 됩니다.
 */
public final class EmailAutomationScheduler {
    private EmailAutomationScheduler() {
    }
}
