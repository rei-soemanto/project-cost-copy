package com.costproject.app.data.repository

import com.costproject.app.data.DataError
import com.costproject.app.data.apiPath
import com.costproject.app.data.local.AuthTokens
import com.costproject.app.data.remote.DEFAULT_EXPORT_FILE_NAME
import com.costproject.app.data.remote.exportFileNameFrom
import com.costproject.app.data.respondError
import com.costproject.app.data.respondJson
import com.costproject.app.data.testContainer
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ExportRepositoryTest {

    /** Not a real workbook: every byte value 0..255, so any text decoding would corrupt it. */
    private val xlsxBytes = ByteArray(256) { it.toByte() }

    private fun signedIn(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        testContainer(handler = handler).also { it.tokenStorage.saveTokens(AuthTokens("access", "refresh")) }

    @Test
    fun downloads_the_file_bytes_intact_with_the_servers_file_name() = runTest {
        var requested = ""
        val container = signedIn { request ->
            requested = request.apiPath
            respond(
                xlsxBytes,
                HttpStatusCode.OK,
                headersOf(
                    HttpHeaders.ContentType to listOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                    HttpHeaders.ContentDisposition to listOf("attachment; filename=\"CostProject-Backup-2026-09-23.xlsx\"")
                )
            )
        }

        val file = container.exportRepository.exportMine().getOrThrow()

        assertEquals("export/projects.xlsx", requested)
        assertEquals("CostProject-Backup-2026-09-23.xlsx", file.fileName)
        assertContentEquals(xlsxBytes, file.bytes)
    }

    @Test
    fun the_admin_export_uses_the_all_users_endpoint() = runTest {
        var requested = ""
        val container = signedIn { request ->
            requested = request.apiPath
            respond(xlsxBytes, HttpStatusCode.OK)
        }
        val file = container.exportRepository.exportAll().getOrThrow()
        assertEquals("export/all.xlsx", requested)
        // No Content-Disposition from the server: falls back to a sensible name.
        assertEquals(DEFAULT_EXPORT_FILE_NAME, file.fileName)
    }

    @Test
    fun a_non_admin_asking_for_everything_gets_forbidden() = runTest {
        val container = signedIn { respondError(HttpStatusCode.Forbidden, "FORBIDDEN") }
        assertIs<DataError.Forbidden>(container.exportRepository.exportAll().exceptionOrNull())
    }

    @Test
    fun is_admin_comes_from_the_me_endpoint() = runTest {
        var requested = ""
        val container = signedIn { request ->
            requested = request.apiPath
            respondJson("""{"data":{"id":"u1","email":"a@b.com","fullName":"A","isAdmin":true}}""")
        }
        assertEquals(true, container.exportRepository.isAdmin().getOrThrow())
        assertEquals("me", requested)
    }

    @Test
    fun file_name_parsing_keeps_only_a_safe_xlsx_base_name() {
        assertEquals("Backup.xlsx", exportFileNameFrom("attachment; filename=\"Backup.xlsx\""))
        assertEquals("Backup.xlsx", exportFileNameFrom("attachment; filename=\"../../etc/Backup.xlsx\""))
        assertEquals("Backup.xlsx", exportFileNameFrom("attachment; filename=\"Backup\""))
        assertEquals(DEFAULT_EXPORT_FILE_NAME, exportFileNameFrom(null))
        assertEquals(DEFAULT_EXPORT_FILE_NAME, exportFileNameFrom("attachment"))
        assertEquals(DEFAULT_EXPORT_FILE_NAME, exportFileNameFrom("attachment; filename=\".xlsx\""))
    }
}
