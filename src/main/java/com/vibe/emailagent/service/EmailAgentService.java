package com.vibe.emailagent.service;

import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 3: Prompt engineering + draft generation via Spring AI ChatClient.
 *
 * Responsibilities
 * - Collect RAG context from {@link EmailContextService}
 *   (thread conversation, similar history, latest business rules)
 * - Call the LLM via Spring AI ChatClient to produce a draft body
 *
 * Key constraints
 * 1) Latest business rules always win.
 *    - If business rules conflict with past history, ALWAYS follow business rules.
 * 2) Preserve the tone of the current thread.
 * 3) Output should be directly usable as a Gmail draft body.
 */
@Service
@Profile("automation")
public class EmailAgentService {

    private final EmailContextService emailContextService;
    private final ChatClient chatClient;

    /**
     * Spring AI commonly injects {@link ChatClient.Builder} and you build a client instance.
     * - Provider(OpenAI/Anthropic) and model options are usually configured via application.yml and auto-config.
     * - This service focuses on prompt composition + invocation.
     */
    public EmailAgentService(EmailContextService emailContextService, ChatClient.Builder chatClientBuilder) {
        this.emailContextService = emailContextService;
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Generates a reply draft for a given Gmail thread.
     *
     * @param threadId Gmail thread id
     * @param currentQuestion the key question/request to answer
     * @param subject optional subject to use
     */
    @Transactional(readOnly = true)
    public EmailDraft generateDraft(String threadId, String currentQuestion, String subject) {
        EmailContext ctx = emailContextService.collectContext(threadId, currentQuestion);

        PromptParts prompts = buildPrompts(ctx);

        // ------------------------------
        // ChatClient call
        // ------------------------------
        String body = chatClient
                .prompt()
                .messages(
                        new SystemMessage(prompts.systemPrompt()),
                        new UserMessage(prompts.userPrompt())
                )
                .call()
                .content();

        // Keep input subject as-is for now; if null, return empty string.
        return new EmailDraft(nullToEmpty(subject), nullToEmpty(body));
    }

    /**
     * Small DTO that holds the composed prompts.
     *
     * Testing tips
     * - You can unit test the generated system/user prompt strings without calling an external LLM.
     *   This helps ensure the non-negotiable policy (latest business rules first) never regresses.
     */
    record PromptParts(String systemPrompt, String userPrompt) {
    }

    /**
     * (package-private) Prompt composition logic.
     *
     * - system prompt: role + non-negotiable policy constraints
     * - user prompt: context data (thread + similar history + business rules)
     *
     * Notes
     * - Prompts can become long and exceed token budgets.
     *   In later phases, consider summarization / trimming / selecting top-K items.
     */
    PromptParts buildPrompts(EmailContext ctx) {
        // ------------------------------
        // System Prompt: role + absolute policy constraints
        // ------------------------------
        String systemPrompt = """
                Role: You are a professional customer service manager.

                You MUST follow these constraints in priority order:
                1) [Highest Priority] If [Latest Business Rules] conflict with [Past Email History], ALWAYS follow [Latest Business Rules].
                   - Never mention outdated prices/policies when a newer rule exists.
                2) Keep a natural conversation flow and match the tone of the current email thread.

                Output requirements:
                - Return ONLY the draft body text (no markdown fences).
                - The output must be directly usable as a Gmail Draft body.
                """;

        // ------------------------------
        // User Prompt: request-specific context payload
        // ------------------------------
        String businessRulesBlock = ctx.businessRules().stream()
                .map(rule -> "- " + rule.getRuleKey() + ": " + rule.getRuleContent() + " (updatedAt=" + rule.getUpdatedAt() + ")")
                .collect(Collectors.joining("\n"));

        String threadConversationBlock = ctx.threadConversation().stream()
                .map(m -> "- " + safe(m.content(), m.snippet()))
                .collect(Collectors.joining("\n"));

        String similarHistoryBlock = ctx.similarHistory().stream()
                .map(m -> "- " + safe(m.content(), m.snippet()))
                .collect(Collectors.joining("\n"));

        String userPrompt = """
                [Task]
                Write a reply draft to the user's email.

                [Current Question]
                %s

                [Current Thread Conversation]
                %s

                [Similar Past Email History]
                %s

                [Latest Business Rules]
                %s

                [Instructions]
                - Use Latest Business Rules as the source of truth.
                - Keep the tone consistent with the current thread.
                - Be concise, clear, and professional.
                """.formatted(
                nullToEmpty(ctx.currentQuestion()),
                emptyFallback(threadConversationBlock, "(no thread conversation found)"),
                emptyFallback(similarHistoryBlock, "(no similar history found)"),
                emptyFallback(businessRulesBlock, "(no business rules found)")
        );

        return new PromptParts(systemPrompt, userPrompt);
    }

    /**
     * If there is no full content, fall back to snippet.
     * - VectorStore search results originate from Documents rather than JPA entities,
     *   so fullContent/snippet may be missing.
     */
    private static String safe(String content, String snippet) {
        if (content != null && !content.isBlank()) {
            return content;
        }
        return nullToEmpty(snippet);
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    private static String emptyFallback(String v, String fallback) {
        if (v == null || v.isBlank()) {
            return fallback;
        }
        return v;
    }
}
