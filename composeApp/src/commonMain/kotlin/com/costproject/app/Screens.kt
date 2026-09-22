package com.costproject.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun JasaTab(
    list: List<Jasa>,
    onAdd: () -> Unit,
    onRemove: (Long) -> Unit,
    onUpdate: (Long, String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "Total: " + ProjectViewModel.formatRupiah(jasa.nilaiHarga),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        item {
            AddButton(text = "+ Tambah Jasa", onClick = onAdd)
        }
    }
}

@Composable
fun BarangTab(
    list: List<Barang>,
    onAdd: () -> Unit,
    onRemove: (Long) -> Unit,
    onUpdate: (Long, String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "Total: " + ProjectViewModel.formatRupiah(barang.total),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        item {
            AddButton(text = "+ Tambah Barang", onClick = onAdd)
        }
    }
}

@Composable
fun TransportasiTab(
    list: List<Transportasi>,
    onAdd: () -> Unit,
    onRemove: (Long) -> Unit,
    onUpdate: (Long, String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "Total: " + ProjectViewModel.formatRupiah(transport.nilaiBiaya),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        item {
            if (list.size < 20) {
                AddButton(text = "+ Tambah Transportasi (${list.size}/20)", onClick = onAdd)
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
                        text = "Maksimal 20 kolom transportasi tercapai",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun LainLainTab(
    list: List<LainLain>,
    onAdd: () -> Unit,
    onRemove: (Long) -> Unit,
    onUpdate: (Long, String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "Total: " + ProjectViewModel.formatRupiah(itemLain.nilaiBiaya),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        item {
            AddButton(text = "+ Tambah Biaya Lain-lain", onClick = onAdd)
        }
    }
}

@Composable
fun RekapTab(
    project: Project,
    onReset: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "GRAND TOTAL",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ProjectViewModel.formatRupiah(project.grandTotal),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Detail Project",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SummaryRow("Project", project.name.ifBlank { "-" })
                    SummaryRow("Customer", project.customer.ifBlank { "-" })
                    SummaryRow("Penanggung Jawab", project.pic.ifBlank { "-" })
                    SummaryRow("Dibuat", project.createdAt)
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Rincian Biaya",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SummaryRow("Jasa", ProjectViewModel.formatRupiah(project.totalJasa))
                    SummaryRow("Barang", ProjectViewModel.formatRupiah(project.totalBarang))
                    SummaryRow("Transportasi", ProjectViewModel.formatRupiah(project.totalTransportasi))
                    SummaryRow("Lain-lain", ProjectViewModel.formatRupiah(project.totalLainLain))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SummaryRow("Total Biaya", ProjectViewModel.formatRupiah(project.grandTotal), isTotal = true)
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Kontrak Project",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SummaryRow(
                        "Harga Kontrak",
                        if (project.hargaKontrak.isBlank()) "-" else ProjectViewModel.formatRupiah(project.nilaiKontrak)
                    )
                    SummaryRow("Total Biaya", ProjectViewModel.formatRupiah(project.grandTotal))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sisa Kontrak",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (project.hargaKontrak.isBlank()) "-"
                            else ProjectViewModel.formatRupiah(project.sisaKontrak),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (project.sisaKontrak >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    if (project.hargaKontrak.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (project.sisaKontrak >= 0)
                                "Kontrak dikurangi total Jasa, Barang, Transportasi, dan Lain-lain. Nilai positif = sisa keuntungan."
                            else
                                "Biaya melebihi nilai kontrak. Nilai negatif = potensi kerugian.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            AddButton(text = "Reset Data Project Ini", onClick = onReset)
        }
    }
}

@Composable
fun ProjectHomeScreen(
    projects: List<Project>,
    onCreate: (String, String, String, String) -> Unit,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreate by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "DAFTAR PROJECT",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            AddButton(text = "+ Buat Project Baru", onClick = { showCreate = true })
        }
        if (projects.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Belum ada project",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tekan \"Buat Project Baru\" untuk mulai menghitung biaya pekerjaan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        items(projects, key = { it.id }) { project ->
            ProjectCard(
                project = project,
                onClick = { onOpen(project.id) },
                onDelete = { onDelete(project.id) }
            )
        }
    }

    if (showCreate) {
        ProjectInfoDialog(
            title = "Buat Project Baru",
            initialName = "",
            initialCustomer = "",
            initialPic = "",
            initialContract = "",
            confirmLabel = "Simpan",
            onDismiss = { showCreate = false },
            onConfirm = { name, customer, pic, contract ->
                onCreate(name, customer, pic, contract)
                showCreate = false
            }
        )
    }
}

@Composable
fun ProjectCard(
    project: Project,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = project.name.ifBlank { "Project tanpa nama" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Hapus project",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Customer: ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = project.customer.ifBlank { "-" },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Penanggung Jawab: ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = project.pic.ifBlank { "-" },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Harga Kontrak",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (project.hargaKontrak.isBlank()) "-" else ProjectViewModel.formatRupiah(project.nilaiKontrak),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Sisa Kontrak",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (project.hargaKontrak.isBlank()) "-" else ProjectViewModel.formatRupiah(project.sisaKontrak),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (project.sisaKontrak >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = project.createdAt,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Total Biaya: " + ProjectViewModel.formatRupiah(project.grandTotal),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

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
                FormField(
                    value = contract,
                    onValueChange = { contract = it },
                    label = "Harga Kontrak",
                    placeholder = "Contoh: 50000000"
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