package com.vibe.emailagent.run;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.vibe.emailagent.gmail.GmailClient;
import com.vibe.emailagent.gmail.GmailMessageSummary;

/**
 * 로컬에서 Gmail OAuth/연동이 제대로 되는지 확인하기 위한 테스트 러너.
 *
 * 실행 방법(예시)
 * - application-local.yml을 쓰는 경우:
 *   -Dspring-boot.run.profiles=local,gmail-test
 *
 * 동작
 * - Gmail API로 최근 메일 5개를 가져와서 콘솔 로그로 출력합니다.
 *
 * 주의
 * - gmail.enabled=true여야 실제 GmailApiClient + OAuth Provider가 활성화됩니다.
 * - refresh-token이 유효하지 않으면 401/invalid_grant가 발생할 수 있습니다.
 */
@Component
@Profile("gmail-test")
public class GmailFetchTestRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GmailFetchTestRunner.class);

    private final GmailClient gmailClient;
    private final ConfigurableApplicationContext applicationContext;

    public GmailFetchTestRunner(GmailClient gmailClient, ConfigurableApplicationContext applicationContext) {
        this.gmailClient = gmailClient;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("[GmailTest] Fetching latest 5 messages...");


            // NOTE:
            // - gmail-test 프로파일에서는 DB/JPA/Flyway auto-config가 꺼져 있으므로 Postgres가 없어도 기동됩니다.
            // - GmailApiClient는 현재 "최근 1시간" query + maxResults=5 로 제한해둔 상태입니다.
            List<GmailMessageSummary> messages = gmailClient.findUnrepliedMessagesSince(
                    java.time.OffsetDateTime.now().minusHours(1));

            if (messages.isEmpty()) {
                log.info("[GmailTest] No messages returned.");
                return;
            }

            for (GmailMessageSummary m : messages) {
                log.info("[GmailTest] messageId={}, threadId={}, receivedAt={}, from={}, subject={}, snippet={}",
                        m.messageId(), m.threadId(), m.receivedAt(), m.from(), m.subject(), m.snippet());
            }

            log.info("[GmailTest] Done.");
        } finally {
            // Ensure the application terminates after this one-off job.
            // Without this, some non-daemon threads from libraries may keep the JVM alive.
            applicationContext.close();
        }
    }
}
