package com.vibe.emailagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds Gmail-related settings from application.yml.
 *
 * Current usage
 * - enabled: feature flag to enable GmailApiClient (real Gmail SDK calls)
 * - oauth.*: OAuth credentials/tokens to be used by GmailAuthProvider implementation
 */
@ConfigurationProperties(prefix = "gmail")
public record GmailProperties(
        boolean enabled,
        OAuth oauth
) {

    /**
     * Nested OAuth settings.
     *
     * Notes
     * - This project currently assumes a Refresh Token based flow.
     * - You may adjust these fields depending on the final OAuth strategy.
     */
    public record OAuth(
            String clientId,
            String clientSecret,
            String redirectUri,
            String refreshToken
    ) {
    }
}
