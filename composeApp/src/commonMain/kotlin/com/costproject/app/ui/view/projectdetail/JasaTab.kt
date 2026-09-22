package com.costproject.app.ui.view.projectdetail

import androidx.compose.runtime.Composable
import com.costproject.app.domain.model.Jasa
import com.costproject.app.ui.view.common.AmountField
import com.costproject.app.ui.view.common.FormField

@Composable
fun JasaTab(
    list: List<Jasa>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onUpdate: (String, (Jasa) -> Jasa) -> Unit
) {
    CostItemList(
        items = list,
        title = "Jasa",
        addLabel = "+ Tambah Jasa",
        idOf = { it.id },
        totalOf = { it.nilaiHarga },
        onAdd = onAdd,
        onRemove = onRemove,
        minItems = 1
    ) { jasa ->
        FormField(
            value = jasa.scope,
            onValueChange = { value -> onUpdate(jasa.id) { it.copy(scope = value) } },
            label = "Scope Pekerjaan",
            placeholder = "Contoh: Rancang bangun sistem"
        )
        FormField(
            value = jasa.engineer,
            onValueChange = { value -> onUpdate(jasa.id) { it.copy(engineer = value) } },
            label = "Engineer",
            placeholder = "Nama engineer yang mengerjakan"
        )
        AmountField(
            value = jasa.harga,
            onValueChange = { value -> onUpdate(jasa.id) { it.copy(harga = value) } },
            label = "Harga Jasa",
            placeholder = "Contoh: 5.000.000"
        )
    }
}
