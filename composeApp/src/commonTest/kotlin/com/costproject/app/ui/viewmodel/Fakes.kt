package com.costproject.app.ui.viewmodel

import com.costproject.app.data.DataError
import com.costproject.app.data.repository.AuthRepository
import com.costproject.app.data.repository.ExportRepository
import com.costproject.app.data.repository.ProjectRepository
import com.costproject.app.domain.model.ExportFile
import com.costproject.app.domain.model.Project
import com.costproject.app.domain.model.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAuthRepository : AuthRepository {
    private val _hasSession = MutableStateFlow(false)
    override val hasSession: StateFlow<Boolean> = _hasSession.asStateFlow()
    override var currentUser: User? = null

    var result: Result<User> = Result.success(User("u1", "a@b.com", "Rei"))
    val loginCalls = mutableListOf<Pair<String, String>>()
    val registerCalls = mutableListOf<Triple<String, String, String>>()

    override suspend fun login(email: String, password: String): Result<User> {
        loginCalls += email to password
        return result.also { r -> r.onSuccess { currentUser = it; _hasSession.value = true } }
    }

    override suspend fun register(fullName: String, email: String, password: String): Result<User> {
        registerCalls += Triple(fullName, email, password)
        return result.also { r -> r.onSuccess { currentUser = it; _hasSession.value = true } }
    }

    override fun logout() {
        currentUser = null
        _hasSession.value = false
    }
}

/**
 * An in-memory "server". Suspends only through [delay], so under runTest every
 * call runs on virtual time and tests stay deterministic.
 */
class FakeProjectRepository(initial: List<Project> = emptyList()) : ProjectRepository {

    /** What the server currently holds. */
    val stored = initial.associateBy { it.id }.toMutableMap()

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    override val changes: Flow<Unit> = _changes.asSharedFlow()

    /** When set, every call fails with this. */
    var failure: DataError? = null
    /** Simulated network latency for writes. */
    var writeDelayMillis = 0L

    var listCalls = 0
    val saveItemsCalls = mutableListOf<Project>()
    val updateInfoCalls = mutableListOf<Project>()
    val deleteCalls = mutableListOf<String>()

    private var writesInFlight = 0
    var maxConcurrentWrites = 0
        private set

    override suspend fun list(): Result<List<Project>> {
        listCalls++
        failure?.let { return Result.failure(it) }
        return Result.success(stored.values.toList())
    }

    override suspend fun get(id: String): Result<Project> {
        failure?.let { return Result.failure(it) }
        return stored[id]?.let { Result.success(it) } ?: Result.failure(DataError.NotFound())
    }

    override suspend fun create(project: Project): Result<Project> = write {
        val created = project.copy(createdAt = "22 Sep 2026")
        stored[created.id] = created
        created
    }

    override suspend fun updateInfo(
        id: String,
        name: String,
        customer: String,
        pic: String,
        hargaKontrak: String
    ): Result<Project> = write {
        val updated = stored.getValue(id).copy(name = name, customer = customer, pic = pic, hargaKontrak = hargaKontrak)
        updateInfoCalls += updated
        stored[id] = updated
        updated
    }

    override suspend fun saveItems(project: Project): Result<Project> = write {
        saveItemsCalls += project
        val current = stored.getValue(project.id)
        val updated = current.copy(
            listJasa = project.listJasa,
            listBarang = project.listBarang,
            listTransportasi = project.listTransportasi,
            listLainLain = project.listLainLain
        )
        stored[project.id] = updated
        updated
    }

    override suspend fun delete(id: String): Result<Unit> = write {
        deleteCalls += id
        stored.remove(id)
        Unit
    }

    /** Emits a change signal from outside, as another screen's save would. */
    fun signalChange() {
        _changes.tryEmit(Unit)
    }

    private suspend fun <T> write(block: () -> T): Result<T> {
        writesInFlight++
        maxConcurrentWrites = maxOf(maxConcurrentWrites, writesInFlight)
        try {
            if (writeDelayMillis > 0) delay(writeDelayMillis)
            failure?.let { return Result.failure(it) }
            return Result.success(block()).also { _changes.tryEmit(Unit) }
        } finally {
            writesInFlight--
        }
    }
}

class FakeExportRepository(
    var admin: Result<Boolean> = Result.success(false),
    var file: Result<ExportFile> = Result.success(ExportFile("CostProject-Backup-2026-09-23.xlsx", byteArrayOf(0x50, 0x4B)))
) : ExportRepository {
    var mineCalls = 0
    var allCalls = 0
    /** Simulated download time, so tests can tap twice while one is running. */
    var delayMillis = 0L

    override suspend fun isAdmin(): Result<Boolean> = admin

    override suspend fun exportMine(): Result<ExportFile> {
        mineCalls++
        if (delayMillis > 0) delay(delayMillis)
        return file
    }

    override suspend fun exportAll(): Result<ExportFile> {
        allCalls++
        if (delayMillis > 0) delay(delayMillis)
        return file
    }
}
