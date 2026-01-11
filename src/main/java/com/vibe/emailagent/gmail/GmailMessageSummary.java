package com.vibe.emailagent.gmail;

import java.time.OffsetDateTime;

/**
 * Gmail에서 가져온 "답장 후보" 메일의 최소 정보.
 *
 * 목적
 * - Phase 4에서 스케줄러가 "최근 1시간 동안 도착했고 아직 답장하지 않은 메일"을 처리할 수 있게
 *   Gmail API 결과를 도메인에 독립적인 형태로 캡슐화합니다.
 *
 * 설계 메모
 * - 필요한 정보는 최소화했습니다.
 * - 다음 Phase에서 messageId -> 원문 fetch, threadId 기반 컨텍스트 수집, 제목/보낸이 등 확장 가능.
 */
public record GmailMessageSummary(
        String messageId,
        String threadId,
        String subject,
        String from,
        String snippet,
        OffsetDateTime receivedAt
) {
}

