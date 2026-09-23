package com.costproject.app.data.remote

import com.costproject.app.data.dto.ApiResponse
import com.costproject.app.data.dto.ProfileDto
import com.costproject.app.domain.model.ExportFile
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders

/** Profile and Excel export endpoints. Takes the authenticated client. */
class ExportApiService(private val client: HttpClient) {

    suspend fun me(): ProfileDto = client.get("me").body<ApiResponse<ProfileDto>>().data

    suspend fun downloadMine(): ExportFile = download("export/projects.xlsx")

    suspend fun downloadAll(): ExportFile = download("export/all.xlsx")

    /**
     * The .xlsx comes back as the raw response body, not in the JSON envelope.
     * Errors still arrive as JSON with a non-2xx status, which expectSuccess
     * turns into an exception before this reads the body.
     */
    private suspend fun download(path: String): ExportFile {
        val response = client.get(path)
        return ExportFile(
            fileName = exportFileNameFrom(response.headers[HttpHeaders.ContentDisposition]),
            bytes = response.body<ByteArray>()
        )
    }
}

internal const val DEFAULT_EXPORT_FILE_NAME = "CostProject-Backup.xlsx"

/**
 * The file name from a Content-Disposition header, reduced to a safe base name
 * ending in .xlsx. The name is only a suggestion shown in the save picker, but
 * it should never carry a path or an unexpected extension.
 */
internal fun exportFileNameFrom(contentDisposition: String?): String {
    val raw = contentDisposition
        ?.let { runCatching { ContentDisposition.parse(it) }.getOrNull() }
        ?.parameter(ContentDisposition.Parameters.FileName)
        ?: return DEFAULT_EXPORT_FILE_NAME
    val base = raw.substringAfterLast('/').substringAfterLast('\\').trim()
    return when {
        base.isEmpty() || base.startsWith(".") -> DEFAULT_EXPORT_FILE_NAME
        base.endsWith(".xlsx", ignoreCase = true) -> base
        else -> "$base.xlsx"
    }
}
