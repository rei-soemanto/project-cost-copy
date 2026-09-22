package com.costproject.app.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.costproject.app.domain.model.Barang
import com.costproject.app.domain.model.Jasa
import com.costproject.app.domain.model.LainLain
import com.costproject.app.domain.model.Project
import com.costproject.app.domain.model.Transportasi
import com.russhwolf.settings.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ProjectViewModel : ViewModel() {

    private val settings: Settings by lazy { Settings() }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    var projects by mutableStateOf(listOf<Project>())
        private set

    var activeProjectId by mutableStateOf<String?>(null)
        private set

    init {
        loadFromDisk()
    }

    val activeProject: Project?
        get() = projects.find { it.id == activeProjectId }

    // --- Project lifecycle -------------------------------------------------

    /** Creates a project and returns its id so the caller can navigate to it. */
    fun createProject(name: String, customer: String, pic: String, hargaKontrak: String): String {
        val project = Project(
            name = name,
            customer = customer,
            pic = pic,
            hargaKontrak = hargaKontrak
        )
        projects = projects + project
        activeProjectId = project.id
        saveToDisk()
        return project.id
    }

    fun openProject(id: String) {
        activeProjectId = id
    }

    fun closeProject() {
        activeProjectId = null
    }

    fun deleteProject(id: String) {
        projects = projects.filterNot { it.id == id }
        if (activeProjectId == id) activeProjectId = null
        saveToDisk()
    }

    /**
     * Updates all four header fields in one pass. Previously the edit dialog called a
     * stringly-typed setter four times, producing four state updates and four full
     * disk writes for a single save.
     */
    fun updateProjectInfo(name: String, customer: String, pic: String, hargaKontrak: String) {
        updateActive {
            it.copy(name = name, customer = customer, pic = pic, hargaKontrak = hargaKontrak)
        }
        saveToDisk()
    }

    // --- Cost items --------------------------------------------------------
    //
    // Updates take a copy-transform rather than a field-name String. The previous
    // `when (field) { ... else -> item.copy(harga = value) }` shape meant any
    // mistyped field name silently wrote to the wrong column; this cannot compile
    // if the field is wrong.

    fun addJasa() = mutateAndSave { it.copy(listJasa = it.listJasa + Jasa()) }

    fun removeJasa(id: String) = mutateAndSave { p ->
        p.copy(listJasa = p.listJasa.filterNot { it.id == id })
    }

    fun updateJasa(id: String, transform: (Jasa) -> Jasa) = mutateAndSave { p ->
        p.copy(listJasa = p.listJasa.map { if (it.id == id) transform(it) else it })
    }

    fun addBarang() = mutateAndSave { it.copy(listBarang = it.listBarang + Barang()) }

    fun removeBarang(id: String) = mutateAndSave { p ->
        p.copy(listBarang = p.listBarang.filterNot { it.id == id })
    }

    fun updateBarang(id: String, transform: (Barang) -> Barang) = mutateAndSave { p ->
        p.copy(listBarang = p.listBarang.map { if (it.id == id) transform(it) else it })
    }

    fun addTransportasi() = mutateAndSave { p ->
        if (p.listTransportasi.size < MAX_TRANSPORTASI) {
            p.copy(listTransportasi = p.listTransportasi + Transportasi())
        } else {
            p
        }
    }

    fun removeTransportasi(id: String) = mutateAndSave { p ->
        p.copy(listTransportasi = p.listTransportasi.filterNot { it.id == id })
    }

    fun updateTransportasi(id: String, transform: (Transportasi) -> Transportasi) = mutateAndSave { p ->
        p.copy(listTransportasi = p.listTransportasi.map { if (it.id == id) transform(it) else it })
    }

    fun addLainLain() = mutateAndSave { it.copy(listLainLain = it.listLainLain + LainLain()) }

    fun removeLainLain(id: String) = mutateAndSave { p ->
        p.copy(listLainLain = p.listLainLain.filterNot { it.id == id })
    }

    fun updateLainLain(id: String, transform: (LainLain) -> LainLain) = mutateAndSave { p ->
        p.copy(listLainLain = p.listLainLain.map { if (it.id == id) transform(it) else it })
    }

    fun resetCurrent() = mutateAndSave {
        it.copy(
            listJasa = listOf(Jasa()),
            listBarang = listOf(Barang()),
            listTransportasi = emptyList(),
            listLainLain = emptyList()
        )
    }

    // --- Internals ---------------------------------------------------------

    private fun mutateAndSave(transform: (Project) -> Project) {
        updateActive(transform)
        saveToDisk()
    }

    private fun updateActive(transform: (Project) -> Project) {
        val id = activeProjectId ?: return
        projects = projects.map { if (it.id == id) transform(it) else it }
    }

    private fun loadFromDisk() {
        val raw = settings.getStringOrNull(KEY_PROJECTS) ?: return
        try {
            projects = json.decodeFromString<List<Project>>(raw)
        } catch (_: Exception) {
            // Data korup atau versi lama: mulai dari awal.
            // Phase 6 replaces this store, and the repository will surface load
            // failures instead of discarding them silently.
        }
    }

    private fun saveToDisk() {
        try {
            settings.putString(KEY_PROJECTS, json.encodeToString(projects))
        } catch (_: Exception) {
            // Abaikan jika gagal menyimpan untuk sementara.
        }
    }

    companion object {
        private const val KEY_PROJECTS = "projects"

        /** Maximum transportasi rows allowed on a project. */
        const val MAX_TRANSPORTASI = 20
    }
}
