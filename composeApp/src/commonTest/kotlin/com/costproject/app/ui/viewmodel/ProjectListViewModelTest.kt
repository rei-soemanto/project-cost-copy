package com.costproject.app.ui.viewmodel

import com.costproject.app.data.DataError
import com.costproject.app.domain.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectListViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val a = Project(id = "a", name = "A")
    private val b = Project(id = "b", name = "B")
    private val c = Project(id = "c", name = "C")

    private fun TestScope.viewModel(repo: FakeProjectRepository) =
        ProjectListViewModel(repo, FakeAuthRepository()).also { advanceUntilIdle() }

    private fun TestScope.collectEvents(vm: ProjectListViewModel): List<ProjectListEvent> {
        val events = mutableListOf<ProjectListEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(events) }
        return events
    }

    private val ProjectListViewModel.projects get() = (uiState.value as ProjectListUiState.Success).projects

    @Test
    fun loads_the_projects() = runTest {
        val vm = viewModel(FakeProjectRepository(listOf(a, b)))
        assertEquals(listOf("a", "b"), vm.projects.map { it.id })
    }

    @Test
    fun a_load_failure_shows_an_error_and_retry_recovers() = runTest {
        val repo = FakeProjectRepository(listOf(a)).apply { failure = DataError.Network() }
        val vm = viewModel(repo)
        assertIs<ProjectListUiState.Error>(vm.uiState.value)

        repo.failure = null
        vm.load()
        advanceUntilIdle()
        assertEquals(listOf("a"), vm.projects.map { it.id })
    }

    @Test
    fun a_new_project_goes_to_the_top_and_is_opened() = runTest {
        val vm = viewModel(FakeProjectRepository(listOf(a)))
        val events = collectEvents(vm)

        vm.createProject("Baru", "PT X", "Budi", "1.000")
        advanceUntilIdle()

        assertEquals("Baru", vm.projects.first().name)
        val opened = events.filterIsInstance<ProjectListEvent.OpenProject>().single()
        assertEquals(vm.projects.first().id, opened.id)
    }

    @Test
    fun delete_removes_at_once_and_restores_in_place_if_the_server_refuses() = runTest {
        val repo = FakeProjectRepository(listOf(a, b, c))
        val vm = viewModel(repo)
        val events = collectEvents(vm)

        repo.failure = DataError.Network()
        vm.deleteProject("b")
        assertEquals(listOf("a", "c"), vm.projects.map { it.id }, "not removed optimistically")

        advanceUntilIdle()
        assertEquals(listOf("a", "b", "c"), vm.projects.map { it.id }, "not restored to its old position")
        assertTrue(events.any { it is ProjectListEvent.ShowMessage })
    }

    @Test
    fun changes_while_hidden_refresh_once_the_list_is_shown_again() = runTest {
        val repo = FakeProjectRepository(listOf(a))
        val vm = viewModel(repo)
        vm.onVisible()
        vm.onHidden() // user opened a project
        val callsBefore = repo.listCalls

        repeat(5) { repo.signalChange() } // five autosaves while editing
        advanceUntilIdle()
        assertEquals(callsBefore, repo.listCalls, "reloaded the list while it was not on screen")

        vm.onVisible() // user came back
        advanceUntilIdle()
        assertEquals(callsBefore + 1, repo.listCalls)
    }

    @Test
    fun a_change_while_visible_refreshes_right_away() = runTest {
        // Covers a save that lands after the user has already returned to the list.
        val repo = FakeProjectRepository(listOf(a))
        val vm = viewModel(repo)
        vm.onVisible()
        val callsBefore = repo.listCalls

        repo.stored["a"] = a.copy(name = "A (edited)")
        repo.signalChange()
        advanceUntilIdle()

        assertEquals(callsBefore + 1, repo.listCalls)
        assertEquals("A (edited)", vm.projects.single().name)
    }
}
