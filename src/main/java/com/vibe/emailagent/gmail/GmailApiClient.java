package com.vibe.emailagent.gmail;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.ListMessagesResponse;
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
        // TODO(Phase 다음 단계): Gmail search query를 적절히 구성해야 합니다.
        // 예시: newer_than:1h -from:me -in:chats
        // 또한 "답장하지 않음" 판단은 label/reply 여부를 어떻게 정의할지 정책 필요.
        try {
            Gmail gmail = gmail();

            // Skeleton: 메시지 ID 목록만 가져오는 형태
            String query = "newer_than:1h -from:me";
            ListMessagesResponse response = gmail.users().messages().list(USER_ID)
                    .setQ(query)
                    .execute();

            if (response.getMessages() == null || response.getMessages().isEmpty()) {
                return Collections.emptyList();
            }

            // Phase 4에서는 message 상세조회(get)까지는 생략하고, 최소 데이터만 placeholder로 반환
            return response.getMessages().stream()
                    .map(m -> new GmailMessageSummary(
                            m.getId(),
                            m.getThreadId(),
                            "", // subject: 다음 단계에서 messages.get + headers 파싱 필요
                            "", // from:    다음 단계에서 필요
                            "", // snippet: 다음 단계에서 messages.get.snippet 활용
                            OffsetDateTime.now() // receivedAt: 다음 단계에서 internalDate 파싱
                    ))
                    .toList();
        } catch (Exception e) {
            // 운영에서는 재시도/알림/서킷브레이커 등을 고려
            log.warn("Failed to query Gmail messages (skeleton): {}", e.getMessage(), e);
            return Collections.emptyList();
        }
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

