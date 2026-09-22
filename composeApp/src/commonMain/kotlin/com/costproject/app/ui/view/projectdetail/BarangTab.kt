package com.costproject.app.ui.view.projectdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.costproject.app.domain.model.Barang
import com.costproject.app.ui.view.common.AmountField
import com.costproject.app.ui.view.common.FormField

@Composable
fun BarangTab(
    list: List<Barang>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onUpdate: (String, (Barang) -> Barang) -> Unit
) {
    CostItemList(
        items = list,
        title = "Barang",
        addLabel = "+ Tambah Barang",
        idOf = { it.id },
        totalOf = { it.total },
        onAdd = onAdd,
        onRemove = onRemove,
        minItems = 1
    ) { barang ->
        FormField(
            value = barang.nama,
            onValueChange = { value -> onUpdate(barang.id) { it.copy(nama = value) } },
            label = "Nama Barang",
            placeholder = "Contoh: Kabel 2m"
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AmountField(
                value = barang.quantity,
                onValueChange = { value -> onUpdate(barang.id) { it.copy(quantity = value) } },
                label = "Quantity",
                placeholder = "Jumlah",
                modifier = Modifier.weight(1f)
            )
            AmountField(
                value = barang.hargaSatuan,
                onValueChange = { value -> onUpdate(barang.id) { it.copy(hargaSatuan = value) } },
                label = "Harga Satuan",
                placeholder = "Harga per unit",
                modifier = Modifier.weight(1f)
            )
        }
    }
}
