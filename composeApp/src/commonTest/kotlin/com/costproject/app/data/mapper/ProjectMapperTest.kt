package com.costproject.app.data.mapper

import com.costproject.app.data.dto.CostItemDto
import com.costproject.app.data.dto.CostItemKindDto
import com.costproject.app.data.dto.ProjectDto
import com.costproject.app.domain.model.Barang
import com.costproject.app.domain.model.Jasa
import com.costproject.app.domain.model.LainLain
import com.costproject.app.domain.model.Project
import com.costproject.app.domain.model.Transportasi
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectMapperTest {

    private val project = Project(
        id = "p1",
        name = "Project IT",
        customer = "PT Maju",
        pic = "Budi",
        hargaKontrak = "50.000.000",
        listJasa = listOf(Jasa("j1", "Rancang", "Andi", "5.000.000"), Jasa("j2", "Instal", "", "")),
        listBarang = listOf(Barang("b1", "Kabel", "10", "25.000")),
        listTransportasi = listOf(Transportasi("t1", "Sewa mobil", "300000")),
        listLainLain = listOf(LainLain("l1", "Konsumsi", "150.000"))
    )

    @Test
    fun create_request_sends_whole_rupiah_integers_parsed_from_typed_text() {
        val request = ProjectMapper.toCreateRequest(project)
        assertEquals(50_000_000L, request.hargaKontrak)
        assertEquals(5_000_000L, request.items.first { it.id == "j1" }.amount)
        // Typed without separators - still parsed.
        assertEquals(300_000L, request.items.first { it.id == "t1" }.amount)
    }

    @Test
    fun items_are_sent_grouped_by_kind_in_a_fixed_order() {
        val request = ProjectMapper.toCreateRequest(project)
        assertEquals(listOf("j1", "j2", "b1", "t1", "l1"), request.items.map { it.id })
        assertEquals(
            listOf(
                CostItemKindDto.JASA, CostItemKindDto.JASA, CostItemKindDto.BARANG,
                CostItemKindDto.TRANSPORTASI, CostItemKindDto.LAIN_LAIN
            ),
            request.items.map { it.kind }
        )
    }

    @Test
    fun barang_carries_quantity_and_unit_price_separately() {
        val barang = ProjectMapper.toCreateRequest(project).items.first { it.kind == CostItemKindDto.BARANG }
        assertEquals(10L, barang.quantity)
        assertEquals(25_000L, barang.amount)
    }

    @Test
    fun round_trip_preserves_every_value() {
        val wire = ProjectMapper.toCreateRequest(project)
        val back = ProjectMapper.toDomain(
            ProjectDto(
                wire.id, wire.name, wire.customer, wire.pic, wire.hargaKontrak, wire.items,
                createdAt = "2026-09-22T05:00:00Z", updatedAt = "2026-09-22T05:00:00Z"
            ),
            TimeZone.UTC
        )

        assertEquals(project.grandTotal, back.grandTotal)
        assertEquals(project.nilaiKontrak, back.nilaiKontrak)
        assertEquals(project.listJasa.map { it.id }, back.listJasa.map { it.id })
        assertEquals("Andi", back.listJasa[0].engineer)
        // Amounts come back in the display format, whatever way they were typed.
        assertEquals("300.000", back.listTransportasi[0].biaya)
    }

    @Test
    fun a_zero_amount_comes_back_as_an_empty_field_not_the_text_zero() {
        val dto = ProjectDto(
            "p", "p", "", "", hargaKontrak = 0,
            items = listOf(CostItemDto("j", CostItemKindDto.JASA, "", engineer = "", amount = 0)),
            createdAt = "2026-09-22T05:00:00Z", updatedAt = "2026-09-22T05:00:00Z"
        )
        val project = ProjectMapper.toDomain(dto, TimeZone.UTC)
        assertEquals("", project.hargaKontrak)
        assertEquals("", project.listJasa[0].harga)
        // An empty contract still reads as "no contract".
        assertEquals(false, project.hasKontrak)
    }

    @Test
    fun created_date_uses_the_local_day_not_the_utc_day() {
        // 19:00 UTC on the 22nd is 02:00 on the 23rd in Jakarta (UTC+7).
        val dto = ProjectDto("p", "p", "", "", 0, emptyList(), "2026-09-22T19:00:00Z", "2026-09-22T19:00:00Z")
        assertEquals("23 Sep 2026", ProjectMapper.toDomain(dto, TimeZone.of("Asia/Jakarta")).createdAt)
        assertEquals("22 Sep 2026", ProjectMapper.toDomain(dto, TimeZone.UTC).createdAt)
    }

    @Test
    fun update_request_parses_the_contract_amount() {
        val request = ProjectMapper.toUpdateRequest("n", "c", "p", "60.000.000")
        assertEquals(60_000_000L, request.hargaKontrak)
    }
}
