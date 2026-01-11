package com.vibe.emailagent.gmail;

import java.util.List;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.vibe.emailagent.config.GmailProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * GmailProperties 기반 Gmail OAuth 인증 Provider.
 *
 * 사용자가 요청한 방향
 * - resources/google/credentials.json(클라이언트 JSON 파일) 의존을 없애고
 * - application.yml / VM args로 주입된 GmailProperties(clientId/clientSecret/refreshToken)를 사용합니다.
 *
 * 동작 방식
 * - refresh_token을 이용해 access_token을 자동으로 갱신하며 Gmail API를 호출합니다.
 * - 애플리케이션 시작 시점(Bean 생성 시점)에 refreshToken()을 한번 호출해
 *   "지금 설정이 유효한지"를 빠르게 검증합니다.
 *
 * 주의
 * - refresh_token이 만료/폐기되었거나 redirect-uri/승인 조건이 바뀐 경우
 *   invalid_grant 오류가 날 수 있습니다.
 */
@Component
@ConditionalOnProperty(prefix = "gmail", name = "enabled", havingValue = "true")
public class GoogleCredentialsGmailAuthProvider implements GmailAuthProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleCredentialsGmailAuthProvider.class);

    /**
     * 최소 권한 원칙 기준 권장 scope.
     *
     * - gmail.compose: draft 생성
     * - gmail.readonly: 메일 조회
     *
     * 필요에 따라 gmail.modify 등으로 확장 가능
     */
    private static final List<String> SCOPES = List.of(
            "https://www.googleapis.com/auth/gmail.compose",
            "https://www.googleapis.com/auth/gmail.readonly"
    );

    private final GmailProperties gmailProperties;

    public GoogleCredentialsGmailAuthProvider(GmailProperties gmailProperties) {
        this.gmailProperties = gmailProperties;
    }

    @PostConstruct
    private void validateOnStartup() {
        // Bean 생성 시점에 한번 호출해서 설정 유효성 검증
        log.info("credential info. {}, {}, {} ,{}",
                gmailProperties.oauth().clientId(),
                gmailProperties.oauth().clientSecret(),
                gmailProperties.oauth().redirectUri(),
                gmailProperties.oauth().refreshToken()
        );
    }

    @Override
    public HttpRequestInitializer requestInitializer() {
        // Gmail.Builder가 요구하는 것은 HttpRequestInitializer 입니다.
        // google-api-client에서는 Credential이 HttpRequestInitializer 역할을 합니다.
        return buildCredentialFromRefreshToken();
    }

    /**
     * GmailProperties에 있는 clientId/clientSecret/refreshToken으로 Credential을 구성합니다.
     */
    private Credential buildCredentialFromRefreshToken() {
        log.info("Building Gmail OAuth credential from GmailProperties...");

        GmailProperties.OAuth oauth = gmailProperties.oauth();
        if (oauth == null) {
            throw new IllegalStateException("gmail.oauth is missing (null). Provide gmail.oauth.* properties.");
        }

        String clientId = oauth.clientId();
        String clientSecret = oauth.clientSecret();
        String refreshToken = oauth.refreshToken();

        if (isBlank(clientId)) {
            throw new IllegalStateException("gmail.oauth.client-id is missing");
        }
        if (isBlank(clientSecret)) {
            throw new IllegalStateException("gmail.oauth.client-secret is missing");
        }
        if (isBlank(refreshToken)) {
            throw new IllegalStateException("gmail.oauth.refresh-token is missing. You need to generate and set a refresh token.");
        }

        try {
            GoogleCredential credential = new GoogleCredential.Builder()
                    .setTransport(new NetHttpTransport())
                    .setJsonFactory(GsonFactory.getDefaultInstance())
                    .setClientSecrets(clientId, clientSecret)
                    .build()
                    .createScoped(SCOPES);

            credential.setRefreshToken(refreshToken);

            // "지금 값이 유효한지"를 즉시 확인하기 위해 refresh를 한번 시도합니다.
            // - 성공하면 내부적으로 access token이 채워집니다.
            // - 실패하면 invalid_grant/401 등의 원인을 바로 알 수 있습니다.
            boolean refreshed = credential.refreshToken();
            if (!refreshed) {
                // refreshToken()은 false를 리턴할 수도 있으므로 명확한 메시지 제공
                throw new IllegalStateException("Refresh token exchange returned false. Check refresh token validity/scopes.");
            }

            log.info("Gmail OAuth credential initialized and token refreshed successfully.");
            return credential;
        } catch (Exception e) {
            // 여기서 가장 흔한 오류: invalid_grant
            throw new IllegalStateException(
                    "Failed to initialize Gmail OAuth credential. " +
                            "Common causes: invalid/expired refresh token, revoked consent, wrong client_id/client_secret.",
                    e);
        }
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}
