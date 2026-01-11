package com.vibe.emailagent.service;

/**
 * Gmail Drafts API에 저장할 수 있는 "초안" 표현.
 *
 * 현재 Phase 3에서는 Gmail API 실제 호출을 아직 하지 않으므로,
 * - 제목(subject)
 * - 본문(body)
 * 정도만 반환하도록 단순화합니다.
 *
 * 다음 Phase에서 Gmail Draft 생성 요청 포맷에 맞춰
 * - mime message(raw)
 * - threadId 연동
 * 등을 추가할 수 있습니다.
 */
public record EmailDraft(
        String subject,
        String body
) {
}

