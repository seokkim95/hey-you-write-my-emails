package com.vibe.emailagent.service;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Lightweight message/document DTO.
 *
 * We avoid leaking persistence concerns (JPA entities) into the RAG prompt layer.
 * This object can represent:
 * - a row from email_embeddings (thread reconstruction)
 * - a VectorStore Document (similarity search result)
 */
public record EmailMessage(
        String id,
        String threadId,
        String subject,
        String from,
        OffsetDateTime receivedAt,
        String snippet,
        String content,
        Map<String, Object> metadata
) {
}

