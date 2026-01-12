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
 * Gmail OAuth provider based on {@link GmailProperties}.
 *
 * Design goal
 * - Avoid direct dependency on resources/google/credentials.json.
 * - Use values injected via application.yml / environment variables / VM args.
 *
 * How it works
 * - Uses refresh_token to obtain/refresh access tokens automatically.
 * - On app startup (bean creation), prints current config to fail fast if misconfigured.
 *
 * Common pitfalls
 * - invalid_grant can happen when:
 *   - refresh token is revoked/expired
 *   - OAuth consent has changed
 *   - redirect URI / client settings mismatch
 */
@Component
@ConditionalOnProperty(prefix = "gmail", name = "enabled", havingValue = "true")
public class GoogleCredentialsGmailAuthProvider implements GmailAuthProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleCredentialsGmailAuthProvider.class);

    /**
     * Recommended scopes following the principle of least privilege.
     *
     * - gmail.compose: create drafts
     * - gmail.readonly: read mail
     *
     * If needed, you can expand to gmail.modify etc.
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
        // Fail fast on startup: print current config.
        log.info("credential info. {}, {}, {} ,{}",
                gmailProperties.oauth().clientId(),
                gmailProperties.oauth().clientSecret(),
                gmailProperties.oauth().redirectUri(),
                gmailProperties.oauth().refreshToken()
        );
    }

    @Override
    public HttpRequestInitializer requestInitializer() {
        // Gmail.Builder expects a HttpRequestInitializer.
        // In google-api-client, Credential implements HttpRequestInitializer.
        return buildCredentialFromRefreshToken();
    }

    /**
     * Builds Credential using clientId/clientSecret/refreshToken from GmailProperties.
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

            // Try refreshing once to validate the current settings.
            boolean refreshed = credential.refreshToken();
            if (!refreshed) {
                throw new IllegalStateException("Refresh token exchange returned false. Check refresh token validity/scopes.");
            }

            log.info("Gmail OAuth credential initialized and token refreshed successfully.");
            return credential;
        } catch (Exception e) {
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
