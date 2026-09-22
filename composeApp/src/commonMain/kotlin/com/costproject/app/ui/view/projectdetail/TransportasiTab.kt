package com.costproject.app.ui.view.projectdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.costproject.app.domain.model.Transportasi
import com.costproject.app.ui.util.formatRupiah
import com.costproject.app.ui.view.common.AddButton
import com.costproject.app.ui.view.common.FormField
import com.costproject.app.ui.view.common.SectionCard
import com.costproject.app.ui.viewmodel.ProjectViewModel

@Composable
fun TransportasiTab(
    list: List<Transportasi>,
    onAdd: () -> Unit,
    onRemove: (Long) -> Unit,
    onUpdate: (Long, String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(list, key = { it.id }) { transport ->
            val index = list.indexOf(transport) + 1
            SectionCard(
                title = "Transportasi",
                number = index.toString(),
                onRemove = { onRemove(transport.id) },
                canRemove = true
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField(
                        value = transport.keterangan,
                        onValueChange = { onUpdate(transport.id, "keterangan", it) },
                        label = "Keterangan",
                        placeholder = "Contoh: Sewa mobil, BBM, Tol"
                    )
                    FormField(
                        value = transport.biaya,
                        onValueChange = { onUpdate(transport.id, "biaya", it) },
                        label = "Biaya",
                        placeholder = "Contoh: 300000"
                    )
                    ItemTotalRow(text = "Total: " + transport.nilaiBiaya.formatRupiah())
                }
            }
        }
        item {
            if (list.size < ProjectViewModel.MAX_TRANSPORTASI) {
                AddButton(
                    text = "+ Tambah Transportasi (${list.size}/${ProjectViewModel.MAX_TRANSPORTASI})",
                    onClick = onAdd
                )
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                ) {
                    Text(
                        text = "Maksimal ${ProjectViewModel.MAX_TRANSPORTASI} kolom transportasi tercapai",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
