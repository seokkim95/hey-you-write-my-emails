package com.vibe.emailagent.service;

import com.vibe.emailagent.retry.ExponentialRetry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

/**
 * Dedicated component that performs the actual LLM HTTP call.
 *
 * Why this exists
 * - Spring AOP retry is proxy-based.
 * - If we annotate a method and call it from within the same class (self-invocation),
 *   the proxy is bypassed and retry won't run.
 * - By moving the call into a separate Spring bean, calls go through the proxy.
 */
@Component
public class LlmCaller {

    private final ChatClient chatClient;

    public LlmCaller(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Executes the chat completion call.
     *
     * Defaults
     * - initial backoff: 3 seconds
     * - max attempts: 3
     */
    @ExponentialRetry
    public String callChatModel(String systemPrompt, String userPrompt) {
        return chatClient
                .prompt()
                .messages(
                        new SystemMessage(systemPrompt),
                        new UserMessage(userPrompt)
                )
                .call()
                .content();
    }
}

