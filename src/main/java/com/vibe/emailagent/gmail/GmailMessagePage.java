package com.vibe.emailagent.gmail;

import java.util.List;

/**
 * DTO that wraps one page from Gmail messages.list in a domain-friendly shape.
 */
public record GmailMessagePage(
        List<GmailMessageSummary> messages,
        String nextPageToken
) {
}
