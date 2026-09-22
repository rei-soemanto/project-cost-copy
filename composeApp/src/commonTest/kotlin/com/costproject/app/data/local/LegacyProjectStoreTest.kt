package com.costproject.app.data.local

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LegacyProjectStoreTest {

    /**
     * Exactly the shape the original, pre-restructure app wrote: ids are JSON
     * numbers. A strict String id field fails on this, and the old loader
     * swallowed the failure and then overwrote the data on the next save.
     */
    private val originalAppJson = """
        [{"id":8234982349823498234,"name":"Gedung A","customer":"PT X","pic":"Budi",
          "hargaKontrak":"50000000",
          "listJasa":[{"id":111,"scope":"Instalasi","engineer":"Andi","harga":"5000000"}],
          "listBarang":[{"id":222,"nama":"Kabel","quantity":"10","hargaSatuan":"25000"}],
          "listTransportasi":[{"id":333,"keterangan":"Sewa mobil","biaya":"300000"}],
          "listLainLain":[],
          "createdAt":"22 Sep 2026"}]
    """.trimIndent()

    @Test
    fun decodes_the_original_apps_numeric_ids() {
        val projects = decodeLegacyProjects(originalAppJson)!!
        assertEquals(1, projects.size)

        val p = projects.single()
        assertEquals("8234982349823498234", p.id)
        assertEquals("111", p.listJasa.single().id)
        assertEquals("222", p.listBarang.single().id)
        assertEquals("333", p.listTransportasi.single().id)
    }

    @Test
    fun keeps_every_value_from_the_original_app() {
        val p = decodeLegacyProjects(originalAppJson)!!.single()
        assertEquals("Gedung A", p.name)
        assertEquals(50_000_000L, p.nilaiKontrak)
        assertEquals(5_000_000L + 250_000L + 300_000L, p.grandTotal)
        assertEquals("22 Sep 2026", p.createdAt)
    }

    @Test
    fun decodes_string_ids_written_by_newer_builds() {
        val json = """[{"id":"abc123","name":"p","listJasa":[{"id":"j1","harga":"1"}]}]"""
        assertEquals("abc123", decodeLegacyProjects(json)!!.single().id)
    }

    @Test
    fun unreadable_data_is_null_rather_than_an_empty_list() {
        // Null tells the caller "data exists but could not be read" - so it
        // must not be overwritten - as opposed to "nothing saved".
        assertNull(decodeLegacyProjects("{ this is not json"))
        assertNull(decodeLegacyProjects("""{"not":"a list"}"""))
    }

    @Test
    fun replace_keeps_only_the_remaining_projects_and_removes_the_key_when_empty() {
        val settings = MapSettings(LegacyProjectStore.KEY_PROJECTS to originalAppJson)
        val store = LegacyProjectStore(settings)

        val loaded = store.load()
        store.replace(loaded)
        assertEquals(loaded.map { it.id }, store.load().map { it.id })

        store.replace(emptyList())
        assertTrue(LegacyProjectStore.KEY_PROJECTS !in settings.keys)
    }
}
