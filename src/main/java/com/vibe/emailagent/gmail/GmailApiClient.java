package com.vibe.emailagent.gmail;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.google.api.services.gmail.model.Thread;

/**
 * GmailClient implementation backed by the official Gmail API (Google SDK).
 *
 * Design notes
 * - OAuth is abstracted behind {@link GmailAuthProvider}.
 * - This class is the single place where we call the Google Gmail SDK.
 */
@Component
@ConditionalOnProperty(prefix = "gmail", name = "enabled", havingValue = "true")
public class GmailApiClient implements GmailClient {

    private static final Logger log = LoggerFactory.getLogger(GmailApiClient.class);

    /**
     * Gmail API user id.
     * - "me" means the currently authenticated user.
     */
    private static final String USER_ID = "me";

    private final GmailAuthProvider authProvider;
    private Gmail gmail;

    public GmailApiClient(GmailAuthProvider authProvider) {
        this.authProvider = authProvider;
    }

    @PostConstruct
    private void init() {
        try {
            this.gmail = gmail();
            log.info("GmailApiClient initialized successfully.");
        } catch (Exception e) {
            log.error("Failed to initialize GmailApiClient: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to initialize GmailApiClient", e);
        }
    }

    @Override
    public List<GmailMessageSummary> findUnrepliedMessagesSince(OffsetDateTime since, int maxMessages) {
        // Gmail query heuristics:
        // - in:inbox -> only inbox
        // - after:<epochSeconds> -> time window start (seconds since Unix epoch)
        // - -from:me -> exclude messages sent by me (helps focus on inbound messages)
        //
        // Notes
        // - Gmail search supports both relative filters (newer_than:1h) and absolute (after:).
        // - We prefer `after:` here because the caller already computed an absolute timestamp.
        // - `-in:sent` is NOT necessary when `in:inbox` is present (sent mail is not in inbox).
        if (since == null) {
            // Fallback to a safe default if caller passes null.
            since = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        }

        long epochSeconds = since.toEpochSecond();
        String query = "in:inbox after:" + epochSeconds + " -from:me";

        // NOTE: maxResults is intentionally small here; callers that need paging should use listMessages().
        return listMessages(query, maxMessages, null).messages();
    }

