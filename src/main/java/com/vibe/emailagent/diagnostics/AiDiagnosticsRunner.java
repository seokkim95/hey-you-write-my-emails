package com.vibe.emailagent.diagnostics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Diagnostics runner for Spring AI configuration.
 *
 * Why this exists
 * - Your OpenAI error indicates the HTTP request payload contains an unexpected JSON field `input`.
 * - For Chat Completions, OpenAI expects `messages`, not `input`.
 * - This runner prints what Spring AI thinks the active model/provider is so we can debug
 *   misconfiguration quickly.
 *
 * How to use
 * - Run with profile: ai-diagnostics
 * - Provide OPENAI_API_KEY
 */
@Component
@Profile("ai-diagnostics")
public class AiDiagnosticsRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AiDiagnosticsRunner.class);

    private final ChatClient.Builder chatClientBuilder;
    private final Environment env;
    private final ConfigurableApplicationContext context;

    public AiDiagnosticsRunner(ChatClient.Builder chatClientBuilder,
                               Environment env,
                               ConfigurableApplicationContext context) {
        this.chatClientBuilder = chatClientBuilder;
        this.env = env;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            // These properties are the usual places Spring AI reads from.
            String apiKey = env.getProperty("spring.ai.openai.api-key");
            String chatModel = env.getProperty("spring.ai.openai.chat.options.model");

            log.info("[AI-DIAG] spring.ai.openai.api-key is {}",
                    (apiKey == null || apiKey.isBlank()) ? "MISSING" : "SET");
            log.info("[AI-DIAG] spring.ai.openai.chat.options.model={}", chatModel);

            // Force bean creation/build.
            ChatClient client = chatClientBuilder.build();
            log.info("[AI-DIAG] ChatClient implementation: {}", client.getClass().getName());

            log.info("[AI-DIAG] If you still see '$.input is invalid', enable HTTP debug logs (see application-local.yml) to inspect request JSON.");
        } finally {
            context.close();
        }
    }
}

