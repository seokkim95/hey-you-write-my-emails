package com.vibe.emailagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ingestion mode configuration.
 *
 * Bound from: emailagent.ingestion.*
 */
@ConfigurationProperties(prefix = "emailagent.ingestion")
public record IngestionProperties(
        int lookbackHours,
        int maxMessages,
        int pageSize,
        int chunkSize,
        int chunkOverlap,
        boolean includeSent
) {
}
