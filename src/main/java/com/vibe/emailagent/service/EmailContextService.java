package com.vibe.emailagent.service;

import java.util.Collections;
import java.util.List;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibe.emailagent.domain.BusinessRule;
import com.vibe.emailagent.domain.EmailHistory;
import com.vibe.emailagent.repository.BusinessRuleRepository;
import com.vibe.emailagent.repository.EmailHistoryRepository;

/**
 * RAG 답장 생성을 위해 "컨텍스트를 수집"하는 서비스.
 *
 * 이 서비스는 아직 "답장 생성(LLM 호출)"을 하지 않습니다.
 * - 책임을 분리하기 위해, Phase 2에서는 데이터 수집만 담당합니다.
 * - 다음 Phase에서 EmailDraftService 같은 곳이 EmailContext를 받아 프롬프트를 구성/생성하도록 이어질 예정입니다.
 *
 * 왜 필요한가?
 * - (1) 현재 스레드에서의 문맥 유지
 * - (2) 과거 유사 사례를 찾아 답변 품질 향상
 * - (3) 최신 비즈니스 규칙을 항상 포함하여 "과거 히스토리와 충돌" 시 최신 규칙을 우선
 */
@Service
@Profile("!gmail-test")
public class EmailContextService {

    private final EmailHistoryRepository emailHistoryRepository;
    private final BusinessRuleRepository businessRuleRepository;

    /**
     * Spring AI VectorStore.
     * - pgvector store starter가 자동 구성해서 주입해줍니다.
     * - DB 연결/스키마가 준비되지 않으면 런타임에 빈 생성 또는 쿼리에서 실패할 수 있습니다.
     */
    private final VectorStore vectorStore;

    public EmailContextService(EmailHistoryRepository emailHistoryRepository,
                              BusinessRuleRepository businessRuleRepository,
                              VectorStore vectorStore) {
        this.emailHistoryRepository = emailHistoryRepository;
        this.businessRuleRepository = businessRuleRepository;
        this.vectorStore = vectorStore;
    }

    /**
     * 컨텍스트를 수집합니다.
     *
     * @param threadId        Gmail thread id
     * @param currentQuestion 현재 이메일에서 답변해야 할 핵심 질문/요청(검색 쿼리로도 사용)
     */
    @Transactional(readOnly = true)
    public EmailContext collectContext(String threadId, String currentQuestion) {
        // ---------------------------------
        // 1) 스레드 대화 모으기
        // ---------------------------------
        // 실제 답장을 생성할 때 가장 중요한 정보는 "현재 스레드에서 무엇을 주고받았는지" 입니다.
        List<EmailHistory> threadConversation = emailHistoryRepository
                .findAllByThreadIdOrderByIdAsc(threadId);

        // ---------------------------------
        // 2) 유사 히스토리(semantic search)
        // ---------------------------------
        // 현재 질문과 의미적으로 유사한 과거 이메일을 찾아, 답변의 톤/포맷/사례를 참고할 수 있습니다.
        //
        // 구현 메모
        // - Spring AI의 VectorStore는 Document 리스트를 반환합니다.
        // - Document에는 text + metadata(map)가 있으며, 실제 엔티티로 역직렬화하기보단
        //   Phase 2에서는 "컨텍스트로 쓸 텍스트" 중심으로 EmailHistory 형태에 담아둡니다.
        //
        // 주의
        // - similaritySearch(String) 결과의 topK는 구현체 기본값을 따릅니다.
        //   topK/filter가 꼭 필요하면, 다음 Phase에서 SearchRequest 빌더 API를 확정한 뒤 적용하세요.
        // - VectorStore가 null을 반환할 가능성은 낮지만, 일부 구현체/에러 케이스를 방어하려면
        //   빈 리스트로 치환하는 로직을 추가하는 것이 안전합니다.
        var documents = vectorStore.similaritySearch(currentQuestion);
        if (documents == null) {
            documents = Collections.emptyList();
        }

        List<EmailHistory> similarHistory = documents
                .stream()
                .map(doc -> {
                    // metadata 키는 ingestion 파이프라인에서 저장해둔 값에 따라 달라질 수 있습니다.
                    // 여기서는 보수적으로 thread_id/snippet 정도만 꺼내고, 없으면 기본값을 사용합니다.
                    String docThreadId = (String) doc.getMetadata().getOrDefault("thread_id", "");
                    String docSnippet = (String) doc.getMetadata().getOrDefault("snippet", null);

                    // Spring AI Document의 본문은 버전별로 getText()/getContent() 등 차이가 있을 수 있습니다.
                    // 현재 프로젝트(Spring AI milestone)에서는 getText() 사용.
                    String docText = doc.getText();

                    // embedding은 검색 결과에 다시 넣을 필요가 없으므로 null
                    return new EmailHistory(docThreadId, docSnippet, docText, null);
                })
                .toList();

        // ---------------------------------
        // 3) 최신 비즈니스 규칙 (가장 중요)
        // ---------------------------------
        // 과거 대화/유사 히스토리보다 항상 우선하는 최신 지식.
        // 예: 가격 인상, 정책 변경, SLA 변경 등
        List<BusinessRule> businessRules = businessRuleRepository.findAll();

        // ---------------------------------
        // 4) 컨텍스트 객체로 묶어서 반환
        // ---------------------------------
        return new EmailContext(threadId, currentQuestion, threadConversation, similarHistory, businessRules);
    }
}
