package com.vibe.emailagent.run;

import java.time.OffsetDateTime;
import java.util.List;

import com.vibe.emailagent.gmail.GmailClient;
import com.vibe.emailagent.gmail.GmailMessageContent;
import com.vibe.emailagent.gmail.GmailMessageSummary;
import com.vibe.emailagent.service.EmailAgentService;
import com.vibe.emailagent.service.EmailDraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * @deprecated Use {@link EmailAutomationRunner} (profile: automation).
 * This runner is kept temporarily for experimentation but is not part of the main flow.
 */
@Component
@Profile("deprecated-draft-test")
public class GmailDraftAutomationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GmailDraftAutomationRunner.class);

    private static final int DEFAULT_LOOKBACK_HOURS = 24;
    private static final int DEFAULT_MAX_MESSAGES = 10;

    // Safety: cap how many characters we send to the LLM to reduce token usage and errors.
    private static final int MAX_QUESTION_CHARS = 10000;

    private final GmailClient gmailClient;
    private final EmailAgentService emailAgentService;
    private final ConfigurableApplicationContext applicationContext;

    public GmailDraftAutomationRunner(GmailClient gmailClient,
                                     EmailAgentService emailAgentService,
                                     ConfigurableApplicationContext applicationContext) {
        this.gmailClient = gmailClient;
        this.emailAgentService = emailAgentService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int lookbackHours = argInt(args, "lookbackHours", DEFAULT_LOOKBACK_HOURS);
            int maxMessages = argInt(args, "maxMessages", DEFAULT_MAX_MESSAGES);

            OffsetDateTime since = OffsetDateTime.now().minusHours(Math.max(1, lookbackHours));

            log.info("[DraftTest] Start. lookbackHours={}, since={}, maxMessages={}", lookbackHours, since, maxMessages);

            List<GmailMessageSummary> candidates = gmailClient.findUnrepliedMessagesSince(since, maxMessages);
            if (candidates == null || candidates.isEmpty()) {
                log.info("[DraftTest] No candidate messages found. Exiting.");
                return;
            }

            int processed = 0;
            int drafted = 0;

            for (GmailMessageSummary msg : candidates) {
                processed++;

                try {
                    GmailMessageContent content = gmailClient.fetchMessageContent(msg.messageId());

                    String currentQuestion = content != null ? content.plainTextBody() : null;
                    if (currentQuestion == null || currentQuestion.isBlank()) {
                        continue;
                    }
                    currentQuestion = truncate(currentQuestion, MAX_QUESTION_CHARS);

                    EmailDraft draft = emailAgentService.generateDraft(msg.threadId(), currentQuestion, msg.subject());
                    String draftSubject = draft.subject();
                    String draftBody = draft.body();

                    // Intentionally not creating drafts here (deprecated runner).
                    // Keep the generated output visible for debugging purposes.
                    drafted++;
                    log.info("[DraftTest] Draft generated. messageId={}, threadId={}, subject='{}', bodyPreview='{}'",
                            msg.messageId(), msg.threadId(), draftSubject, preview(draftBody, 300));
                } catch (Exception e) {
                    log.warn("[DraftTest] Failed to process messageId={}, threadId={}: {}",
                            msg.messageId(), msg.threadId(), e.getMessage(), e);
                }
            }

            log.info("[DraftTest] Finished. processed={}, drafted={}", processed, drafted);
        } finally {
            applicationContext.close();
        }
    }

    private static String truncate(String s, int maxChars) {
        if (s == null) return "";
        if (s.length() <= maxChars) return s;
        return s.substring(0, maxChars);
    }

    private static int argInt(ApplicationArguments args, String key, int defaultValue) {
        if (!args.containsOption(key)) {
            return defaultValue;
        }
        try {
            List<String> values = args.getOptionValues(key);
            if (values == null || values.isEmpty()) {
                return defaultValue;
            }
            return Integer.parseInt(values.get(0));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static String preview(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars);
    }
}
