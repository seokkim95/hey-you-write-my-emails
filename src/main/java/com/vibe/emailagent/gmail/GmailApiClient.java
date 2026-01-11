package com.vibe.emailagent.gmail;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Gmail API 기반 GmailClient 구현체 (뼈대).
 *
 * Phase 4에서는 "구글 API 인증 부분은 인터페이스로 추상화"가 요구사항이므로,
 * 실제 동작에 필요한 OAuth 구현은 GmailAuthProvider가 담당하도록 분리했습니다.
 *
 * 이 클래스가 제공하는 것
 * - Google Gmail SDK 호출 위치를 한 군데로 모아둔 skeleton
 * - users.messages.list / users.drafts.create 호출 자리
 *
 * 주의
 * - findUnrepliedMessagesSince()는 현재 skeleton이며, 실제 query/필터링/원문 fetch는 다음 단계에서 필요
 * - createReplyDraft()도 MIME 메시지 생성(raw base64url)까지는 구현하지 않고, 호출 형태만 보여줍니다.
 */
@Component
@ConditionalOnProperty(prefix = "gmail", name = "enabled", havingValue = "true", matchIfMissing = false)
public class GmailApiClient implements GmailClient {

    private static final Logger log = LoggerFactory.getLogger(GmailApiClient.class);

    /**
     * Gmail API에서 사용하는 사용자 식별자.
     * - 'me'는 인증된 사용자 본인을 의미합니다.
     */
    private static final String USER_ID = "me";

    private final GmailAuthProvider authProvider;

    public GmailApiClient(GmailAuthProvider authProvider) {
        this.authProvider = authProvider;
    }

    @Override
    public List<GmailMessageSummary> findUnrepliedMessagesSince(OffsetDateTime since) {
        try {
            Gmail gmail = gmail();

            // Gmail query를 "그냥 최근에 받은 메일"이 나오도록 최대한 단순화합니다.
            // - in:inbox  : 받은 편지함
            // - -from:me  : 내가 보낸 메일 제외
            //
            // NOTE:
            // - Gmail search query는 기본적으로 최신 메일이 먼저 옵니다.
            // - since 파라미터를 아직 query에 반영하지 않습니다(테스트 목적). 필요하면 after:epochSeconds로 확장하세요.
            String query = "in:inbox -from:me";

            ListMessagesResponse response = gmail.users().messages().list(USER_ID)
                    .setQ(query)
                    .setMaxResults(5L)
                    .execute();

            if (log.isDebugEnabled()) {
                log.debug("Gmail list: query='{}', estimateResultSize={}, nextPageToken={}",
                        query, response.getResultSizeEstimate(), response.getNextPageToken());
            }

            if (response.getMessages() == null || response.getMessages().isEmpty()) {
                log.warn("No messages found for query: {}", query);
                return Collections.emptyList();
            }

            // messages.get을 호출해 subject/from/snippet/receivedAt을 채웁니다.
            return response.getMessages().stream()
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

                            return new GmailMessageSummary(
                                    full.getId(),
                                    full.getThreadId(),
                                    subject,
                                    from,
                                    snippet,
                                    receivedAt
                            );
                        } catch (Exception e) {
                            log.warn("Failed to fetch message details for id={}: {}", m.getId(), e.getMessage());
                            return new GmailMessageSummary(m.getId(), m.getThreadId(), "", "", "", OffsetDateTime.now());
                        }
                    })
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to query Gmail messages: {}", e.getMessage(), e);
            return Collections.emptyList();
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
        // TODO(Phase 다음 단계): Gmail Draft 생성은 RFC822 MIME 메시지를 만들어 base64url로 넣어야 합니다.
        // - to/from, subject, In-Reply-To, References, threadId 등 헤더 구성 필요
        // - Gmail users.drafts.create에 Draft.message.raw를 세팅
        try {
            Gmail gmail = gmail();

            // Skeleton: 실제로는 Draft에 Message(raw)를 채워야 합니다.
            Draft draft = new Draft();
            Draft created = gmail.users().drafts().create(USER_ID, draft).execute();

            return created.getId();
        } catch (Exception e) {
            log.warn("Failed to create Gmail draft (skeleton): {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to create Gmail draft", e);
        }
    }

    private Gmail gmail() throws Exception {
        // Google API 클라이언트 구성
        NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        return new Gmail.Builder(transport, GsonFactory.getDefaultInstance(), authProvider.requestInitializer())
                .setApplicationName("emailagent")
                .build();
    }
}