    @Override
    public GmailMessagePage listMessages(String query, long maxResults, String pageToken) {
        try {
            ListMessagesResponse response = gmail.users().messages().list(USER_ID)
                    .setQ(query)
                    .setMaxResults(maxResults)
                    .setPageToken(pageToken)
                    .execute();

            if (response.getMessages() == null || response.getMessages().isEmpty()) {
                return new GmailMessagePage(List.of(), null);
            }

            List<GmailMessageSummary> summaries = response.getMessages().stream()
                    .map(m -> {
                        try {
                            Message full = gmail.users().messages().get(USER_ID, m.getId())
                                    .setFormat("metadata")
                                    .setMetadataHeaders(List.of("Subject", "From"))
                                    .execute();

                            String subject = headerValue(full.getPayload() != null ? full.getPayload().getHeaders() : null, "Subject");
                            String from = headerValue(full.getPayload() != null ? full.getPayload().getHeaders() : null, "From");
                            String snippet = full.getSnippet() != null ? full.getSnippet() : "";

                            OffsetDateTime receivedAt = OffsetDateTime.now();
                            if (full.getInternalDate() != null) {
                                receivedAt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(full.getInternalDate()), ZoneOffset.UTC);
                            }

                            return new GmailMessageSummary(full.getId(), full.getThreadId(), subject, from, snippet, receivedAt);
                        } catch (Exception e) {
                            log.warn("Failed to fetch metadata for messageId={}: {}", m.getId(), e.getMessage());
                            return new GmailMessageSummary(m.getId(), m.getThreadId(), "", "", "", OffsetDateTime.now());
                        }
                    })
                    .toList();

            return new GmailMessagePage(summaries, response.getNextPageToken());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to list Gmail messages", e);
        }
    }

    @Override
    public GmailMessageContent fetchMessageContent(String messageId) {
        try {
            Message full = gmail.users().messages().get(USER_ID, messageId)
                    .setFormat("full")
                    .execute();

            String subject = headerValue(full.getPayload() != null ? full.getPayload().getHeaders() : null, "Subject");
            String from = headerValue(full.getPayload() != null ? full.getPayload().getHeaders() : null, "From");

            OffsetDateTime receivedAt = null;
            if (full.getInternalDate() != null) {
                receivedAt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(full.getInternalDate()), ZoneOffset.UTC);
            }

            String snippet = full.getSnippet() != null ? full.getSnippet() : "";

            String body = extractBestEffortPlainText(full.getPayload());
            body = GmailTextCleaner.clean(body);

            return new GmailMessageContent(full.getId(), full.getThreadId(), subject, from, receivedAt, snippet, body);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch Gmail message content for id=" + messageId, e);
        }
    }

    /**
     * Best-effort extraction of a human-readable body.
     */
    private static String extractBestEffortPlainText(MessagePart part) {
        if (part == null) return "";

        String plain = findFirstPart(part, "text/plain");
        if (plain != null && !plain.isBlank()) {
            return plain;
        }

        String html = findFirstPart(part, "text/html");
        if (html != null && !html.isBlank()) {
            return GmailTextCleaner.clean(html);
        }

        return decodeBody(part.getBody());
    }

    private static String findFirstPart(MessagePart part, String mimeType) {
        if (part == null) return null;

        if (mimeType.equalsIgnoreCase(part.getMimeType())) {
            return decodeBody(part.getBody());
        }

        if (part.getParts() == null || part.getParts().isEmpty()) {
            return null;
        }

        for (MessagePart p : part.getParts()) {
            String found = findFirstPart(p, mimeType);
            if (found != null && !found.isBlank()) {
                return found;
            }
        }

        return null;
    }

    private static String decodeBody(MessagePartBody body) {
        if (body == null || body.getData() == null) return "";
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(body.getData());
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static String headerValue(List<MessagePartHeader> headers, String name) {
        if (headers == null) return "";
        return headers.stream()
                .filter(h -> name.equalsIgnoreCase(h.getName()))
                .map(MessagePartHeader::getValue)
                .findFirst()
                .orElse("");
    }

    @Override
    public String createReplyDraft(String messageId, String threadId, String subject, String draftBody) {
        try {
            // NOTE: Still a skeleton.
            // Proper reply drafting requires building a raw RFC822 message with In-Reply-To/References.
            // For now, we create a plain text draft.
            Message message = new Message();
            message.setThreadId(threadId);

            String raw = "Subject: " + (subject == null ? "" : subject) + "\r\n" +
                    "Content-Type: text/plain; charset=\"UTF-8\"\r\n" +
                    "\r\n" +
                    (draftBody == null ? "" : draftBody);

            String encoded = Base64.getUrlEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
            message.setRaw(encoded);

            Draft draft = new Draft();
            draft.setMessage(message);

            Draft created = gmail.users().drafts().create(USER_ID, draft).execute();
            return created.getId();
        } catch (Exception e) {
            log.warn("Failed to create Gmail draft: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to create Gmail draft", e);
        }
    }

    @Override
    public List<GmailMessageContent> fetchThreadMessages(String threadId) {
        try {
            Thread thread = gmail.users().threads().get(USER_ID, threadId)
                    // 'full' gives payload parts and internalDate
                    .setFormat("full")
                    .execute();

            if (thread.getMessages() == null || thread.getMessages().isEmpty()) {
                return List.of();
            }

            return thread.getMessages().stream()
                    .map(m -> {
                        try {
                            String subject = headerValue(m.getPayload() != null ? m.getPayload().getHeaders() : null, "Subject");
                            String from = headerValue(m.getPayload() != null ? m.getPayload().getHeaders() : null, "From");

                            OffsetDateTime receivedAt = null;
                            if (m.getInternalDate() != null) {
                                receivedAt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(m.getInternalDate()), ZoneOffset.UTC);
                            }

                            String snippet = m.getSnippet() != null ? m.getSnippet() : "";
                            String body = extractBestEffortPlainText(m.getPayload());
                            body = GmailTextCleaner.clean(body);

                            return new GmailMessageContent(m.getId(), m.getThreadId(), subject, from, receivedAt, snippet, body);
                        } catch (Exception e) {
                            // Best effort fallback: keep at least IDs/snippet
                            OffsetDateTime receivedAt = null;
                            if (m.getInternalDate() != null) {
                                receivedAt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(m.getInternalDate()), ZoneOffset.UTC);
                            }
                            return new GmailMessageContent(m.getId(), m.getThreadId(), "", "", receivedAt, m.getSnippet(), "");
                        }
                    })
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch thread messages for threadId=" + threadId, e);
        }
    }

    // NOTE: write-draft trigger based automation was removed.
    // Any draft-search/update methods were intentionally deleted.

    private Gmail gmail() throws Exception {
        NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        return new Gmail.Builder(transport, GsonFactory.getDefaultInstance(), authProvider.requestInitializer())
                .setApplicationName("emailagent")
                .build();
    }
}
