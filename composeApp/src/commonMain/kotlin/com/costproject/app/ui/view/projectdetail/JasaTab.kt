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
import com.costproject.app.domain.model.Jasa
import com.costproject.app.ui.util.formatRupiah
import com.costproject.app.ui.view.common.AddButton
import com.costproject.app.ui.view.common.FormField
import com.costproject.app.ui.view.common.SectionCard

@Composable
fun JasaTab(
    list: List<Jasa>,
    onAdd: () -> Unit,
    onRemove: (Long) -> Unit,
    onUpdate: (Long, String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(list, key = { it.id }) { jasa ->
            val index = list.indexOf(jasa) + 1
            SectionCard(
                title = "Jasa",
                number = index.toString(),
                onRemove = { onRemove(jasa.id) },
                canRemove = list.size > 1
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField(
                        value = jasa.scope,
                        onValueChange = { onUpdate(jasa.id, "scope", it) },
                        label = "Scope Pekerjaan",
                        placeholder = "Contoh: Rancang bangun sistem"
                    )
                    FormField(
                        value = jasa.engineer,
                        onValueChange = { onUpdate(jasa.id, "engineer", it) },
                        label = "Engineer",
                        placeholder = "Nama engineer yang mengerjakan"
                    )
                    FormField(
                        value = jasa.harga,
                        onValueChange = { onUpdate(jasa.id, "harga", it) },
                        label = "Harga Jasa",
                        placeholder = "Contoh: 5000000"
                    )
                    ItemTotalRow(text = "Total: " + jasa.nilaiHarga.formatRupiah())
                }
            }
        }
        item {
            AddButton(text = "+ Tambah Jasa", onClick = onAdd)
        }
    }
}
