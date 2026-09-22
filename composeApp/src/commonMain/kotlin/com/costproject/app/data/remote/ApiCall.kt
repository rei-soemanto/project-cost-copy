package com.costproject.app.data.remote

import com.costproject.app.data.DataError
import com.costproject.app.data.dto.ErrorBody
import com.costproject.app.data.dto.ErrorResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

/**
 * Runs an API call and converts every failure into a [DataError].
 *
 * This is the repository error boundary. Specific exception types are caught -
 * never a bare `catch (e: Exception)`, which would also swallow
 * [CancellationException] and break coroutine cancellation, leaving a screen
 * that was closed still waiting on its request.
 */
suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: ResponseException) {
        Result.failure(e.toDataError())
    } catch (e: IOException) {
        // Includes HttpRequestTimeoutException and connect/socket timeouts.
        Result.failure(DataError.Network(e))
    } catch (e: ContentConvertException) {
        Result.failure(DataError.Server(cause = e))
    } catch (e: SerializationException) {
        Result.failure(DataError.Server(cause = e))
    }

private suspend fun ResponseException.toDataError(): DataError {
    val status = response.status.value
    val body = readErrorBody()

    return when (status) {
        400 -> DataError.Validation(
            message = "Data yang dimasukkan tidak valid.",
            fieldErrors = body?.details?.associate { it.path to it.message } ?: emptyMap()
        )
        401 -> if (body?.code == "INVALID_CREDENTIALS") DataError.InvalidCredentials() else DataError.Unauthorized()
        404 -> DataError.NotFound()
        409 -> DataError.Conflict(
            when (body?.code) {
                "EMAIL_TAKEN" -> "Email sudah terdaftar."
                "PROJECT_EXISTS" -> "Project sudah ada."
                else -> "Data bertentangan dengan data yang sudah ada."
            }
        )
        429 -> DataError.RateLimited()
        else -> DataError.Server(statusCode = status, cause = this)
    }
}

/**
 * The error envelope, or null if the body is not one - e.g. an HTML error page
 * from a proxy in front of the API. The status code alone then decides.
 */
private suspend fun ResponseException.readErrorBody(): ErrorBody? =
    try {
        response.body<ErrorResponse>().error
    } catch (e: CancellationException) {
        throw e
    } catch (_: ContentConvertException) {
        null
    } catch (_: SerializationException) {
        null
    } catch (_: IOException) {
        null
    }
