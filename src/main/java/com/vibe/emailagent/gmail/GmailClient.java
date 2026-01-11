package com.vibe.emailagent.gmail;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Gmail API 연동을 캡슐화한 클라이언트 인터페이스.
 *
 * Phase 4 요구사항
 * 1) 최근 1시간동안 도착한 메시지 중 응답하지 않은 메시지 조회
 * 2) Draft 생성(users.drafts.create)
 *
 * 설계 목표
 * - Google SDK의 복잡성/모델 객체를 서비스 로직에서 격리합니다.
 * - 테스트에서 이 인터페이스만 mocking하면 스케줄러를 쉽게 검증할 수 있습니다.
 */
public interface GmailClient {

    /**
     * "최근 since 이후에 도착"했고 "아직 응답하지 않은" 메시지 목록을 가져옵니다.
     *
     * 구현 메모(추후)
     * - Gmail search query 예시:
     *   - newer_than:1h
     *   - -in:chats
     *   - -from:me  (내가 보낸 메일 제외)
     *   - is:unread 또는 -label:replied 등 조직 정책에 맞게 조합
     */
    List<GmailMessageSummary> findUnrepliedMessagesSince(OffsetDateTime since);

    /**
     * 사용자의 Drafts에 임시 저장합니다.
     *
     * @param messageId 원본 메시지 id (옵션: 구현에서 thread 매핑/참조를 위해 사용 가능)
     * @param threadId  원본 thread id (답장 draft를 같은 thread로 묶을 때 유용)
     * @param draftBody Gmail에 저장할 답장 본문(plain text)
     * @param subject   답장 제목
     * @return 생성된 draft id (구글 API의 Draft.id)
     */
    String createReplyDraft(String messageId, String threadId, String subject, String draftBody);
}

