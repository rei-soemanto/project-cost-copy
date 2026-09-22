package com.costproject.app.data.repository

import com.costproject.app.data.DataError
import com.costproject.app.data.apiPath
import com.costproject.app.data.bodyText
import com.costproject.app.data.local.AuthTokens
import com.costproject.app.data.local.LegacyProjectStore
import com.costproject.app.data.projectJson
import com.costproject.app.data.respondError
import com.costproject.app.data.respondJson
import com.costproject.app.data.testContainer
import com.costproject.app.domain.model.Project
import com.russhwolf.settings.MapSettings
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProjectRepositoryTest {

    private fun signedIn(
        legacySettings: MapSettings = MapSettings(),
        handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData
    ) = testContainer(legacySettings, handler).also {
        it.tokenStorage.saveTokens(AuthTokens("access", "refresh"))
    }

    @Test
    fun a_retried_create_that_answers_409_resolves_to_the_existing_project() = runTest {
        val container = signedIn { request ->
            when {
                request.method == HttpMethod.Post -> respondError(HttpStatusCode.Conflict, "PROJECT_EXISTS")
                request.apiPath == "projects/p1" -> respondJson("""{"data":${projectJson(id = "p1", name = "Already there")}}""")
                else -> error("unexpected ${request.method} ${request.url}")
            }
        }
        val result = container.projectRepository.create(Project(id = "p1", name = "Already there"))
        assertEquals("Already there", result.getOrThrow().name)
    }

    @Test
    fun deleting_something_already_gone_counts_as_success() = runTest {
        val container = signedIn { respondError(HttpStatusCode.NotFound, "NOT_FOUND") }
        assertTrue(container.projectRepository.delete("gone").isSuccess)
    }

    @Test
    fun patch_omits_unset_fields_instead_of_sending_null() = runTest {
        var sent = ""
        val container = signedIn { request ->
            sent = request.bodyText()
            respondJson("""{"data":${projectJson()}}""")
        }
        container.projectRepository.updateInfo("p1", "Name", "Cust", "PIC", "60.000.000").getOrThrow()

        // The server's PATCH schema accepts a missing field but rejects null.
        assertFalse("null" in sent, "request body contained a null: $sent")
        assertTrue("\"hargaKontrak\":60000000" in sent, "contract not sent as an integer: $sent")
    }

    @Test
    fun cost_items_are_sent_as_integers_not_formatted_text() = runTest {
        var sent = ""
        val container = signedIn { request ->
            sent = request.bodyText()
            respondJson("""{"data":${projectJson()}}""")
        }
        val project = Project(id = "p1", listJasa = listOf(com.costproject.app.domain.model.Jasa("j1", harga = "5.000.000")))
        container.projectRepository.saveItems(project).getOrThrow()
        assertTrue("\"amount\":5000000" in sent, sent)
    }

    // --- Legacy import -------------------------------------------------------

    private val twoLegacyProjects = """
        [{"id":111,"name":"Lama A","hargaKontrak":"1000","listJasa":[{"id":1,"harga":"500"}]},
         {"id":222,"name":"","hargaKontrak":"2000"}]
    """.trimIndent()

    @Test
    fun legacy_projects_are_uploaded_then_removed_from_the_device() = runTest {
        val legacy = MapSettings(LegacyProjectStore.KEY_PROJECTS to twoLegacyProjects)
        val uploaded = mutableListOf<String>()
        val container = signedIn(legacy) { request ->
            if (request.method == HttpMethod.Post) {
                uploaded += request.bodyText()
                // The first was already sent by an earlier interrupted import.
                if ("\"id\":\"111\"" in request.bodyText()) respondError(HttpStatusCode.Conflict, "PROJECT_EXISTS")
                else respondJson("""{"data":${projectJson(id = "222")}}""", HttpStatusCode.Created)
            } else {
                respondJson("""{"data":[${projectJson(id = "111")},${projectJson(id = "222")}]}""")
            }
        }

        val projects = container.projectRepository.list().getOrThrow()

        assertEquals(listOf("111", "222"), projects.map { it.id })
        assertEquals(2, uploaded.size)
        // Numeric legacy ids go up as strings the server accepts.
        assertTrue(uploaded.all { "\"id\":\"111\"" in it || "\"id\":\"222\"" in it })
        // A blank legacy name is sent as the label the old app displayed.
        assertTrue(uploaded.any { "Project tanpa nama" in it })
        assertFalse(LegacyProjectStore.KEY_PROJECTS in legacy.keys, "legacy data should be cleared once uploaded")
    }

    @Test
    fun a_failed_legacy_upload_keeps_the_data_on_the_device() = runTest {
        val legacy = MapSettings(LegacyProjectStore.KEY_PROJECTS to twoLegacyProjects)
        val container = signedIn(legacy) { request ->
            if (request.method == HttpMethod.Post) respondError(HttpStatusCode.ServiceUnavailable, "INTERNAL_ERROR")
            else respondJson("""{"data":[]}""")
        }

        container.projectRepository.list()

        // Nothing was accepted, so nothing may be deleted - it retries next time.
        assertEquals(2, LegacyProjectStore(legacy).load().size)
    }

    @Test
    fun one_invalid_legacy_project_does_not_block_or_destroy_the_others() = runTest {
        val legacy = MapSettings(LegacyProjectStore.KEY_PROJECTS to twoLegacyProjects)
        val container = signedIn(legacy) { request ->
            if (request.method == HttpMethod.Post) {
                if ("\"id\":\"111\"" in request.bodyText()) respondError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
                else respondJson("""{"data":${projectJson(id = "222")}}""", HttpStatusCode.Created)
            } else {
                respondJson("""{"data":[]}""")
            }
        }

        container.projectRepository.list()

        // 222 went up; 111 was rejected and is kept on the device, not dropped.
        assertEquals(listOf("111"), LegacyProjectStore(legacy).load().map { it.id })
    }

    @Test
    fun list_failure_surfaces_as_a_data_error() = runTest {
        val container = signedIn { respondError(HttpStatusCode.InternalServerError, "INTERNAL_ERROR") }
        assertIs<DataError.Server>(container.projectRepository.list().exceptionOrNull())
    }
}
