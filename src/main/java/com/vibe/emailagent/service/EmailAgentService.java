package com.vibe.emailagent.service;

import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 3: AI 프롬프트 엔지니어링 + 답장 초안 생성.
 *
 * 역할
 * - EmailContextService로부터 RAG 컨텍스트(스레드 대화, 유사 히스토리, 최신 비즈니스 규칙)를 가져오고
 * - Spring AI ChatClient를 사용해 답장 초안을 생성합니다.
 *
 * 중요한 설계 원칙
 * 1) 최신 비즈니스 규칙 우선(Priority)
 *    - 과거 히스토리(또는 유사 히스토리)와 충돌하면 무조건 businessRules를 우선합니다.
 *    - 예: 가격 인상 후에는 예전 가격을 절대 언급하면 안 됨
 *
 * 2) 톤 유지
 *    - 스레드 대화(threadConversation)를 참고해 말투/문장 길이/격식을 맞춥니다.
 *
 * 3) 출력 형태
 *    - Phase 3에서는 Gmail API 호출 전 단계이므로 "Gmail Draft에 저장 가능한 본문"을 String/JSON으로 반환합니다.
 *    - 다음 Phase에서 Gmail Drafts API에 맞춰 MIME raw 생성/저장 로직을 붙입니다.
 */
@Service
public class EmailAgentService {

    private final EmailContextService emailContextService;
    private final ChatClient chatClient;

    /**
     * Spring AI에서는 보통 ChatClient.Builder를 주입받아 빌드하는 패턴을 사용합니다.
     *
     * - 모델(OpenAI/Anthropic) 선택/설정은 application.yml 및 starter의 auto-config가 담당
     * - 이 서비스는 "프롬프트 구성"과 "호출"만 집중합니다.
     */
    public EmailAgentService(EmailContextService emailContextService, ChatClient.Builder chatClientBuilder) {
        this.emailContextService = emailContextService;
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 특정 threadId에 대한 답장 초안을 생성합니다.
     *
     * @param threadId Gmail thread id
     * @param currentQuestion 현재 들어온 이메일에서 답변해야 할 핵심 질문/요청
     * @param subject (옵션) 답장 제목. 없으면 LLM이 생성하거나, 다음 Phase에서 thread에서 추출하도록 변경 가능
     */
    @Transactional(readOnly = true)
    public EmailDraft generateDraft(String threadId, String currentQuestion, String subject) {
        EmailContext ctx = emailContextService.collectContext(threadId, currentQuestion);

        PromptParts prompts = buildPrompts(ctx);

        // ------------------------------
        // ChatClient 호출
        // ------------------------------
        // Spring AI는 메시지 기반으로 호출할 수 있습니다.
        // - System: 변하지 않는 정책/헌법(최신 규칙 우선)
        // - User: 스레드 대화/유사 사례/룰 등 가변 컨텍스트
        String body = chatClient
                .prompt()
                .messages(
                        new SystemMessage(prompts.systemPrompt()),
                        new UserMessage(prompts.userPrompt())
                )
                .call()
                .content();

        // subject는 아직 정책이 없으므로 입력값을 그대로 쓰되, null이면 빈 문자열
        return new EmailDraft(nullToEmpty(subject), nullToEmpty(body));
    }

    /**
     * Prompt 구성 결과를 담는 단순 DTO.
     *
     * 테스트 방향
     * - 외부 LLM 호출 없이도 여기서 만들어지는 system/user 프롬프트 문자열을 검증하면
     *   "핵심 정책이 빠지지 않았는지"를 안정적으로 테스트할 수 있습니다.
     */
    record PromptParts(String systemPrompt, String userPrompt) {
    }

    /**
     * (package-private) Prompt 구성 로직.
     *
     * - system prompt: 역할/절대 규칙(정책)
     * - user prompt: 컨텍스트 데이터(스레드/유사/룰)
     *
     * 주의
     * - 프롬프트 길이가 길어지면 token budget을 초과할 수 있습니다.
     *   다음 Phase에서 요약/트리밍/중요도 기반 선택 로직을 추가하는 것을 권장합니다.
     */
    PromptParts buildPrompts(EmailContext ctx) {
        // ------------------------------
        // System Prompt: "역할" + "절대 규칙"을 여기에 둡니다.
        // ------------------------------
        String systemPrompt = """
                Role: You are a professional customer service manager.

                You MUST follow these constraints in priority order:
                1) [Highest Priority] If [Latest Business Rules] conflict with [Past Email History], ALWAYS follow [Latest Business Rules].
                   - Never mention outdated prices/policies when a newer rule exists.
                2) Keep a natural conversation flow and match the tone of the current email thread.

                Output requirements:
                - Return ONLY the draft body text (no markdown fences).
                - The output must be directly usable as a Gmail Draft body.
                """;

        // ------------------------------
        // User Prompt: 이번 요청에서 변하는 데이터(컨텍스트)를 넣습니다.
        // ------------------------------
        String businessRulesBlock = ctx.businessRules().stream()
                .map(rule -> "- " + rule.getRuleKey() + ": " + rule.getRuleContent() + " (updatedAt=" + rule.getUpdatedAt() + ")")
                .collect(Collectors.joining("\n"));

        String threadConversationBlock = ctx.threadConversation().stream()
                .map(h -> "- " + safe(h.getFullContent(), h.getSnippet()))
                .collect(Collectors.joining("\n"));

        String similarHistoryBlock = ctx.similarHistory().stream()
                .map(h -> "- " + safe(h.getFullContent(), h.getSnippet()))
                .collect(Collectors.joining("\n"));

        String userPrompt = """
                [Task]
                Write a reply draft to the user's email.

                [Current Question]
                %s

                [Current Thread Conversation]
                %s

                [Similar Past Email History]
                %s

                [Latest Business Rules]
                %s

                [Instructions]
                - Use Latest Business Rules as the source of truth.
                - Keep the tone consistent with the current thread.
                - Be concise, clear, and professional.
                """.formatted(
                nullToEmpty(ctx.currentQuestion()),
                emptyFallback(threadConversationBlock, "(no thread conversation found)"),
                emptyFallback(similarHistoryBlock, "(no similar history found)"),
                emptyFallback(businessRulesBlock, "(no business rules found)")
        );

        return new PromptParts(systemPrompt, userPrompt);
    }

    /**
     * 본문이 없으면 snippet을 대체로 사용합니다.
     * - VectorStore 검색 결과는 원래 엔티티가 아니라 Document에서 변환한 것이기 때문에
     *   fullContent/snippet이 비어있을 수 있습니다.
     */
    private static String safe(String fullContent, String snippet) {
        if (fullContent != null && !fullContent.isBlank()) {
            return fullContent;
        }
        return nullToEmpty(snippet);
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    private static String emptyFallback(String v, String fallback) {
        if (v == null || v.isBlank()) {
            return fallback;
        }
        return v;
    }
}
