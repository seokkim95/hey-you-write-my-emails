package com.vibe.emailagent.run;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;

import com.vibe.emailagent.config.EmailAgentRunnerProperties;
import com.vibe.emailagent.gmail.GmailClient;
import com.vibe.emailagent.gmail.GmailMessageSummary;
import com.vibe.emailagent.service.EmailAgentService;
import com.vibe.emailagent.service.EmailDraft;

/**
 * 기본 모드(default profile): "write"로 표시된 초안 후보들을 찾아 RAG 기반 답장 Draft를 생성하는 1회성 러너.
 *
 * 요구사항 해석
 * - AWS EventBridge 등 외부 스케줄러가 이 애플리케이션을 "주기적으로 실행"한다.
 * - 따라서 앱 내부에서는 @Scheduled 로 계속 돌기보다는, 실행되면 한번 처리하고 종료하는 방식이 적합.
 *
 * 현재 Phase(boilerplate)
 * - 아직 Gmail API에서 "write" Draft/Label 기반 조회 기능을 구현하지 않았습니다.
 * - 그래서 임시로 기존 API(findUnrepliedMessagesSince)를 재사용합니다.
 *
 * 다음 단계에서 할 일
 * - GmailClient에 findWriteDraftCandidates() 같은 메서드를 추가하고
 * - Gmail에서 "write"로 표시된 draft/라벨만 가져오도록 구현을 교체하세요.
 */
@Component
@Profile("!ingest & !gmail-test")
@ConditionalOnProperty(prefix = "emailagent.runner", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EmailAutomationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EmailAutomationRunner.class);

    private final GmailClient gmailClient;
    private final EmailAgentService emailAgentService;
    private final EmailAgentRunnerProperties runnerProperties;
    private final ConfigurableApplicationContext applicationContext;

    public EmailAutomationRunner(GmailClient gmailClient,
                                EmailAgentService emailAgentService,
                                EmailAgentRunnerProperties runnerProperties,
                                ConfigurableApplicationContext applicationContext) {
        this.gmailClient = gmailClient;
        this.emailAgentService = emailAgentService;
        this.runnerProperties = runnerProperties;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int lookback = Math.max(1, runnerProperties.lookbackHours());

            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime since = now.minus(lookback, ChronoUnit.HOURS);

            log.info("[Runner] Start email automation. lookbackHours={}, since={}", lookback, since);

            List<GmailMessageSummary> candidates = gmailClient.findUnrepliedMessagesSince(since);
            if (candidates.isEmpty()) {
                log.info("[Runner] No candidate messages found. Exiting.");
                return;
            }

            for (GmailMessageSummary msg : candidates) {
                try {
                    // Phase 4-> 변경 예정: currentQuestion을 snippet으로 임시 대체
                    String currentQuestion = msg.snippet();

                    EmailDraft draft = emailAgentService.generateDraft(msg.threadId(), currentQuestion, msg.subject());

                    String draftId = gmailClient.createReplyDraft(msg.messageId(), msg.threadId(), draft.subject(), draft.body());

                    log.info("[Runner] Draft created. messageId={}, threadId={}, draftId={}", msg.messageId(), msg.threadId(), draftId);
                } catch (Exception e) {
                    log.warn("[Runner] Failed to process messageId={}, threadId={}: {}", msg.messageId(), msg.threadId(), e.getMessage(), e);
                }
            }

            log.info("[Runner] Email automation finished.");
        } finally {
            applicationContext.close();
        }
    }
}
