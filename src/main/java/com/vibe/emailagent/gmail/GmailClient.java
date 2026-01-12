package com.vibe.emailagent.gmail;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Gmail client abstraction.
 *
 * Goal
 * - Hide Google SDK complexity from service/business logic.
 * - Make components easy to mock in tests.
 */
public interface GmailClient {

    /**
     * Returns candidate messages that arrived since the given timestamp.
     *
     * NOTE: This method is currently a placeholder/skeleton.
     */
    List<GmailMessageSummary> findUnrepliedMessagesSince(OffsetDateTime since);

    /**
     * Creates a reply draft in the user's Gmail drafts.
     *
     * @param messageId original message id
     * @param threadId original thread id
     * @param subject draft subject
     * @param draftBody draft body (plain text)
     * @return created draft id
     */
    String createReplyDraft(String messageId, String threadId, String subject, String draftBody);

    /**
     * Batch/ingestion API: lists messages using a Gmail search query.
     *
     * @param query Gmail search query (e.g., "in:inbox -from:me")
     * @param maxResults maximum number of results for this page
     * @param pageToken next page token (optional)
     */
    GmailMessagePage listMessages(String query, long maxResults, String pageToken);

    /**
     * Fetches message content (plain text) for ingestion.
     */
    GmailMessageContent fetchMessageContent(String messageId);
}
