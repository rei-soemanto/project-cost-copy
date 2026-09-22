package com.costproject.app.data.dto

import kotlinx.serialization.Serializable

/**
 * Wire shapes for projects. Money is always a whole-rupiah Long here - the
 * conversion from the user's typed text happens in ProjectMapper, never on
 * the wire.
 */
@Serializable
data class ProjectDto(
    val id: String,
    val name: String,
    val customer: String,
    val pic: String,
    val hargaKontrak: Long,
    val items: List<CostItemDto>,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
enum class CostItemKindDto { JASA, BARANG, TRANSPORTASI, LAIN_LAIN }

/** One line item; which fields apply depends on [kind] - see the table in API.md. */
@Serializable
data class CostItemDto(
    val id: String,
    val kind: CostItemKindDto,
    val description: String,
    val engineer: String? = null,
    val quantity: Long? = null,
    val amount: Long
)

@Serializable
data class CreateProjectRequest(
    val id: String,
    val name: String,
    val customer: String,
    val pic: String,
    val hargaKontrak: Long,
    val items: List<CostItemDto>
)

/** Every field optional; null fields are omitted from the JSON (explicitNulls = false). */
@Serializable
data class UpdateProjectRequest(
    val name: String? = null,
    val customer: String? = null,
    val pic: String? = null,
    val hargaKontrak: Long? = null
)

@Serializable
data class ReplaceItemsRequest(val items: List<CostItemDto>)
