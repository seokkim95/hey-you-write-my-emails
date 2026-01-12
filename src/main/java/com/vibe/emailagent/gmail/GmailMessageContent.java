package com.vibe.emailagent.gmail;

import java.time.OffsetDateTime;

/**
 * Minimal message details used for ingestion.
 *
 * Notes
 * - Full raw/MIME handling is deferred to later iterations.
 * - For now, we focus on extracting useful text for drafting/embedding.
 */
public record GmailMessageContent(
        String messageId,
        String threadId,
        String subject,
        String from,
        OffsetDateTime receivedAt,
        String snippet,
        String plainTextBody
) {
}
