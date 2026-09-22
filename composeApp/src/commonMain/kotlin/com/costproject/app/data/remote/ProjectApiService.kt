package com.costproject.app.data.remote

import com.costproject.app.data.dto.ApiResponse
import com.costproject.app.data.dto.CreateProjectRequest
import com.costproject.app.data.dto.ProjectDto
import com.costproject.app.data.dto.ReplaceItemsRequest
import com.costproject.app.data.dto.UpdateProjectRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.encodeURLPathPart

/** Project endpoints. Takes the authenticated client. */
class ProjectApiService(private val client: HttpClient) {

    suspend fun list(): List<ProjectDto> =
        client.get("projects").body<ApiResponse<List<ProjectDto>>>().data

    suspend fun get(id: String): ProjectDto =
        client.get(projectPath(id)).body<ApiResponse<ProjectDto>>().data

    suspend fun create(request: CreateProjectRequest): ProjectDto =
        client.post("projects") { setBody(request) }.body<ApiResponse<ProjectDto>>().data

    suspend fun update(id: String, request: UpdateProjectRequest): ProjectDto =
        client.patch(projectPath(id)) { setBody(request) }.body<ApiResponse<ProjectDto>>().data

    suspend fun replaceItems(id: String, request: ReplaceItemsRequest): ProjectDto =
        client.put("${projectPath(id)}/items") { setBody(request) }.body<ApiResponse<ProjectDto>>().data

    suspend fun delete(id: String) {
        client.delete(projectPath(id))
    }

    private fun projectPath(id: String) = "projects/${id.encodeURLPathPart()}"
}
