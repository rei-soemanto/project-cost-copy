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
import com.costproject.app.ui.util.formatRupiah
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
    var activeProjectId by mutableStateOf<Long?>(null)

    init {
        loadFromDisk()
    }

    val activeProject: Project?
        get() = projects.find { it.id == activeProjectId }

    fun createProject(name: String, customer: String, pic: String, hargaKontrak: String) {
        val project = Project(
            name = name,
            customer = customer,
            pic = pic,
            hargaKontrak = hargaKontrak
        )
        projects = projects + project
        activeProjectId = project.id
        saveToDisk()
    }

    fun openProject(id: Long) {
        activeProjectId = id
    }

    fun closeProject() {
        activeProjectId = null
    }

    fun deleteProject(id: Long) {
        projects = projects.filterNot { it.id == id }
        if (activeProjectId == id) activeProjectId = null
        saveToDisk()
    }

    fun updateProjectInfo(field: String, value: String) {
        updateActive { project ->
            project.copy(
                name = if (field == "name") value else project.name,
                customer = if (field == "customer") value else project.customer,
                pic = if (field == "pic") value else project.pic,
                hargaKontrak = if (field == "kontrak") value else project.hargaKontrak
            )
        }
        saveToDisk()
    }

    fun addJasa() {
        updateActive { it.copy(listJasa = it.listJasa + Jasa()) }
        saveToDisk()
    }

    fun removeJasa(id: Long) {
        updateActive { it.copy(listJasa = it.listJasa.filterNot { j -> j.id == id }) }
        saveToDisk()
    }

    fun updateJasa(id: Long, field: String, value: String) {
        updateActive { p ->
            p.copy(listJasa = p.listJasa.map { item ->
                if (item.id == id) {
                    when (field) {
                        "scope" -> item.copy(scope = value)
                        "engineer" -> item.copy(engineer = value)
                        else -> item.copy(harga = value)
                    }
                } else item
            })
        }
        saveToDisk()
    }

    fun addBarang() {
        updateActive { it.copy(listBarang = it.listBarang + Barang()) }
        saveToDisk()
    }

    fun removeBarang(id: Long) {
        updateActive { it.copy(listBarang = it.listBarang.filterNot { b -> b.id == id }) }
        saveToDisk()
    }

    fun updateBarang(id: Long, field: String, value: String) {
        updateActive { p ->
            p.copy(listBarang = p.listBarang.map { item ->
                if (item.id == id) {
                    when (field) {
                        "nama" -> item.copy(nama = value)
                        "quantity" -> item.copy(quantity = value)
                        else -> item.copy(hargaSatuan = value)
                    }
                } else item
            })
        }
        saveToDisk()
    }

    fun addTransportasi() {
        updateActive { p ->
            if (p.listTransportasi.size < MAX_TRANSPORTASI) {
                p.copy(listTransportasi = p.listTransportasi + Transportasi())
            } else p
        }
        saveToDisk()
    }

    fun removeTransportasi(id: Long) {
        updateActive { it.copy(listTransportasi = it.listTransportasi.filterNot { t -> t.id == id }) }
        saveToDisk()
    }

    fun updateTransportasi(id: Long, field: String, value: String) {
        updateActive { p ->
            p.copy(listTransportasi = p.listTransportasi.map { item ->
                if (item.id == id) {
                    when (field) {
                        "keterangan" -> item.copy(keterangan = value)
                        else -> item.copy(biaya = value)
                    }
                } else item
            })
        }
        saveToDisk()
    }

    fun addLainLain() {
        updateActive { it.copy(listLainLain = it.listLainLain + LainLain()) }
        saveToDisk()
    }

    fun removeLainLain(id: Long) {
        updateActive { it.copy(listLainLain = it.listLainLain.filterNot { l -> l.id == id }) }
        saveToDisk()
    }

    fun updateLainLain(id: Long, field: String, value: String) {
        updateActive { p ->
            p.copy(listLainLain = p.listLainLain.map { item ->
                if (item.id == id) {
                    when (field) {
                        "keterangan" -> item.copy(keterangan = value)
                        else -> item.copy(biaya = value)
                    }
                } else item
            })
        }
        saveToDisk()
    }

    fun resetCurrent() {
        updateActive {
            it.copy(
                listJasa = listOf(Jasa()),
                listBarang = listOf(Barang()),
                listTransportasi = listOf(),
                listLainLain = listOf()
            )
        }
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
            // Data korup/versi lama: abaikan dan mulai dari awal
        }
    }

    private fun saveToDisk() {
        try {
            settings.putString(KEY_PROJECTS, json.encodeToString(projects))
        } catch (_: Exception) {
            // Abaikan jika gagal menyimpan untuk sementara
        }
    }

    companion object {
        private const val KEY_PROJECTS = "projects"

        /** Maximum transportasi rows allowed on a project. */
        const val MAX_TRANSPORTASI = 20

        /**
         * Kept as a companion function so existing call sites keep working.
         * The implementation now lives in [com.costproject.app.ui.util.formatRupiah].
         */
        fun formatRupiah(value: Double): String = value.formatRupiah()
    }
}
