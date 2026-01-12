package com.vibe.emailagent.service;

/**
 * Represents a draft that can be stored in Gmail Drafts.
 *
 * Current scope (Phase 3)
 * - We don't call Gmail Drafts API yet.
 * - We only return:
 *   - subject
 *   - body
 *
 * Future extensions
 * - MIME message(raw) generation
 * - threadId / In-Reply-To / References headers
 */
public record EmailDraft(
        String subject,
        String body
) {
}
