package com.costproject.app.data.repository

import com.costproject.app.data.DataError
import com.costproject.app.data.apiPath
import com.costproject.app.data.local.AuthTokens
import com.costproject.app.data.respondError
import com.costproject.app.data.respondJson
import com.costproject.app.data.testContainer
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Session behaviour of the authenticated client, through the real AppContainer wiring. */
class AuthSessionTest {

    private fun authResponse(access: String, refresh: String, email: String = "a@b.com") =
        """{"data":{"accessToken":"$access","refreshToken":"$refresh","user":{"id":"u1","email":"$email","fullName":"A"}}}"""

    @Test
    fun an_expired_token_is_refreshed_once_and_the_request_replayed() = runTest {
        val sentTokens = mutableListOf<String?>()
        val container = testContainer { request ->
            when (request.apiPath) {
                "projects" -> {
                    val auth = request.headers[HttpHeaders.Authorization]
                    sentTokens += auth
                    if (auth == "Bearer new-access") respondJson("""{"data":[]}""")
                    else respondError(HttpStatusCode.Unauthorized, "TOKEN_EXPIRED")
                }
                "auth/refresh" -> respondJson("""{"data":{"accessToken":"new-access","refreshToken":"new-refresh"}}""")
                else -> error("unexpected request ${request.url}")
            }
        }
        container.tokenStorage.saveTokens(AuthTokens("old-access", "old-refresh"))

        assertTrue(container.projectRepository.list().isSuccess)
        assertEquals(listOf<String?>("Bearer old-access", "Bearer new-access"), sentTokens)
        assertEquals(AuthTokens("new-access", "new-refresh"), container.tokenStorage.loadTokens())
    }

    @Test
    fun a_rejected_refresh_ends_the_session() = runTest {
        val container = testContainer { request ->
            when (request.apiPath) {
                "projects" -> respondError(HttpStatusCode.Unauthorized, "TOKEN_EXPIRED")
                "auth/refresh" -> respondError(HttpStatusCode.Unauthorized, "INVALID_REFRESH_TOKEN")
                else -> error("unexpected request ${request.url}")
            }
        }
        container.tokenStorage.saveTokens(AuthTokens("old-access", "revoked-refresh"))
        assertTrue(container.authRepository.hasSession.value)

        val error = container.projectRepository.list().exceptionOrNull()

        assertIs<DataError.Unauthorized>(error)
        // hasSession flipping to false is what sends the UI back to login.
        assertFalse(container.authRepository.hasSession.value)
        assertEquals(null, container.tokenStorage.loadTokens())
    }

    @Test
    fun a_network_failure_during_refresh_keeps_the_user_signed_in() = runTest {
        val container = testContainer { request ->
            when (request.apiPath) {
                "projects" -> respondError(HttpStatusCode.Unauthorized, "TOKEN_EXPIRED")
                "auth/refresh" -> throw IOException("connection dropped")
                else -> error("unexpected request ${request.url}")
            }
        }
        container.tokenStorage.saveTokens(AuthTokens("old-access", "still-valid-refresh"))

        val error = container.projectRepository.list().exceptionOrNull()

        // Reported as a connectivity problem, not "session expired"...
        assertIs<DataError.Network>(error)
        // ...and the session survives for the next attempt.
        assertTrue(container.authRepository.hasSession.value)
        assertEquals("still-valid-refresh", container.tokenStorage.loadTokens()?.refreshToken)
    }

    @Test
    fun switching_accounts_sends_the_new_accounts_token_not_a_cached_one() = runTest {
        val sentTokens = mutableListOf<String?>()
        val container = testContainer { request ->
            when (request.apiPath) {
                "auth/login" -> {
                    val body = (request.body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    if ("alice" in body) respondJson(authResponse("alice-token", "r1", "alice@x.com"))
                    else respondJson(authResponse("bob-token", "r2", "bob@x.com"))
                }
                "projects" -> {
                    sentTokens += request.headers[HttpHeaders.Authorization]
                    respondJson("""{"data":[]}""")
                }
                else -> error("unexpected request ${request.url}")
            }
        }

        container.authRepository.login("alice@x.com", "pw").getOrThrow()
        container.projectRepository.list().getOrThrow()
        container.authRepository.logout()
        container.authRepository.login("bob@x.com", "pw").getOrThrow()
        container.projectRepository.list().getOrThrow()

        // Without clearing Ktor's in-memory token cache on login/logout, the
        // second request would still carry Alice's token.
        assertEquals(listOf<String?>("Bearer alice-token", "Bearer bob-token"), sentTokens)
    }

    @Test
    fun login_stores_the_session_and_the_user() = runTest {
        val container = testContainer { respondJson(authResponse("a", "r")) }
        val user = container.authRepository.login(" a@b.com ", "pw").getOrThrow()

        assertEquals("a@b.com", user.email)
        assertEquals(user, container.authRepository.currentUser)
        assertTrue(container.authRepository.hasSession.value)
    }
}
