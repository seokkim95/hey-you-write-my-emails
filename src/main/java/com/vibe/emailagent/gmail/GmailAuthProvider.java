package com.vibe.emailagent.gmail;

import com.google.api.client.http.HttpRequestInitializer;

/**
 * Gmail API 인증/권한 부여를 추상화한 인터페이스.
 *
 * 왜 인터페이스로 분리하나?
 * - OAuth 토큰 갱신/저장 전략은 환경마다 다릅니다.
 *   (로컬: 파일 기반 토큰, 서버: Secret Manager/DB, 기업 환경: 워크스페이스 서비스 계정 등)
 * - 스케줄러/비즈니스 로직은 "Gmail을 어떻게 인증하는지"에 관심이 없어야 유지보수가 쉽습니다.
 *
 * 구현체 예시(추후)
 * - RefreshToken 기반 (현재 application.yml의 gmail.oauth.*)
 * - GoogleAuthorizationCodeFlow 기반
 * - 서비스 계정 + 도메인 위임(Domain-wide delegation)
 */
public interface GmailAuthProvider {

    /**
     * Gmail API 클라이언트를 만들 때 사용할 HttpRequestInitializer를 제공합니다.
     *
     * 구글 라이브러리에서는 보통 Credential이 HttpRequestInitializer 역할을 수행합니다.
     * - Authorization header 주입
     * - 토큰 만료 시 자동 refresh
     */
    HttpRequestInitializer requestInitializer();
}

