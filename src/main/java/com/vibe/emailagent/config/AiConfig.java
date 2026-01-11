package com.vibe.emailagent.config;

import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 설정용 Configuration.
 *
 * 현재는 intentionally empty 입니다.
 * - spring-ai-*-spring-boot-starter들이 제공하는 auto-configuration으로
 *   ChatModel, EmbeddingModel, VectorStore(pgvector) 등의 Bean이 자동 등록되는 것을 전제로 합니다.
 *
 * 언제 이 클래스에 코드를 추가하나?
 * - 프롬프트 템플릿/모델 옵션을 코드로 강제하고 싶을 때
 * - 다중 모델(OpenAI + Anthropic) 라우팅 전략을 Bean으로 두고 싶을 때
 * - VectorStore 검색 파라미터(topK, filter) 전략을 공통화하고 싶을 때
 */
@Configuration
public class AiConfig {
    // Intentionally empty
}
