package com.costproject.app.data

import com.costproject.app.data.container.AppContainer
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf

const val TEST_BASE_URL = "https://api.test/api/v1/"

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
    respond(body, status, jsonHeaders)

fun MockRequestHandleScope.respondError(
    status: HttpStatusCode,
    code: String,
    message: String = "error",
    details: String? = null
): HttpResponseData {
    val detailsJson = if (details == null) "" else ""","details":$details"""
    return respondJson("""{"error":{"code":"$code","message":"$message"$detailsJson}}""", status)
}

/** The request body as text, for asserting what the client actually sent. */
fun HttpRequestData.bodyText(): String = when (val content = body) {
    is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
    else -> ""
}

/** Path relative to the API root, e.g. "projects/abc". */
val HttpRequestData.apiPath: String get() = url.encodedPath.removePrefix("/api/v1/")

fun projectJson(
    id: String = "p1",
    name: String = "Project",
    hargaKontrak: Long = 0,
    items: String = "[]"
) = """{"id":"$id","name":"$name","customer":"","pic":"","hargaKontrak":$hargaKontrak,"items":$items,""" +
    """"createdAt":"2026-09-22T05:00:00.000Z","updatedAt":"2026-09-22T05:00:00.000Z"}"""

/**
 * The real AppContainer - real clients, repositories and wiring - over a mock
 * engine and in-memory settings. Retries are off so failure paths do not wait
 * out real backoff delays.
 */
fun testContainer(
    legacySettings: MapSettings = MapSettings(),
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
) = AppContainer(
    engine = MockEngine(handler),
    baseUrl = TEST_BASE_URL,
    secureSettings = MapSettings(),
    legacySettings = legacySettings,
    httpMaxRetries = 0
)
