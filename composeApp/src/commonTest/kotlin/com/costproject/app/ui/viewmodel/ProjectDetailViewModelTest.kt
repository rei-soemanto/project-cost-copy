package com.costproject.app.ui.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.costproject.app.data.DataError
import com.costproject.app.domain.model.Jasa
import com.costproject.app.domain.model.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectDetailViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val project = Project(id = "p1", name = "Gedung", listJasa = listOf(Jasa("j1", harga = "")))
    private val delay = ProjectDetailViewModel.DEFAULT_SAVE_DELAY_MILLIS

    /**
     * Saves run in the application scope. Here that is the test scope itself, not
     * backgroundScope: advanceUntilIdle() stops once only background tasks remain,
     * so saves launched into backgroundScope would silently never run.
     */
    private fun TestScope.loadedViewModel(
        repo: FakeProjectRepository,
        applicationScope: CoroutineScope = this
    ): ProjectDetailViewModel =
        ProjectDetailViewModel("p1", repo, applicationScope).also { advanceUntilIdle() }

    private val ProjectDetailViewModel.editing get() = uiState.value as ProjectDetailUiState.Editing

    private fun ProjectDetailViewModel.typeHarga(value: String) = updateJasa("j1") { it.copy(harga = value) }

    @Test
    fun loads_the_project_and_starts_saved() = runTest {
        val vm = loadedViewModel(FakeProjectRepository(listOf(project)))
        assertEquals("Gedung", vm.editing.project.name)
        assertEquals(SaveStatus.Saved, vm.editing.saveStatus)
    }

    @Test
    fun a_load_failure_shows_an_error_and_retry_recovers() = runTest {
        val repo = FakeProjectRepository(listOf(project)).apply { failure = DataError.Network() }
        val vm = loadedViewModel(repo)
        assertIs<ProjectDetailUiState.Error>(vm.uiState.value)

        repo.failure = null
        vm.load()
        advanceUntilIdle()
        assertIs<ProjectDetailUiState.Editing>(vm.uiState.value)
    }

    @Test
    fun an_edit_shows_on_screen_immediately_and_waits_before_saving() = runTest {
        val repo = FakeProjectRepository(listOf(project))
        val vm = loadedViewModel(repo)

        vm.typeHarga("5")
        runCurrent()

        assertEquals("5", vm.editing.project.listJasa.single().harga)
        assertEquals(SaveStatus.Pending, vm.editing.saveStatus)
        assertTrue(repo.saveItemsCalls.isEmpty(), "saved before typing paused")
    }

    @Test
    fun rapid_typing_produces_one_save_carrying_the_final_value() = runTest {
        val repo = FakeProjectRepository(listOf(project))
        val vm = loadedViewModel(repo)

        listOf("5", "50", "500", "5.000").forEach {
            vm.typeHarga(it)
            advanceTimeBy(delay / 2) // keeps typing within the pause window
        }
        advanceUntilIdle()

        assertEquals(1, repo.saveItemsCalls.size)
        assertEquals("5.000", repo.saveItemsCalls.single().listJasa.single().harga)
        assertEquals(SaveStatus.Saved, vm.editing.saveStatus)
    }

    @Test
    fun an_edit_that_changes_nothing_does_not_save() = runTest {
        val repo = FakeProjectRepository(listOf(project))
        val vm = loadedViewModel(repo)

        vm.typeHarga("") // already ""
        advanceUntilIdle()
        assertTrue(repo.saveItemsCalls.isEmpty())
        assertEquals(SaveStatus.Saved, vm.editing.saveStatus)

        // Control: a real change does save, so the assertion above is meaningful.
        vm.typeHarga("1")
        advanceUntilIdle()
        assertEquals(1, repo.saveItemsCalls.size)
    }

    @Test
    fun edits_made_during_a_save_are_sent_after_it_never_alongside_it() = runTest {
        val repo = FakeProjectRepository(listOf(project)).apply { writeDelayMillis = 2_000 }
        val vm = loadedViewModel(repo)

        vm.typeHarga("1")
        advanceTimeBy(delay + 100) // first save now in flight, 2s long
        vm.typeHarga("12")
        advanceTimeBy(delay + 100) // second save's timer fires while the first is still running
        advanceUntilIdle()

        // Overlapping requests could land out of order and leave "1" on the server.
        assertEquals(1, repo.maxConcurrentWrites, "saves overlapped")
        assertEquals(listOf("1", "12"), repo.saveItemsCalls.map { it.listJasa.single().harga })
        assertEquals("12", repo.stored.getValue("p1").listJasa.single().harga)
        assertEquals(SaveStatus.Saved, vm.editing.saveStatus)
    }

    @Test
    fun a_failed_save_keeps_the_edits_and_retry_saves_them() = runTest {
        val repo = FakeProjectRepository(listOf(project))
        val vm = loadedViewModel(repo)
        repo.failure = DataError.Network() // the connection drops after loading

        vm.typeHarga("5.000")
        advanceUntilIdle()

        assertIs<SaveStatus.Failed>(vm.editing.saveStatus)
        // The user's typing is not rolled back.
        assertEquals("5.000", vm.editing.project.listJasa.single().harga)

        repo.failure = null
        vm.retrySave()
        advanceUntilIdle()

        assertEquals(SaveStatus.Saved, vm.editing.saveStatus)
        assertEquals("5.000", repo.stored.getValue("p1").listJasa.single().harga)
    }

    @Test
    fun editing_project_info_saves_at_once_and_only_the_header() = runTest {
        val repo = FakeProjectRepository(listOf(project))
        val vm = loadedViewModel(repo)

        vm.updateInfo("Gedung B", "PT X", "Budi", "50.000.000")
        runCurrent() // no typing delay for a dialog save

        assertEquals("Gedung B", repo.stored.getValue("p1").name)
        assertEquals(1, repo.updateInfoCalls.size)
        assertTrue(repo.saveItemsCalls.isEmpty(), "items unchanged, so they should not be re-sent")
    }

    @Test
    fun save_now_flushes_pending_edits_without_waiting_for_the_pause() = runTest {
        val repo = FakeProjectRepository(listOf(project))
        val vm = loadedViewModel(repo)

        vm.typeHarga("9")
        val saved = async { vm.saveNow() }
        runCurrent()

        assertTrue(saved.await())
        assertEquals("9", repo.stored.getValue("p1").listJasa.single().harga)
    }

    @Test
    fun save_now_reports_failure_so_the_screen_can_warn_before_leaving() = runTest {
        val repo = FakeProjectRepository(listOf(project))
        val vm = loadedViewModel(repo)
        repo.failure = DataError.Network()

        vm.typeHarga("9")
        val saved = async { vm.saveNow() }
        advanceUntilIdle()

        assertFalse(saved.await())
    }

    @Test
    fun closing_the_screen_still_saves_edits_made_moments_before() = runTest {
        val repo = FakeProjectRepository(listOf(project))
        val store = ViewModelStore()
        val vm = ViewModelProvider.create(
            store,
            viewModelFactory { initializer { ProjectDetailViewModel("p1", repo, this@runTest) } }
        )[ProjectDetailViewModel::class]
        advanceUntilIdle()

        vm.typeHarga("7")
        runCurrent()
        store.clear() // back pressed before the typing pause elapsed
        advanceUntilIdle()

        assertEquals("7", repo.stored.getValue("p1").listJasa.single().harga)
    }

    @Test
    fun transportasi_stops_at_the_limit() = runTest {
        val vm = loadedViewModel(FakeProjectRepository(listOf(project)))
        repeat(Project.MAX_TRANSPORTASI + 5) { vm.addTransportasi() }
        assertEquals(Project.MAX_TRANSPORTASI, vm.editing.project.listTransportasi.size)
    }

    @Test
    fun reset_clears_items_back_to_one_empty_jasa_and_barang() = runTest {
        val busy = project.copy(
            listJasa = listOf(Jasa("j1", harga = "1"), Jasa("j2", harga = "2")),
            listTransportasi = listOf(com.costproject.app.domain.model.Transportasi("t1", biaya = "3"))
        )
        val repo = FakeProjectRepository(listOf(busy))
        val vm = loadedViewModel(repo)

        vm.resetItems()
        advanceUntilIdle()

        val saved = repo.stored.getValue("p1")
        assertEquals(1, saved.listJasa.size)
        assertEquals("", saved.listJasa.single().harga)
        assertEquals(1, saved.listBarang.size)
        assertTrue(saved.listTransportasi.isEmpty())
        assertEquals(0L, saved.grandTotal)
    }
}
