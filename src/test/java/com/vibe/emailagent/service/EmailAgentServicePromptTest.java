package com.vibe.emailagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;

import com.vibe.emailagent.domain.BusinessRule;
import com.vibe.emailagent.domain.EmailHistory;

/**
 * Phase 3 최소 단위 테스트 (프롬프트 구성 검증용).
 *
 * 왜 "프롬프트 문자열"만 테스트하나?
 * - ChatClient의 fluent DSL/반환 타입은 milestone 버전에서 변경될 수 있어 mocking이 매우 취약합니다.
 * - 반면, 프롬프트는 우리가 통제하는 핵심 로직이므로
 *   "정책/규칙이 프롬프트에 포함됐는지"를 빠르게 검증하는 것이 유지보수에 더 유용합니다.
 */
class EmailAgentServicePromptTest {

    @Test
    void buildPrompts_containsPriorityRulesAndContextBlocks() {
        // given
        EmailContextService ctxService = Mockito.mock(EmailContextService.class);
        ChatClient.Builder builder = Mockito.mock(ChatClient.Builder.class);
        Mockito.when(builder.build()).thenReturn(Mockito.mock(ChatClient.class));

        EmailAgentService service = new EmailAgentService(ctxService, builder);

        EmailContext ctx = new EmailContext(
                "t-1",
                "가격이 얼마인가요?",
                List.of(new EmailHistory("t-1", "snip", "이전 스레드 톤", null)),
                List.of(new EmailHistory("", null, "유사 히스토리 예시", null)),
                List.of(new BusinessRule("pricing.current", "2026년부터 10% 인상"))
        );

        // when
        EmailAgentService.PromptParts prompts = service.buildPrompts(ctx);

        // then: System Prompt 핵심 정책
        assertThat(prompts.systemPrompt()).contains("professional customer service manager");
        assertThat(prompts.systemPrompt()).contains("ALWAYS follow [Latest Business Rules]");
        assertThat(prompts.systemPrompt()).contains("match the tone");
        assertThat(prompts.systemPrompt()).contains("Return ONLY the draft body text");

        // then: User Prompt에 컨텍스트 블록들이 포함되어야 함
        assertThat(prompts.userPrompt()).contains("[Current Question]");
        assertThat(prompts.userPrompt()).contains("가격이 얼마인가요?");
        assertThat(prompts.userPrompt()).contains("[Current Thread Conversation]");
        assertThat(prompts.userPrompt()).contains("이전 스레드 톤");
        assertThat(prompts.userPrompt()).contains("[Similar Past Email History]");
        assertThat(prompts.userPrompt()).contains("유사 히스토리 예시");
        assertThat(prompts.userPrompt()).contains("[Latest Business Rules]");
        assertThat(prompts.userPrompt()).contains("pricing.current");
        assertThat(prompts.userPrompt()).contains("2026년부터 10% 인상");
    }
}
