package com.vibe.emailagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gmail.oauth")
public record GmailProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String refreshToken
) {
}

