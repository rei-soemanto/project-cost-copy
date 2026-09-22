package com.costproject.app.data.mapper

import com.costproject.app.data.dto.CostItemDto
import com.costproject.app.data.dto.CostItemKindDto
import com.costproject.app.data.dto.CreateProjectRequest
import com.costproject.app.data.dto.ProjectDto
import com.costproject.app.data.dto.UpdateProjectRequest
import com.costproject.app.domain.model.Barang
import com.costproject.app.domain.model.Jasa
import com.costproject.app.domain.model.LainLain
import com.costproject.app.domain.model.Project
import com.costproject.app.domain.model.Transportasi
import com.costproject.app.ui.util.formatDateLabel
import com.costproject.app.ui.util.parseQuantity
import com.costproject.app.ui.util.parseRupiah
import com.costproject.app.ui.util.toAmountInput
import com.costproject.app.ui.util.toQuantityInput
import kotlinx.datetime.TimeZone

/**
 * Converts between the domain model and the wire DTOs.
 *
 * The domain keeps amounts as the text the user typed; the wire carries whole
 * rupiah as integers. This is the only place that crosses between the two, so
 * the "5.000.000" -> 5000000 conversion cannot drift between call sites.
 *
 * The domain holds four separate item lists; the wire holds one ordered list
 * tagged by kind. Items are sent grouped by kind in a fixed order, so the order
 * within each kind survives a round trip.
 */
object ProjectMapper {

    fun toDomain(dto: ProjectDto, timeZone: TimeZone = TimeZone.currentSystemDefault()): Project {
        val byKind = dto.items.groupBy { it.kind }
        fun itemsOf(kind: CostItemKindDto) = byKind[kind].orEmpty()

        return Project(
            id = dto.id,
            name = dto.name,
            customer = dto.customer,
            pic = dto.pic,
            hargaKontrak = dto.hargaKontrak.toAmountInput(),
            listJasa = itemsOf(CostItemKindDto.JASA).map {
                Jasa(id = it.id, scope = it.description, engineer = it.engineer.orEmpty(), harga = it.amount.toAmountInput())
            },
            listBarang = itemsOf(CostItemKindDto.BARANG).map {
                Barang(
                    id = it.id,
                    nama = it.description,
                    quantity = it.quantity.toQuantityInput(),
                    hargaSatuan = it.amount.toAmountInput()
                )
            },
            listTransportasi = itemsOf(CostItemKindDto.TRANSPORTASI).map {
                Transportasi(id = it.id, keterangan = it.description, biaya = it.amount.toAmountInput())
            },
            listLainLain = itemsOf(CostItemKindDto.LAIN_LAIN).map {
                LainLain(id = it.id, keterangan = it.description, biaya = it.amount.toAmountInput())
            },
            createdAt = formatDateLabel(dto.createdAt, timeZone)
        )
    }

    fun toCreateRequest(project: Project) = CreateProjectRequest(
        id = project.id,
        name = project.name,
        customer = project.customer,
        pic = project.pic,
        hargaKontrak = project.hargaKontrak.parseRupiah(),
        items = toItemDtos(project)
    )

    fun toUpdateRequest(name: String, customer: String, pic: String, hargaKontrak: String) = UpdateProjectRequest(
        name = name,
        customer = customer,
        pic = pic,
        hargaKontrak = hargaKontrak.parseRupiah()
    )

    fun toItemDtos(project: Project): List<CostItemDto> =
        project.listJasa.map {
            CostItemDto(it.id, CostItemKindDto.JASA, it.scope, engineer = it.engineer, amount = it.harga.parseRupiah())
        } + project.listBarang.map {
            CostItemDto(
                it.id,
                CostItemKindDto.BARANG,
                it.nama,
                quantity = it.quantity.parseQuantity(),
                amount = it.hargaSatuan.parseRupiah()
            )
        } + project.listTransportasi.map {
            CostItemDto(it.id, CostItemKindDto.TRANSPORTASI, it.keterangan, amount = it.biaya.parseRupiah())
        } + project.listLainLain.map {
            CostItemDto(it.id, CostItemKindDto.LAIN_LAIN, it.keterangan, amount = it.biaya.parseRupiah())
        }
}
