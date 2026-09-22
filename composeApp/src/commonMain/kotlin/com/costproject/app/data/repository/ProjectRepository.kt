package com.costproject.app.data.repository

import com.costproject.app.data.DataError
import com.costproject.app.data.dto.ReplaceItemsRequest
import com.costproject.app.data.local.LegacyProjectStore
import com.costproject.app.data.mapper.ProjectMapper
import com.costproject.app.data.remote.ProjectApiService
import com.costproject.app.data.remote.safeApiCall
import com.costproject.app.domain.model.Project
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The user's projects. Every method returns a [Result] whose failure is always a
 * [DataError]. ViewModels depend on this interface, so tests can fake it.
 */
interface ProjectRepository {
    /** Emits after every successful write, so screens showing project data know to refresh. */
    val changes: Flow<Unit>

    suspend fun list(): Result<List<Project>>
    suspend fun get(id: String): Result<Project>
    suspend fun create(project: Project): Result<Project>
    suspend fun updateInfo(id: String, name: String, customer: String, pic: String, hargaKontrak: String): Result<Project>
    /** Replaces the project's whole item list. What the editor's autosave calls. */
    suspend fun saveItems(project: Project): Result<Project>
    suspend fun delete(id: String): Result<Unit>
}

/** Server-backed [ProjectRepository]. */
class DefaultProjectRepository(
    private val api: ProjectApiService,
    private val legacyStore: LegacyProjectStore
) : ProjectRepository {

    // Only "something changed" matters, never how many times: a one-slot buffer
    // that drops the older signal means emitting never suspends or fails.
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val changes: Flow<Unit> = _changes.asSharedFlow()

    /**
     * All of the user's projects, newest first.
     *
     * First uploads any projects left on the device from before the app had a
     * server. That runs here, on the first list after sign-in, so existing work
     * appears in the user's account without a separate migration step.
     */
    override suspend fun list(): Result<List<Project>> {
        importLegacyProjects()
        return safeApiCall { api.list().map { ProjectMapper.toDomain(it) } }
    }

    override suspend fun get(id: String): Result<Project> =
        safeApiCall { ProjectMapper.toDomain(api.get(id)) }

    override suspend fun create(project: Project): Result<Project> {
        val result = safeApiCall { ProjectMapper.toDomain(api.create(ProjectMapper.toCreateRequest(project))) }
        // The HTTP client retries on timeouts. If the first attempt did land,
        // the retry answers 409 for our own id - the project exists, so fetch it.
        if (result.exceptionOrNull() is DataError.Conflict) return get(project.id).alsoSignalChange()
        return result.alsoSignalChange()
    }

    override suspend fun updateInfo(
        id: String,
        name: String,
        customer: String,
        pic: String,
        hargaKontrak: String
    ): Result<Project> = safeApiCall {
        ProjectMapper.toDomain(api.update(id, ProjectMapper.toUpdateRequest(name, customer, pic, hargaKontrak)))
    }.alsoSignalChange()

    override suspend fun saveItems(project: Project): Result<Project> = safeApiCall {
        ProjectMapper.toDomain(api.replaceItems(project.id, ReplaceItemsRequest(ProjectMapper.toItemDtos(project))))
    }.alsoSignalChange()

    override suspend fun delete(id: String): Result<Unit> {
        val result = safeApiCall { api.delete(id) }
        // Already gone - e.g. a retried delete whose first attempt succeeded.
        if (result.exceptionOrNull() is DataError.NotFound) return Result.success(Unit)
        return result.alsoSignalChange()
    }

    private fun <T> Result<T>.alsoSignalChange(): Result<T> = also { if (it.isSuccess) _changes.tryEmit(Unit) }

    /**
     * Uploads legacy on-device projects, removing each from the device only once
     * the server has it.
     *
     * - Accepted, or 409 (an earlier interrupted import already sent it): done.
     * - Network, auth or server trouble: stop, keep the rest for next time.
     * - Rejected as invalid: keep it on the device and carry on with the others,
     *   so one bad record can neither block the rest nor be destroyed.
     */
    private suspend fun importLegacyProjects() {
        val pending = legacyStore.load()
        if (pending.isEmpty()) return

        val remaining = pending.toMutableList()
        for (project in pending) {
            val result = safeApiCall {
                // The old app displayed a blank name as "Project tanpa nama". The
                // server requires a name, so send what the user always saw.
                val named = project.copy(name = project.name.ifBlank { "Project tanpa nama" })
                api.create(ProjectMapper.toCreateRequest(named))
            }
            when (result.exceptionOrNull()) {
                null, is DataError.Conflict -> remaining.remove(project)
                is DataError.Network, is DataError.Unauthorized, is DataError.Server -> break
                else -> Unit
            }
        }
        legacyStore.replace(remaining)
    }
}
