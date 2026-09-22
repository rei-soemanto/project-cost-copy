package com.costproject.app.ui.view.projectdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.costproject.app.domain.model.LainLain
import com.costproject.app.ui.util.formatRupiah
import com.costproject.app.ui.view.common.AddButton
import com.costproject.app.ui.view.common.FormField
import com.costproject.app.ui.view.common.SectionCard

@Composable
fun LainLainTab(
    list: List<LainLain>,
    onAdd: () -> Unit,
    onRemove: (Long) -> Unit,
    onUpdate: (Long, String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(list, key = { it.id }) { itemLain ->
            val index = list.indexOf(itemLain) + 1
            SectionCard(
                title = "Lain-lain",
                number = index.toString(),
                onRemove = { onRemove(itemLain.id) },
                canRemove = list.size > 1
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField(
                        value = itemLain.keterangan,
                        onValueChange = { onUpdate(itemLain.id, "keterangan", it) },
                        label = "Keterangan Biaya",
                        placeholder = "Contoh: Konsumsi, Dokumentasi"
                    )
                    FormField(
                        value = itemLain.biaya,
                        onValueChange = { onUpdate(itemLain.id, "biaya", it) },
                        label = "Biaya",
                        placeholder = "Contoh: 150000"
                    )
                    ItemTotalRow(text = "Total: " + itemLain.nilaiBiaya.formatRupiah())
                }
            }
        }
        item {
            AddButton(text = "+ Tambah Biaya Lain-lain", onClick = onAdd)
        }
    }
}
