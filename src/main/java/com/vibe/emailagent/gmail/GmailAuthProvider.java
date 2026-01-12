package com.vibe.emailagent.gmail;

import com.google.api.client.http.HttpRequestInitializer;

/**
 * Abstraction for Gmail API authentication/authorization.
 *
 * Why keep this as an interface?
 * - OAuth token refresh/storage strategies vary by environment.
 *   (local: file-based token, server: secret manager/DB, enterprise: workspace service account, etc.)
 * - Business logic should not care how Gmail is authenticated.
 *
 * Implementation examples
 * - Refresh token based (current approach using gmail.oauth.*)
 * - GoogleAuthorizationCodeFlow based
 * - Service account + domain-wide delegation
 */
public interface GmailAuthProvider {

    /**
     * Returns the HttpRequestInitializer used by the Gmail API client.
     *
     * In Google libraries, Credential typically implements HttpRequestInitializer.
     * - Injects Authorization header
     * - Refreshes access token automatically when expired
     */
    HttpRequestInitializer requestInitializer();
}
