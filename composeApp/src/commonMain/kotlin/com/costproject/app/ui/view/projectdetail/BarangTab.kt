package com.costproject.app.ui.view.projectdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.costproject.app.domain.model.Barang
import com.costproject.app.ui.util.formatRupiah
import com.costproject.app.ui.view.common.AddButton
import com.costproject.app.ui.view.common.FormField
import com.costproject.app.ui.view.common.SectionCard

@Composable
fun BarangTab(
    list: List<Barang>,
    onAdd: () -> Unit,
    onRemove: (Long) -> Unit,
    onUpdate: (Long, String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(list, key = { it.id }) { barang ->
            val index = list.indexOf(barang) + 1
            SectionCard(
                title = "Barang",
                number = index.toString(),
                onRemove = { onRemove(barang.id) },
                canRemove = list.size > 1
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField(
                        value = barang.nama,
                        onValueChange = { onUpdate(barang.id, "nama", it) },
                        label = "Nama Barang",
                        placeholder = "Contoh: Kabel 2m"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FormField(
                            value = barang.quantity,
                            onValueChange = { onUpdate(barang.id, "quantity", it) },
                            label = "Quantity",
                            placeholder = "Jumlah",
                            modifier = Modifier.weight(1f)
                        )
                        FormField(
                            value = barang.hargaSatuan,
                            onValueChange = { onUpdate(barang.id, "hargaSatuan", it) },
                            label = "Harga Satuan",
                            placeholder = "Harga per unit",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    ItemTotalRow(text = "Total: " + barang.total.formatRupiah())
                }
            }
        }
        item {
            AddButton(text = "+ Tambah Barang", onClick = onAdd)
        }
    }
}
