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
 * Default automation mode (one-off runner).
 *
 * Target behavior
 * - The app is executed periodically by an external scheduler (e.g., AWS EventBridge).
 * - Therefore, we run once and exit instead of using @Scheduled.
 *
 * Current implementation (boilerplate)
 * - The final "write"-draft based candidate selection is not implemented yet.
 * - For now we reuse GmailClient.findUnrepliedMessagesSince(...) as a placeholder.
 *
 * Next steps
 * - Add something like GmailClient.findWriteDraftCandidates().
 * - Implement a Gmail query that finds drafts/messages you marked as "write".
 */
@Component
@Profile("automation")
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
                    // TODO: Phase 4+ - For now we use snippet as a placeholder for the user's question
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
