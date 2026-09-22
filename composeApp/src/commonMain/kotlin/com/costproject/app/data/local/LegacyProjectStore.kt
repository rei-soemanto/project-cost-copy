package com.costproject.app.data.local

import com.costproject.app.domain.model.Barang
import com.costproject.app.domain.model.Jasa
import com.costproject.app.domain.model.LainLain
import com.costproject.app.domain.model.Project
import com.costproject.app.domain.model.Transportasi
import com.russhwolf.settings.Settings
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive

/**
 * Projects saved on the device before the app had a server.
 *
 * Earlier builds kept every project as one JSON array under the "projects" key.
 * After sign-in, ProjectRepository uploads these to the user's account and
 * removes each one from here only once the server has accepted it, so nothing
 * is lost if the upload is interrupted.
 */
class LegacyProjectStore(private val settings: Settings) {

    /** The legacy projects, or an empty list if there are none. */
    fun load(): List<Project> {
        val raw = settings.getStringOrNull(KEY_PROJECTS) ?: return emptyList()
        return decodeLegacyProjects(raw) ?: emptyList()
    }

    /** Keeps only [remaining]; removes the key entirely once nothing is left. */
    fun replace(remaining: List<Project>) {
        if (remaining.isEmpty()) {
            settings.remove(KEY_PROJECTS)
        } else {
            settings.putString(KEY_PROJECTS, encodeLegacyProjects(remaining))
        }
    }

    companion object {
        /** Must match the key the pre-server app wrote to. */
        const val KEY_PROJECTS = "projects"
    }
}

private val legacyJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
}

/**
 * Decodes the legacy JSON, or returns null if it cannot be read at all.
 *
 * Null (rather than an empty list) lets callers tell "nothing saved" apart from
 * "saved data I could not read", so unreadable data is never overwritten.
 */
fun decodeLegacyProjects(raw: String): List<Project>? = try {
    legacyJson.decodeFromString(ListSerializer(LegacyProject.serializer()), raw).map(LegacyProject::toDomain)
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

fun encodeLegacyProjects(projects: List<Project>): String =
    legacyJson.encodeToString(ListSerializer(LegacyProject.serializer()), projects.map(LegacyProject::fromDomain))

/**
 * The original app stored ids as random JSON numbers; builds after the MVVM
 * restructure store them as strings. Accepting both is what keeps an update from
 * silently discarding every existing project: a strict String field fails to
 * decode a number, and the old loader swallowed that failure.
 */
internal object FlexibleIdSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("FlexibleId", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val json = decoder as? JsonDecoder ?: return decoder.decodeString()
        return json.decodeJsonElement().jsonPrimitive.content
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

// Field names match the JSON the original app wrote; do not rename.

@Serializable
private data class LegacyJasa(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val scope: String = "",
    val engineer: String = "",
    val harga: String = ""
)

@Serializable
private data class LegacyBarang(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val nama: String = "",
    val quantity: String = "",
    val hargaSatuan: String = ""
)

@Serializable
private data class LegacyBiaya(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val keterangan: String = "",
    val biaya: String = ""
)

@Serializable
private data class LegacyProject(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val name: String = "",
    val customer: String = "",
    val pic: String = "",
    val hargaKontrak: String = "",
    val listJasa: List<LegacyJasa> = emptyList(),
    val listBarang: List<LegacyBarang> = emptyList(),
    val listTransportasi: List<LegacyBiaya> = emptyList(),
    val listLainLain: List<LegacyBiaya> = emptyList(),
    val createdAt: String = ""
) {
    fun toDomain() = Project(
        id = id,
        name = name,
        customer = customer,
        pic = pic,
        hargaKontrak = hargaKontrak,
        listJasa = listJasa.map { Jasa(it.id, it.scope, it.engineer, it.harga) },
        listBarang = listBarang.map { Barang(it.id, it.nama, it.quantity, it.hargaSatuan) },
        listTransportasi = listTransportasi.map { Transportasi(it.id, it.keterangan, it.biaya) },
        listLainLain = listLainLain.map { LainLain(it.id, it.keterangan, it.biaya) },
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(p: Project) = LegacyProject(
            id = p.id,
            name = p.name,
            customer = p.customer,
            pic = p.pic,
            hargaKontrak = p.hargaKontrak,
            listJasa = p.listJasa.map { LegacyJasa(it.id, it.scope, it.engineer, it.harga) },
            listBarang = p.listBarang.map { LegacyBarang(it.id, it.nama, it.quantity, it.hargaSatuan) },
            listTransportasi = p.listTransportasi.map { LegacyBiaya(it.id, it.keterangan, it.biaya) },
            listLainLain = p.listLainLain.map { LegacyBiaya(it.id, it.keterangan, it.biaya) },
            createdAt = p.createdAt
        )
    }
}
