package com.vibe.emailagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gmail 관련 설정을 application.yml에서 바인딩하는 설정 클래스.
 *
 * Phase 4 기준
 * - enabled: GmailApiClient(실제 Gmail SDK 호출) 활성화 플래그
 * - oauth.*: 추후 GmailAuthProvider 구현에서 사용할 OAuth 자격증명/토큰 정보
 */
@ConfigurationProperties(prefix = "gmail")
public record GmailProperties(
        boolean enabled,
        OAuth oauth
) {

    /**
     * oauth 하위 설정 묶음.
     *
     * 참고
     * - 현재는 Refresh Token 기반 예시 형태만 두었고,
     *   실제 구현(GmailAuthProvider)에서 어떤 인증 흐름을 쓸지에 따라 필드는 바뀔 수 있습니다.
     */
    public record OAuth(
            String clientId,
            String clientSecret,
            String redirectUri,
            String refreshToken
    ) {
    }
}
