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

/**
 * GmailClient implementation backed by the official Gmail API (Google SDK).
 *
 * Design notes
 * - OAuth is abstracted behind {@link GmailAuthProvider}.
 * - This class is the single place where we call the Google Gmail SDK.
 *
 * Current status (scaffolding)
 * - Draft creation is still a skeleton (MIME raw generation is not implemented yet).
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
    public List<GmailMessageSummary> findUnrepliedMessagesSince(OffsetDateTime since) {
        // Compatibility method used by earlier phases/tests; implemented via listMessages.
        return listMessages("in:inbox -from:me", 5, null).messages();
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
            String body = extractPlainText(full.getPayload());

            return new GmailMessageContent(full.getId(), full.getThreadId(), subject, from, receivedAt, snippet, body);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch Gmail message content for id=" + messageId, e);
        }
    }

    private static String extractPlainText(MessagePart part) {
        if (part == null) return "";

        if ("text/plain".equalsIgnoreCase(part.getMimeType())) {
            return decodeBody(part.getBody());
        }

        if (part.getParts() != null) {
            for (MessagePart p : part.getParts()) {
                String text = extractPlainText(p);
                if (!text.isBlank()) {
                    return text;
                }
            }
        }

        return decodeBody(part.getBody());
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
            Gmail gmail = gmail();
            Draft draft = new Draft();
            Draft created = gmail.users().drafts().create(USER_ID, draft).execute();
            return created.getId();
        } catch (Exception e) {
            log.warn("Failed to create Gmail draft (skeleton): {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to create Gmail draft", e);
        }
    }

    private Gmail gmail() throws Exception {
        NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        return new Gmail.Builder(transport, GsonFactory.getDefaultInstance(), authProvider.requestInitializer())
                .setApplicationName("emailagent")
                .build();
    }
}
