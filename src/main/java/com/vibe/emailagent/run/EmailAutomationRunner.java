package com.vibe.emailagent.run;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.vibe.emailagent.config.EmailAgentRunnerProperties;
import com.vibe.emailagent.gmail.GmailClient;
import com.vibe.emailagent.gmail.GmailMessageContent;
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
 * Current implementation
 * - Finds candidate inbound messages received within the configured lookback window.
 * - Generates and creates Gmail drafts.
 */
@Component
@Profile("automation")
@ConditionalOnProperty(prefix = "emailagent.runner", name = "enabled", havingValue = "true", matchIfMissing = true)
public class  EmailAutomationRunner implements ApplicationRunner {

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

        int maxMessages = Math.max(1, runnerProperties.maxMessages());

        try {
            // Requirement: consider last 24 hours.
            // - Even if the app runs hourly, we want to be robust to delays.
            int lookback = 24;

            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime since = now.minusHours(lookback);

            log.info("[Runner] Start email automation. lookbackHours={}, since={}, maxMessages={}", lookback, since, maxMessages);

            // 1) Fetch candidate inbound messages in the time window.
            //    This might include multiple messages from the same thread.
            List<GmailMessageSummary> candidates = gmailClient.findUnrepliedMessagesSince(since, maxMessages);
            if (candidates.isEmpty()) {
                log.info("[Runner] No candidate messages found. Exiting.");
                return;
            }

            // 2) Group by threadId and pick ONLY the latest message per thread for drafting.
            //    If the customer sent 3 consecutive emails, we only draft once (for the last message).
            Map<String, GmailMessageSummary> latestByThread = new HashMap<>();
            for (GmailMessageSummary msg : candidates) {
                if (msg.threadId() == null || msg.threadId().isBlank()) {
                    continue;
                }
                GmailMessageSummary existing = latestByThread.get(msg.threadId());
                if (existing == null) {
                    latestByThread.put(msg.threadId(), msg);
                    continue;
                }

                OffsetDateTime a = existing.receivedAt();
                OffsetDateTime b = msg.receivedAt();

                // Prefer non-null receivedAt; otherwise keep existing.
                if (a == null && b != null) {
                    latestByThread.put(msg.threadId(), msg);
                } else if (a != null && b != null && b.isAfter(a)) {
                    latestByThread.put(msg.threadId(), msg);
                }
            }

            List<GmailMessageSummary> draftTargets = latestByThread.values().stream()
                    .sorted(Comparator.comparing(GmailMessageSummary::receivedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                    .toList();

            log.info("[Runner] candidates={}, uniqueThreads={}, draftTargets={}", candidates.size(), latestByThread.size(), draftTargets.size());

            // 3) For each thread, build an LLM prompt that includes ALL inbound messages in the thread
            //    that we haven't replied to yet (best effort: we include all messages we can fetch).
            for (GmailMessageSummary target : draftTargets) {
                try {
                    List<GmailMessageContent> threadMessages = gmailClient.fetchThreadMessages(target.threadId());

                    // Sort ascending so the prompt reads naturally.
                    threadMessages = threadMessages.stream()
                            .sorted(Comparator.comparing(GmailMessageContent::receivedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                            .toList();

                    // Best-effort inbound filter: exclude messages from me.
                    // WARNING: 'From' parsing isn't robust yet. We'll refine later.
                    List<GmailMessageContent> inbound = threadMessages.stream()
                            .filter(m -> m.from() == null || !m.from().toLowerCase().contains("me"))
                            .toList();

                    String combinedInbound = inbound.stream()
                            .map(m -> {
                                String ts = m.receivedAt() != null ? m.receivedAt().toString() : "";
                                String body = (m.plainTextBody() == null || m.plainTextBody().isBlank()) ? m.snippet() : m.plainTextBody();
                                return "[Inbound Message] " + ts + "\n" + body;
                            })
                            .collect(Collectors.joining("\n\n---\n\n"));

                    EmailDraft draft = emailAgentService.generateDraft(target.threadId(), combinedInbound, target.subject());

                    String draftId = gmailClient.createReplyDraft(target.messageId(), target.threadId(), draft.subject(), draft.body());

                    log.info("[Runner] Draft created. threadId={}, messageId={}, draftId={}", target.threadId(), target.messageId(), draftId);
                } catch (Exception e) {
                    log.warn("[Runner] Failed to process threadId={}, messageId={}: {}", target.threadId(), target.messageId(), e.getMessage(), e);
                }
            }

            log.info("[Runner] Email automation finished.");
        } finally {
            applicationContext.close();
        }
    }
}
