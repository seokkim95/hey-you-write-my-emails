package com.vibe.emailagent.gmail;

/**
 * Represents a Gmail Draft that the user wants the agent to complete.
 *
 * Terminology
 * - "Trigger draft": a draft created by the user, containing a marker keyword (e.g., "write")
 *   and optional extra instructions.
 * - The agent will update/overwrite the body of this draft with an AI-generated reply.
 *
 * Notes
 * - In Gmail API, drafts have their own draftId and embed a Message.
 * - For a reply draft, the embedded Message may have threadId set.
 */
public record GmailDraftCandidate(
        String draftId,
        String messageId,
        String threadId,
        String subject,
        String from,
        String plainTextBody
) {
}

