package com.costproject.app.ui.view.projectlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.costproject.app.ui.view.common.AmountField
import com.costproject.app.ui.view.common.FormField

@Composable
fun ProjectInfoDialog(
    title: String,
    initialName: String,
    initialCustomer: String,
    initialPic: String,
    initialContract: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, customer: String, pic: String, contract: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var customer by remember { mutableStateOf(initialCustomer) }
    var pic by remember { mutableStateOf(initialPic) }
    var contract by remember { mutableStateOf(initialContract) }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FormField(
                    value = name,
                    onValueChange = { name = it; showError = false },
                    label = "Nama Project",
                    placeholder = "Contoh: Project IT System"
                )
                FormField(
                    value = customer,
                    onValueChange = { customer = it },
                    label = "Customer (Perusahaan)",
                    placeholder = "Contoh: PT Maju Jaya"
                )
                FormField(
                    value = pic,
                    onValueChange = { pic = it },
                    label = "Penanggung Jawab Customer",
                    placeholder = "Nama PIC customer"
                )
                AmountField(
                    value = contract,
                    onValueChange = { contract = it },
                    label = "Harga Kontrak",
                    placeholder = "Contoh: 50.000.000"
                )
                if (showError) {
                    Text(
                        text = "Nama project wajib diisi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    showError = true
                } else {
                    onConfirm(name, customer, pic, contract)
                }
            }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
