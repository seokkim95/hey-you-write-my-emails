package com.vibe.emailagent.gmail;

import com.google.api.client.http.HttpRequestInitializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 인증 구현체가 아직 없을 때 애플리케이션이 "컴파일/기동"은 되도록 해주는 NOOP 구현체.
 *
 * 주의
 * - 실제 Gmail API 호출은 이 Provider로는 동작하지 않습니다.
 * - Phase 4에서는 "뼈대만" 요청이므로, 구체 구현은 다음 단계에서 추가하세요.
 */
@Component
@ConditionalOnMissingBean(GmailAuthProvider.class)
public class NoopGmailAuthProvider implements GmailAuthProvider {

    @Override
    public HttpRequestInitializer requestInitializer() {
        return request -> {
            throw new UnsupportedOperationException(
                    "GmailAuthProvider is not configured. Provide a real OAuth implementation.");
        };
    }
}

