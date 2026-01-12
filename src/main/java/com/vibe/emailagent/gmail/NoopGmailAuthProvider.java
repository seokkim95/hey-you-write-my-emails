package com.vibe.emailagent.gmail;

import com.google.api.client.http.HttpRequestInitializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * NOOP implementation used to keep the app compilable/startable when no real auth provider is configured.
 *
 * Notes
 * - Real Gmail API calls will NOT work with this provider.
 * - This exists as scaffolding. Replace with a real OAuth implementation.
 */
@Component
@ConditionalOnMissingBean(GmailAuthProvider.class)
public class NoopGmailAuthProvider implements GmailAuthProvider {

    @Override
    public HttpRequestInitializer requestInitializer() {
        return request -> {
            throw new UnsupportedOperationException(
                    "GmailAuthProvider is not configured. Provide a real OAuth implementation.");
        };
    }
}
