package com.costproject.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.costproject.app.data.repository.AuthRepository
import com.costproject.app.data.repository.ProjectRepository
import com.costproject.app.domain.model.Project
import com.costproject.app.domain.model.User
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ProjectListUiState {
    data object Loading : ProjectListUiState
    data class Success(val projects: List<Project>, val isRefreshing: Boolean = false) : ProjectListUiState
    data class Error(val message: String) : ProjectListUiState
}

/** Things that happen once, which state cannot express: navigate, show a message. */
sealed interface ProjectListEvent {
    data class OpenProject(val id: String) : ProjectListEvent
    data class ShowMessage(val message: String) : ProjectListEvent
}

class ProjectListViewModel(
    private val projectRepository: ProjectRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProjectListUiState>(ProjectListUiState.Loading)
    val uiState: StateFlow<ProjectListUiState> = _uiState.asStateFlow()

    /**
     * A Channel, not a SharedFlow: each event is consumed exactly once. A replaying
     * flow would re-open the project or re-show the message after a rotation.
     */
    private val _events = Channel<ProjectListEvent>(Channel.BUFFERED)
    val events: Flow<ProjectListEvent> = _events.receiveAsFlow()

    val currentUser: User? get() = authRepository.currentUser

    private var isCreating = false

    /** Whether the list is on screen. Set by the view as it enters and leaves composition. */
    private var isVisible = false
    /** A project changed while the list was hidden; refresh when it is shown again. */
    private var isStale = false

    init {
        load()
        viewModelScope.launch {
            // Refreshing only while visible avoids reloading the whole list on every
            // autosave while the user is editing a project. A save that lands after
            // the user has already come back still triggers a refresh here.
            projectRepository.changes.collect {
                if (isVisible) refresh() else isStale = true
            }
        }
    }

    fun onVisible() {
        isVisible = true
        if (isStale) {
            isStale = false
            refresh()
        }
    }

    fun onHidden() {
        isVisible = false
    }

    /** Full load with a loading screen. Used initially and by the error screen's retry. */
    fun load() {
        _uiState.value = ProjectListUiState.Loading
        viewModelScope.launch {
            _uiState.value = projectRepository.list().fold(
                onSuccess = { ProjectListUiState.Success(it) },
                onFailure = { ProjectListUiState.Error(it.message ?: "Gagal memuat project.") }
            )
        }
    }

    /** Reloads while keeping the current list on screen. */
    fun refresh() {
        val current = _uiState.value as? ProjectListUiState.Success ?: return
        _uiState.value = current.copy(isRefreshing = true)
        viewModelScope.launch {
            projectRepository.list()
                .onSuccess { _uiState.value = ProjectListUiState.Success(it) }
                .onFailure { error ->
                    // Keep showing what we have; a failed background refresh is not
                    // worth replacing the list with an error screen.
                    _uiState.update { (it as? ProjectListUiState.Success)?.copy(isRefreshing = false) ?: it }
                    _events.send(ProjectListEvent.ShowMessage(error.message ?: "Gagal memperbarui."))
                }
        }
    }

    fun createProject(name: String, customer: String, pic: String, hargaKontrak: String) {
        if (isCreating) return
        isCreating = true
        viewModelScope.launch {
            projectRepository.create(Project(name = name, customer = customer, pic = pic, hargaKontrak = hargaKontrak))
                .onSuccess { created ->
                    _uiState.update { state ->
                        if (state is ProjectListUiState.Success) state.copy(projects = listOf(created) + state.projects)
                        else ProjectListUiState.Success(listOf(created))
                    }
                    _events.send(ProjectListEvent.OpenProject(created.id))
                }
                .onFailure { _events.send(ProjectListEvent.ShowMessage(it.message ?: "Gagal membuat project.")) }
            isCreating = false
        }
    }

    /** Removes the project immediately and puts it back if the server refuses. */
    fun deleteProject(id: String) {
        val before = (_uiState.value as? ProjectListUiState.Success)?.projects ?: return
        val index = before.indexOfFirst { it.id == id }
        if (index < 0) return
        val removed = before[index]

        _uiState.update { (it as? ProjectListUiState.Success)?.copy(projects = before - removed) ?: it }
        viewModelScope.launch {
            projectRepository.delete(id).onFailure { error ->
                _uiState.update { state ->
                    // Restore at its old position, unless a reload already brought it back.
                    if (state !is ProjectListUiState.Success || state.projects.any { it.id == id }) return@update state
                    state.copy(projects = state.projects.toMutableList().apply { add(index.coerceAtMost(size), removed) })
                }
                _events.send(ProjectListEvent.ShowMessage(error.message ?: "Gagal menghapus project."))
            }
        }
    }

    fun logout() = authRepository.logout()
}
