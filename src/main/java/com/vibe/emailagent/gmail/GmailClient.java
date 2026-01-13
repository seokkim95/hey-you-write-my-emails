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
     * Returns candidate inbound messages since 'since'.
     *
     * Contract
     * - Result can contain multiple messages from the same thread.
     * - Caller may want to de-duplicate by thread and only draft for the latest message.
     */
    List<GmailMessageSummary> findUnrepliedMessagesSince(OffsetDateTime since, int maxMessages);

    /**
     * Fetches message content (plain text).
     */
    GmailMessageContent fetchMessageContent(String messageId);

    /**
     * Fetches all messages for a thread.
     *
     * Notes
     * - Gmail API returns messages in no guaranteed order.
     * - Caller should sort by receivedAt/internalDate.
     */
    List<GmailMessageContent> fetchThreadMessages(String threadId);

    /**
     * Creates a reply draft in the user's Gmail drafts.
     */
    String createReplyDraft(String messageId, String threadId, String subject, String draftBody);

    /**
     * Batch/ingestion API: lists messages using a Gmail search query.
     */
    GmailMessagePage listMessages(String query, long maxResults, String pageToken);
}
