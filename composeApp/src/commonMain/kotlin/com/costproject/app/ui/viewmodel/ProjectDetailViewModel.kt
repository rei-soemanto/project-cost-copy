package com.costproject.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.costproject.app.data.repository.ProjectRepository
import com.costproject.app.domain.model.Barang
import com.costproject.app.domain.model.Jasa
import com.costproject.app.domain.model.LainLain
import com.costproject.app.domain.model.Project
import com.costproject.app.domain.model.Transportasi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Whether the on-screen project matches what the server has. */
sealed interface SaveStatus {
    data object Saved : SaveStatus
    /** Edited; waiting for typing to pause before saving. */
    data object Pending : SaveStatus
    data object Saving : SaveStatus
    data class Failed(val message: String) : SaveStatus
}

sealed interface ProjectDetailUiState {
    data object Loading : ProjectDetailUiState
    data class Error(val message: String) : ProjectDetailUiState
    data class Editing(val project: Project, val saveStatus: SaveStatus) : ProjectDetailUiState
}

/**
 * Edits one project with autosave.
 *
 * Every edit applies to the on-screen project at once, so typing never waits on
 * the network. A save goes out once typing pauses for [saveDelayMillis].
 *
 * - Saves run in [applicationScope], not viewModelScope, so an edit made just
 *   before leaving the screen still reaches the server after the screen closes.
 * - Saves are serialised with a mutex and each sends the newest state. Two
 *   overlapping requests could otherwise land out of order, leaving the older
 *   one on the server.
 * - A failed save keeps the user's edits and offers a retry. Rolling back text
 *   the user just typed would lose their work.
 * - The server's response is not written back over the screen: the user may
 *   have typed more while the request was in flight.
 */
