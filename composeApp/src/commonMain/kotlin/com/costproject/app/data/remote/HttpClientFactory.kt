package com.costproject.app.data.remote

import com.costproject.app.data.local.AuthTokens
import com.costproject.app.data.local.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The single JSON configuration shared by both clients.
 *
 * - encodeDefaults: without it, a property equal to its declared default is
 *   silently dropped from the request body.
 * - explicitNulls = false: optional fields left null are omitted rather than
 *   sent as `null`. The server's PATCH schema accepts a missing field but
 *   rejects an explicit null.
 */
val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
    explicitNulls = false
}

/**
 * Client for login, register and refresh.
 *
 * Deliberately has no Auth plugin and no retry:
 * - With the Auth plugin, a failed login's 401 would be taken as an expired
 *   token and trigger a refresh.
 * - Refresh rotates the token. If a refresh timed out after the server had
 *   already rotated, an automatic retry would replay the spent token, which the
 *   server treats as theft and answers by revoking every session.
 * These are user-initiated calls; the user retries them.
 */
fun createPublicHttpClient(
    engine: HttpClientEngine,
    baseUrl: String,
    enableLogging: Boolean = false
): HttpClient = HttpClient(engine) {
    installCommon(baseUrl, enableLogging)
    installTimeouts()
}

/**
 * Client for everything behind authentication.
 *
 * Attaches the stored bearer token to every request. On a 401 it calls
 * [refreshSession] once - Ktor serialises concurrent refreshes - then replays
 * the request with the new token.
 *
 * @param refreshSession exchanges a refresh token for a new pair. Returns null
 *   when the session is dead; throws on network failure, which fails the
 *   original request with a network error instead of logging the user out.
 * @param maxRetries retries for 5xx and network failures. Tests pass 0 to avoid
 *   the real backoff delays.
 */
fun createAuthenticatedHttpClient(
    engine: HttpClientEngine,
    baseUrl: String,
    tokenStorage: TokenStorage,
    refreshSession: suspend (refreshToken: String) -> AuthTokens?,
    enableLogging: Boolean = false,
    maxRetries: Int = 2
): HttpClient = HttpClient(engine) {
    installCommon(baseUrl, enableLogging)

    // Safe to retry here: every project call is idempotent. Creates carry a
    // client-generated id, so a retried create answers 409 rather than
    // duplicating, and ProjectRepository treats that as success.
    // Must be installed before HttpTimeout, or timeouts are never retried.
    if (maxRetries > 0) {
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = maxRetries)
            retryOnException(maxRetries = maxRetries, retryOnTimeout = true)
            exponentialDelay()
        }
    }
    installTimeouts()

    install(Auth) {
        bearer {
            loadTokens {
                tokenStorage.loadTokens()?.let { BearerTokens(it.accessToken, it.refreshToken) }
            }
            refreshTokens {
                val refreshToken = oldTokens?.refreshToken
                    ?: tokenStorage.loadTokens()?.refreshToken
                    ?: return@refreshTokens null
                refreshSession(refreshToken)?.let { BearerTokens(it.accessToken, it.refreshToken) }
            }
            // Every endpoint on this client needs the token, so send it up front
            // instead of waiting for a 401 challenge first.
            sendWithoutRequest { true }
        }
    }
}

/**
 * Drops the bearer tokens Ktor has cached in memory, so the next request
 * re-reads storage. Call after login and logout; otherwise the client keeps
 * sending the previous session's token.
 */
fun HttpClient.clearCachedBearerTokens() {
    authProvider<BearerAuthProvider>()?.clearToken()
}

private fun HttpClientConfig<*>.installCommon(baseUrl: String, enableLogging: Boolean) {
    // Non-2xx responses throw, and repositories map the exception. One model
    // project-wide: nothing downstream inspects status codes by hand.
    expectSuccess = true

    install(ContentNegotiation) { json(AppJson) }

    if (enableLogging) {
        install(Logging) {
            level = LogLevel.INFO
            // Never write credentials to the log.
            sanitizeHeader { it == HttpHeaders.Authorization }
        }
    }

    defaultRequest {
        url(baseUrl)
        contentType(ContentType.Application.Json)
    }
}

private fun HttpClientConfig<*>.installTimeouts() {
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 15_000
    }
}
