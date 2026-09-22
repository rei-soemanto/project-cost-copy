package com.costproject.app.ui.view.projectdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.costproject.app.domain.model.Project
import com.costproject.app.ui.util.formatRupiah
import com.costproject.app.ui.view.projectlist.ProjectInfoDialog
import com.costproject.app.ui.viewmodel.ProjectViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectTopBar(
    project: Project,
    onBack: () -> Unit,
    onEditInfo: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = project.name.ifBlank { "Project Baru" },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Customer: ${project.customer.ifBlank { "-" }}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali",
                    tint = Color.White
                )
            }
        },
        actions = {
            IconButton(onClick = onEditInfo) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit info project",
                    tint = Color.White
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = Color.White
        )
    )
}

@Composable
fun ProjectView(project: Project, viewModel: ProjectViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showEditInfo by remember { mutableStateOf(false) }

    val titles = listOf("Jasa", "Barang", "Transport", "Lain-lain", "Rekap")

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            ProjectTopBar(
                project = project,
                onBack = viewModel::closeProject,
                onEditInfo = { showEditInfo = true }
            )
        },
        bottomBar = {
            BottomSummary(total = project.grandTotal, onTapTotal = { selectedTab = 4 })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                titles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            when (selectedTab) {
                0 -> JasaTab(project.listJasa, viewModel::addJasa, viewModel::removeJasa, viewModel::updateJasa)
                1 -> BarangTab(project.listBarang, viewModel::addBarang, viewModel::removeBarang, viewModel::updateBarang)
                2 -> TransportasiTab(project.listTransportasi, viewModel::addTransportasi, viewModel::removeTransportasi, viewModel::updateTransportasi)
                3 -> LainLainTab(project.listLainLain, viewModel::addLainLain, viewModel::removeLainLain, viewModel::updateLainLain)
                4 -> RekapTab(project = project, onReset = { showResetDialog = true })
            }
        }
    }

    if (showEditInfo) {
        ProjectInfoDialog(
            title = "Edit Project",
            initialName = project.name,
            initialCustomer = project.customer,
            initialPic = project.pic,
            initialContract = project.hargaKontrak,
            confirmLabel = "Simpan",
            onDismiss = { showEditInfo = false },
            onConfirm = { name, customer, pic, contract ->
                viewModel.updateProjectInfo(name, customer, pic, contract)
                showEditInfo = false
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Data Project?") },
            text = { Text("Semua data biaya pada project ini akan dihapus. Anda yakin?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetCurrent()
                    showResetDialog = false
                }) {
                    Text("Ya, Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun BottomSummary(total: Long, onTapTotal: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        onClick = onTapTotal
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "TOTAL BIAYA",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    text = total.formatRupiah(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Icon(
                imageVector = Icons.Filled.Savings,
                contentDescription = null,
                tint = Color.White
            )
        }
    }
}
