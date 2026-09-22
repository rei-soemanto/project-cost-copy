package com.costproject.app.ui.view.projectdetail

import androidx.compose.runtime.Composable
import com.costproject.app.domain.model.Transportasi
import com.costproject.app.ui.view.common.AmountField
import com.costproject.app.ui.view.common.FormField
import com.costproject.app.ui.viewmodel.ProjectViewModel

@Composable
fun TransportasiTab(
    list: List<Transportasi>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onUpdate: (String, (Transportasi) -> Transportasi) -> Unit
) {
    CostItemList(
        items = list,
        title = "Transportasi",
        addLabel = "+ Tambah Transportasi",
        idOf = { it.id },
        totalOf = { it.nilaiBiaya },
        onAdd = onAdd,
        onRemove = onRemove,
        maxItems = ProjectViewModel.MAX_TRANSPORTASI,
        maxReachedLabel = "Maksimal ${ProjectViewModel.MAX_TRANSPORTASI} kolom transportasi tercapai"
    ) { transport ->
        FormField(
            value = transport.keterangan,
            onValueChange = { value -> onUpdate(transport.id) { it.copy(keterangan = value) } },
            label = "Keterangan",
            placeholder = "Contoh: Sewa mobil, BBM, Tol"
        )
        AmountField(
            value = transport.biaya,
            onValueChange = { value -> onUpdate(transport.id) { it.copy(biaya = value) } },
            label = "Biaya",
            placeholder = "Contoh: 300.000"
        )
    }
}
