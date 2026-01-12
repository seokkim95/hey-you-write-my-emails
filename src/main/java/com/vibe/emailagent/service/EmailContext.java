package com.vibe.emailagent.service;

import java.util.List;

import com.vibe.emailagent.domain.BusinessRule;

/**
 * Aggregated context payload used for drafting.
 *
 * This record is intentionally a DTO used to pass "retrieved materials" into prompt composition.
 *
 * Components
 * 1) threadConversation
 *    - Conversation within the current Gmail thread (reconstructed from email_embeddings)
 * 2) similarHistory
 *    - Semantically similar past items retrieved from the VectorStore
 * 3) businessRules
 *    - Latest business rules (source of truth; highest priority)
 */
public record EmailContext(
        String threadId,
        String currentQuestion,
        List<EmailMessage> threadConversation,
        List<EmailMessage> similarHistory,
        List<BusinessRule> businessRules
) {
}
