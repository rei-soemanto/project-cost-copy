package com.costproject.app.domain.model

import com.costproject.app.ui.util.parseQuantity
import com.costproject.app.ui.util.parseRupiah
import kotlin.random.Random

/**
 * Client-generated identifier. Strings rather than random Longs so the server can
 * accept a client-created id as-is, which keeps creates idempotent on retry.
 */
internal fun newId(): String {
    val alphabet = "0123456789abcdef"
    return buildString(24) { repeat(24) { append(alphabet[Random.nextInt(alphabet.length)]) } }
}

/**
 * Amount fields hold the raw text the user typed. The parsed value is exposed
 * separately as whole rupiah in a [Long]; see CurrencyFormat.kt for why money is
 * never a Double here. Keeping the raw text lets a partially typed field stay
 * exactly as entered instead of being reformatted mid-keystroke.
 *
 * Every property is a `val`. The previous models used `var`, which made them
 * unstable to the Compose compiler and prevented composables from skipping
 * recomposition — and nothing ever mutated them anyway, since all updates go
 * through `copy()`.
 */
data class Jasa(
    val id: String = newId(),
    val scope: String = "",
    val engineer: String = "",
    val harga: String = ""
) {
    val nilaiHarga: Long get() = harga.parseRupiah()
}

data class Barang(
    val id: String = newId(),
    val nama: String = "",
    val quantity: String = "",
    val hargaSatuan: String = ""
) {
    val total: Long get() = quantity.parseQuantity() * hargaSatuan.parseRupiah()
}

data class Transportasi(
    val id: String = newId(),
    val keterangan: String = "",
    val biaya: String = ""
) {
    val nilaiBiaya: Long get() = biaya.parseRupiah()
}

data class LainLain(
    val id: String = newId(),
    val keterangan: String = "",
    val biaya: String = ""
) {
    val nilaiBiaya: Long get() = biaya.parseRupiah()
}

data class Project(
    val id: String = newId(),
    val name: String = "",
    val customer: String = "",
    val pic: String = "",
    val hargaKontrak: String = "",
    val listJasa: List<Jasa> = listOf(Jasa()),
    val listBarang: List<Barang> = listOf(Barang()),
    val listTransportasi: List<Transportasi> = emptyList(),
    val listLainLain: List<LainLain> = emptyList(),
    /** Display label such as "22 Sep 2026", set from the server's timestamp. Empty until saved. */
    val createdAt: String = ""
) {
    val nilaiKontrak: Long get() = hargaKontrak.parseRupiah()

    val totalJasa: Long get() = listJasa.sumOf { it.nilaiHarga }

    val totalBarang: Long get() = listBarang.sumOf { it.total }

    val totalTransportasi: Long get() = listTransportasi.sumOf { it.nilaiBiaya }

    val totalLainLain: Long get() = listLainLain.sumOf { it.nilaiBiaya }

    val grandTotal: Long get() = totalJasa + totalBarang + totalTransportasi + totalLainLain

    val sisaKontrak: Long get() = nilaiKontrak - grandTotal

    /** True when the user has entered a contract value, so the UI can show "-" instead of Rp0. */
    val hasKontrak: Boolean get() = hargaKontrak.isNotBlank()

    companion object {
        /** Maximum transportasi rows per project. The server enforces the same limit. */
        const val MAX_TRANSPORTASI = 20
    }
}
