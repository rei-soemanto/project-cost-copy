package com.costproject.app.data.remote

import com.costproject.app.data.DataError
import com.costproject.app.data.respondError
import com.costproject.app.data.respondJson
import com.costproject.app.data.testContainer
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** The repository error boundary: every failure reaches callers as a DataError. */
class ErrorMappingTest {

    private suspend fun loginFailure(respond: suspend io.ktor.client.engine.mock.MockRequestHandleScope.() -> io.ktor.client.request.HttpResponseData): Throwable? {
        val container = testContainer { respond() }
        return container.authRepository.login("a@b.com", "pw").exceptionOrNull()
    }

    @Test
    fun wrong_password_is_invalid_credentials_not_session_expired() = runTest {
        val error = loginFailure { respondError(HttpStatusCode.Unauthorized, "INVALID_CREDENTIALS") }
        assertIs<DataError.InvalidCredentials>(error)
    }

    @Test
    fun duplicate_email_is_a_conflict_with_a_readable_message() = runTest {
        val container = testContainer { respondError(HttpStatusCode.Conflict, "EMAIL_TAKEN") }
        val error = container.authRepository.register("A", "a@b.com", "password123").exceptionOrNull()
        assertIs<DataError.Conflict>(error)
        assertEquals("Email sudah terdaftar.", error.message)
    }

    @Test
    fun validation_details_become_a_field_error_map() = runTest {
        val error = loginFailure {
            respondError(
                HttpStatusCode.BadRequest, "VALIDATION_ERROR",
                details = """[{"path":"email","message":"Invalid email"}]"""
            )
        }
        assertIs<DataError.Validation>(error)
        assertEquals(mapOf("email" to "Invalid email"), error.fieldErrors)
    }

    @Test
    fun rate_limit_maps_to_rate_limited() = runTest {
        assertIs<DataError.RateLimited>(loginFailure { respondError(HttpStatusCode.TooManyRequests, "RATE_LIMITED") })
    }

    @Test
    fun server_error_maps_to_server() = runTest {
        val error = loginFailure { respondError(HttpStatusCode.InternalServerError, "INTERNAL_ERROR") }
        assertIs<DataError.Server>(error)
        assertEquals(500, error.statusCode)
    }

    @Test
    fun a_non_json_error_page_still_maps_by_status_code() = runTest {
        // e.g. a reverse proxy's HTML 502 page in front of the API.
        val error = loginFailure { respondJson("<html>Bad Gateway</html>", HttpStatusCode.BadGateway) }
        assertIs<DataError.Server>(error)
        assertEquals(502, error.statusCode)
    }

    @Test
    fun unreachable_server_is_a_network_error() = runTest {
        val container = testContainer { throw IOException("offline") }
        assertIs<DataError.Network>(container.authRepository.login("a@b.com", "pw").exceptionOrNull())
    }

    @Test
    fun cancellation_propagates_instead_of_becoming_a_failure_result() = runTest {
        var result: Result<Unit>? = null
        val job = launch { result = safeApiCall { awaitCancellation() } }
        testScheduler.advanceUntilIdle()
        job.cancelAndJoin()
        // Had safeApiCall swallowed the CancellationException, it would have
        // returned a failure Result and this would be non-null.
        assertNull(result)
    }
}
