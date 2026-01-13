package com.vibe.emailagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;

import com.vibe.emailagent.domain.BusinessRule;

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


    }
}
