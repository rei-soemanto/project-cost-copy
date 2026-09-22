package com.costproject.app.domain.model

import com.costproject.app.util.todayLabel
import kotlin.random.Random
import kotlinx.serialization.Serializable

private fun newId(): Long = Random.nextLong(1, Long.MAX_VALUE)

@Serializable
data class Jasa(
    val id: Long = newId(),
    var scope: String = "",
    var engineer: String = "",
    var harga: String = ""
) {
    val nilaiHarga: Double
        get() = harga.toDoubleOrNull() ?: 0.0
}

@Serializable
data class Barang(
    val id: Long = newId(),
    var nama: String = "",
    var quantity: String = "",
    var hargaSatuan: String = ""
) {
    val total: Double
        get() = (hargaSatuan.toDoubleOrNull() ?: 0.0) * (quantity.toDoubleOrNull() ?: 0.0)
}

@Serializable
data class Transportasi(
    val id: Long = newId(),
    var keterangan: String = "",
    var biaya: String = ""
) {
    val nilaiBiaya: Double
        get() = biaya.toDoubleOrNull() ?: 0.0
}

@Serializable
data class LainLain(
    val id: Long = newId(),
    var keterangan: String = "",
    var biaya: String = ""
) {
    val nilaiBiaya: Double
        get() = biaya.toDoubleOrNull() ?: 0.0
}

@Serializable
data class Project(
    val id: Long = newId(),
    var name: String = "",
    var customer: String = "",
    var pic: String = "",
    var hargaKontrak: String = "",
    var listJasa: List<Jasa> = listOf(Jasa()),
    var listBarang: List<Barang> = listOf(Barang()),
    var listTransportasi: List<Transportasi> = listOf(),
    var listLainLain: List<LainLain> = listOf(),
    val createdAt: String = todayLabel()
) {
    val nilaiKontrak: Double
        get() = hargaKontrak.toDoubleOrNull() ?: 0.0

    val totalJasa: Double
        get() = listJasa.sumOf { it.nilaiHarga }

    val totalBarang: Double
        get() = listBarang.sumOf { it.total }

    val totalTransportasi: Double
        get() = listTransportasi.sumOf { it.nilaiBiaya }

    val totalLainLain: Double
        get() = listLainLain.sumOf { it.nilaiBiaya }

    val grandTotal: Double
        get() = totalJasa + totalBarang + totalTransportasi + totalLainLain

    val sisaKontrak: Double
        get() = nilaiKontrak - grandTotal
}
