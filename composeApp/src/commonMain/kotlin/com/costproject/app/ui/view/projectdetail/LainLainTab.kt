package com.costproject.app.ui.view.projectdetail

import androidx.compose.runtime.Composable
import com.costproject.app.domain.model.LainLain
import com.costproject.app.ui.view.common.AmountField
import com.costproject.app.ui.view.common.FormField

@Composable
fun LainLainTab(
    list: List<LainLain>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onUpdate: (String, (LainLain) -> LainLain) -> Unit
) {
    CostItemList(
        items = list,
        title = "Lain-lain",
        addLabel = "+ Tambah Biaya Lain-lain",
        idOf = { it.id },
        totalOf = { it.nilaiBiaya },
        onAdd = onAdd,
        onRemove = onRemove
    ) { itemLain ->
        FormField(
            value = itemLain.keterangan,
            onValueChange = { value -> onUpdate(itemLain.id) { it.copy(keterangan = value) } },
            label = "Keterangan Biaya",
            placeholder = "Contoh: Konsumsi, Dokumentasi"
        )
        AmountField(
            value = itemLain.biaya,
            onValueChange = { value -> onUpdate(itemLain.id) { it.copy(biaya = value) } },
            label = "Biaya",
            placeholder = "Contoh: 150.000"
        )
    }
}
