package com.costproject.app.ui.viewmodel

import com.costproject.app.data.DataError
import com.costproject.app.domain.model.Project
import com.costproject.app.ui.util.SaveResult
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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
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

    private fun TestScope.viewModel(
        repo: FakeProjectRepository,
        export: FakeExportRepository = FakeExportRepository()
    ) = ProjectListViewModel(repo, FakeAuthRepository(), export).also { advanceUntilIdle() }

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

    // --- Excel export ---------------------------------------------------------

    @Test
    fun admin_status_is_loaded_and_a_failed_check_hides_the_admin_option() = runTest {
        val admin = viewModel(FakeProjectRepository(), FakeExportRepository(admin = Result.success(true)))
        assertTrue(admin.isAdmin.value)

        val unknown = viewModel(FakeProjectRepository(), FakeExportRepository(admin = Result.failure(DataError.Network())))
        assertFalse(unknown.isAdmin.value)
    }

    @Test
    fun export_downloads_then_asks_the_screen_to_save_holding_the_bytes_until_done() = runTest {
        val export = FakeExportRepository()
        val vm = viewModel(FakeProjectRepository(), export)
        val events = collectEvents(vm)

        vm.export(all = false)
        advanceUntilIdle()

        assertEquals(1, export.mineCalls)
        assertEquals(0, export.allCalls)
        val save = events.filterIsInstance<ProjectListEvent.SaveFile>().single()
        assertEquals("CostProject-Backup-2026-09-23.xlsx", save.fileName)
        // Held in the ViewModel, so the picker can read it even after a rotation.
        assertContentEquals(byteArrayOf(0x50, 0x4B), vm.pendingExportBytes)
        assertFalse(vm.isExporting.value)

        vm.onExportSaved(SaveResult.Saved)
        advanceUntilIdle()
        assertNull(vm.pendingExportBytes)
        assertEquals("Backup Excel tersimpan.", events.filterIsInstance<ProjectListEvent.ShowMessage>().last().message)
    }

    @Test
    fun the_admin_export_calls_the_all_users_endpoint() = runTest {
        val export = FakeExportRepository(admin = Result.success(true))
        val vm = viewModel(FakeProjectRepository(), export)
        vm.export(all = true)
        advanceUntilIdle()
        assertEquals(1, export.allCalls)
        assertEquals(0, export.mineCalls)
    }

    @Test
    fun a_cancelled_save_releases_the_file_without_a_message() = runTest {
        val vm = viewModel(FakeProjectRepository())
        val events = collectEvents(vm)
        vm.export(all = false)
        advanceUntilIdle()

        vm.onExportSaved(SaveResult.Cancelled)
        advanceUntilIdle()

        assertNull(vm.pendingExportBytes)
        assertTrue(events.none { it is ProjectListEvent.ShowMessage })
    }

    @Test
    fun a_failed_download_shows_the_error_and_offers_no_file() = runTest {
        val export = FakeExportRepository(file = Result.failure(DataError.Forbidden()))
        val vm = viewModel(FakeProjectRepository(), export)
        val events = collectEvents(vm)

        vm.export(all = true)
        advanceUntilIdle()

        assertTrue(events.none { it is ProjectListEvent.SaveFile })
        val message = events.filterIsInstance<ProjectListEvent.ShowMessage>().single().message
        assertEquals(DataError.Forbidden().message, message)
        assertFalse(vm.isExporting.value)
    }

    @Test
    fun a_second_tap_while_downloading_is_ignored() = runTest {
        val export = FakeExportRepository().apply { delayMillis = 1_000 }
        val vm = viewModel(FakeProjectRepository(), export)

        vm.export(all = false)
        assertTrue(vm.isExporting.value)
        vm.export(all = false)
        advanceUntilIdle()

        assertEquals(1, export.mineCalls)
    }
}
