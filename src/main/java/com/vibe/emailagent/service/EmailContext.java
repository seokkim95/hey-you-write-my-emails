package com.vibe.emailagent.service;

import java.util.List;

import com.vibe.emailagent.domain.BusinessRule;
import com.vibe.emailagent.domain.EmailHistory;

/**
 * RAG 답변 생성을 위해 수집한 컨텍스트 묶음.
 *
 * 이 객체는 "모델에게 전달할 재료"를 한 곳에 모아둔 DTO(불변)입니다.
 *
 * 구성 요소
 * 1) threadConversation
 *    - 현재 답장해야 하는 스레드의 실제 대화 흐름(가장 직접적인 컨텍스트)
 * 2) similarHistory
 *    - 과거 전체 히스토리 중, 현재 질문과 의미적으로 유사한 사례(레퍼런스/톤/서식)
 * 3) businessRules
 *    - 최신 정책/가격 등. 과거 데이터와 충돌할 때 항상 우선해야 하는 '진실의 원천(source of truth)'
 *
 * 유지보수 팁
 * - 이후 Phase에서 token budget 관리(요약/trim) 로직이 들어가면
 *   이 DTO 자체는 유지하고, 생성하는 서비스에서 '압축된 형태'로 바꾸는 것이 깔끔합니다.
 */
public record EmailContext(
        String threadId,
        String currentQuestion,
        List<EmailHistory> threadConversation,
        List<EmailHistory> similarHistory,
        List<BusinessRule> businessRules
) {
}