class ProjectDetailViewModel(
    private val projectId: String,
    private val repository: ProjectRepository,
    private val applicationScope: CoroutineScope,
    private val saveDelayMillis: Long = DEFAULT_SAVE_DELAY_MILLIS
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProjectDetailUiState>(ProjectDetailUiState.Loading)
    val uiState: StateFlow<ProjectDetailUiState> = _uiState.asStateFlow()

    /** The state the server last confirmed. Edits are diffed against it. Guarded by [saveMutex]. */
    private var lastSaved: Project? = null
    private val saveMutex = Mutex()
    private var debounceJob: Job? = null

    init {
        load()
    }

    fun load() {
        _uiState.value = ProjectDetailUiState.Loading
        viewModelScope.launch {
            repository.get(projectId)
                .onSuccess { project ->
                    saveMutex.withLock { lastSaved = project }
                    _uiState.value = ProjectDetailUiState.Editing(project, SaveStatus.Saved)
                }
                .onFailure { _uiState.value = ProjectDetailUiState.Error(it.message ?: "Gagal memuat project.") }
        }
    }

    // --- Edits -------------------------------------------------------------

    fun addJasa() = edit { it.copy(listJasa = it.listJasa + Jasa()) }
    fun removeJasa(id: String) = edit { p -> p.copy(listJasa = p.listJasa.filterNot { it.id == id }) }
    fun updateJasa(id: String, transform: (Jasa) -> Jasa) =
        edit { p -> p.copy(listJasa = p.listJasa.map { if (it.id == id) transform(it) else it }) }

    fun addBarang() = edit { it.copy(listBarang = it.listBarang + Barang()) }
    fun removeBarang(id: String) = edit { p -> p.copy(listBarang = p.listBarang.filterNot { it.id == id }) }
    fun updateBarang(id: String, transform: (Barang) -> Barang) =
        edit { p -> p.copy(listBarang = p.listBarang.map { if (it.id == id) transform(it) else it }) }

    fun addTransportasi() = edit { p ->
        if (p.listTransportasi.size < Project.MAX_TRANSPORTASI) p.copy(listTransportasi = p.listTransportasi + Transportasi())
        else p
    }
    fun removeTransportasi(id: String) =
        edit { p -> p.copy(listTransportasi = p.listTransportasi.filterNot { it.id == id }) }
    fun updateTransportasi(id: String, transform: (Transportasi) -> Transportasi) =
        edit { p -> p.copy(listTransportasi = p.listTransportasi.map { if (it.id == id) transform(it) else it }) }

    fun addLainLain() = edit { it.copy(listLainLain = it.listLainLain + LainLain()) }
    fun removeLainLain(id: String) = edit { p -> p.copy(listLainLain = p.listLainLain.filterNot { it.id == id }) }
    fun updateLainLain(id: String, transform: (LainLain) -> LainLain) =
        edit { p -> p.copy(listLainLain = p.listLainLain.map { if (it.id == id) transform(it) else it }) }

    fun resetItems() = edit {
        it.copy(listJasa = listOf(Jasa()), listBarang = listOf(Barang()), listTransportasi = emptyList(), listLainLain = emptyList())
    }

    /** Header fields from the edit dialog. A deliberate "save", so it skips the typing delay. */
    fun updateInfo(name: String, customer: String, pic: String, hargaKontrak: String) =
        edit(immediate = true) { it.copy(name = name, customer = customer, pic = pic, hargaKontrak = hargaKontrak) }

    fun retrySave() = scheduleSave(immediate = true)

    /**
     * Saves pending edits right away and waits for the result. Used before leaving
     * the screen. True when nothing is left unsaved.
     */
    suspend fun saveNow(): Boolean {
        debounceJob?.cancel()
        // In applicationScope: if the caller gives up waiting, the save still finishes.
        return applicationScope.async { save() }.await()
    }

    override fun onCleared() {
        // The screen is closing. Anything still unsaved goes out now rather than
        // being dropped with the pending timer. save() is a no-op if nothing changed.
        debounceJob?.cancel()
        applicationScope.launch { save() }
    }

    // --- Saving ------------------------------------------------------------

    private fun edit(immediate: Boolean = false, transform: (Project) -> Project) {
        val state = _uiState.value as? ProjectDetailUiState.Editing ?: return
        val updated = transform(state.project)
        if (updated == state.project) return
        _uiState.value = state.copy(project = updated, saveStatus = SaveStatus.Pending)
        scheduleSave(immediate)
    }

    private fun scheduleSave(immediate: Boolean) {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            if (!immediate) delay(saveDelayMillis)
            // A new edit cancels only this timer, never a save already running.
            applicationScope.launch { save() }
        }
    }

    /** Persists the newest on-screen state. True if the server now matches it. */
    private suspend fun save(): Boolean = saveMutex.withLock {
        val snapshot = (_uiState.value as? ProjectDetailUiState.Editing)?.project ?: return@withLock false
        val saved = lastSaved ?: return@withLock false
        if (snapshot == saved) {
            setStatusIfCurrent(snapshot, SaveStatus.Saved)
            return@withLock true
        }

        setStatus(SaveStatus.Saving)
        val result = persist(saved, snapshot)
        if (result.isSuccess) lastSaved = snapshot

        val latest = (_uiState.value as? ProjectDetailUiState.Editing)?.project
        setStatus(
            when {
                result.isFailure -> SaveStatus.Failed(result.exceptionOrNull()?.message ?: "Gagal menyimpan.")
                // More edits arrived while this save was in flight; their save is queued.
                latest != snapshot -> SaveStatus.Pending
                else -> SaveStatus.Saved
            }
        )
        result.isSuccess && latest == snapshot
    }

    /** Sends only what changed: header fields with PATCH, items with PUT. */
    private suspend fun persist(saved: Project, snapshot: Project): Result<Unit> {
        if (saved.headerDiffers(snapshot)) {
            repository.updateInfo(snapshot.id, snapshot.name, snapshot.customer, snapshot.pic, snapshot.hargaKontrak)
                .onFailure { return Result.failure(it) }
        }
        if (saved.itemsDiffer(snapshot)) {
            repository.saveItems(snapshot).onFailure { return Result.failure(it) }
        }
        return Result.success(Unit)
    }

    private fun setStatus(status: SaveStatus) = _uiState.update {
        (it as? ProjectDetailUiState.Editing)?.copy(saveStatus = status) ?: it
    }

    private fun setStatusIfCurrent(snapshot: Project, status: SaveStatus) = _uiState.update {
        if (it is ProjectDetailUiState.Editing && it.project == snapshot) it.copy(saveStatus = status) else it
    }

    private fun Project.headerDiffers(other: Project) =
        name != other.name || customer != other.customer || pic != other.pic || hargaKontrak != other.hargaKontrak

    private fun Project.itemsDiffer(other: Project) =
        listJasa != other.listJasa || listBarang != other.listBarang ||
            listTransportasi != other.listTransportasi || listLainLain != other.listLainLain

    companion object {
        const val DEFAULT_SAVE_DELAY_MILLIS = 800L
    }
}
