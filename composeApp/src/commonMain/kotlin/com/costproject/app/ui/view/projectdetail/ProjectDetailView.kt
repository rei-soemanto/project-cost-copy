package com.costproject.app.ui.view.projectdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.costproject.app.domain.model.Project
import com.costproject.app.ui.util.PlatformBackHandler
import com.costproject.app.ui.util.formatRupiah
import com.costproject.app.ui.view.projectlist.ProjectInfoDialog
import com.costproject.app.ui.viewmodel.ProjectDetailUiState
import com.costproject.app.ui.viewmodel.ProjectDetailViewModel
import com.costproject.app.ui.viewmodel.SaveStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** How long leaving the screen waits for a final save before offering to discard. */
private const val LEAVE_SAVE_TIMEOUT_MILLIS = 5_000L

@Composable
fun ProjectDetailScreen(viewModel: ProjectDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    when (val s = state) {
        ProjectDetailUiState.Loading -> PlainScreen(onBack) { CircularProgressIndicator() }
        is ProjectDetailUiState.Error -> PlainScreen(onBack) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.message, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                Spacer(Modifier.height(12.dp))
                Button(onClick = viewModel::load) { Text("Coba lagi") }
            }
        }
        is ProjectDetailUiState.Editing -> ProjectEditor(s.project, s.saveStatus, viewModel, onBack)
    }
}

@Composable
private fun ProjectEditor(
    project: Project,
    saveStatus: SaveStatus,
    viewModel: ProjectDetailViewModel,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showEditInfo by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var isLeaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Leaving waits briefly for the last edits to reach the server. If they
    // cannot (e.g. offline), the user chooses between retrying and discarding,
    // rather than losing the edits without knowing.
    val requestBack: () -> Unit = {
        if (!isLeaving) {
            isLeaving = true
            scope.launch {
                val saved = withTimeoutOrNull(LEAVE_SAVE_TIMEOUT_MILLIS) { viewModel.saveNow() } ?: false
                isLeaving = false
                if (saved) onBack() else showUnsavedDialog = true
            }
        }
    }
    PlatformBackHandler(onBack = requestBack)

    val titles = listOf("Jasa", "Barang", "Transport", "Lain-lain", "Rekap")

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            ProjectTopBar(
                project = project,
                saveStatus = saveStatus,
                isLeaving = isLeaving,
                onBack = requestBack,
                onEditInfo = { showEditInfo = true }
            )
        },
        bottomBar = {
            BottomSummary(total = project.grandTotal, onTapTotal = { selectedTab = titles.lastIndex })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // The Scaffold's default insets exclude the keyboard; consume what it
                // applied, then pad for the keyboard so fields stay above it.
                .consumeWindowInsets(innerPadding)
                .imePadding()
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

            if (saveStatus is SaveStatus.Failed) {
                SaveFailedBanner(message = saveStatus.message, onRetry = viewModel::retrySave)
            }

            when (selectedTab) {
                0 -> JasaTab(project.listJasa, viewModel::addJasa, viewModel::removeJasa, viewModel::updateJasa)
                1 -> BarangTab(project.listBarang, viewModel::addBarang, viewModel::removeBarang, viewModel::updateBarang)
                2 -> TransportasiTab(
                    project.listTransportasi,
                    viewModel::addTransportasi,
                    viewModel::removeTransportasi,
                    viewModel::updateTransportasi
                )
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
                viewModel.updateInfo(name, customer, pic, contract)
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
                    viewModel.resetItems()
                    showResetDialog = false
                }) { Text("Ya, Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Batal") } }
        )
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Perubahan Belum Tersimpan") },
            text = {
                Text("Perubahan terakhir belum terkirim ke server. Periksa koneksi internet Anda. Jika keluar sekarang, perubahan tersebut bisa hilang.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    requestBack()
                }) { Text("Coba Lagi") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    onBack()
                }) { Text("Keluar", color = MaterialTheme.colorScheme.error) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectTopBar(
    project: Project,
    saveStatus: SaveStatus,
    isLeaving: Boolean,
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
            IconButton(onClick = onBack, enabled = !isLeaving) {
                if (isLeaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                }
            }
        },
        actions = {
            SaveStatusIcon(saveStatus)
            IconButton(onClick = onEditInfo) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit info project", tint = Color.White)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = Color.White
        )
    )
}

/** Quiet when all is well; the failure case also gets a banner with a retry button. */
@Composable
private fun SaveStatusIcon(status: SaveStatus) {
    val (icon, description) = when (status) {
        SaveStatus.Saved -> Icons.Filled.CloudDone to "Tersimpan"
        SaveStatus.Pending -> Icons.Filled.CloudUpload to "Belum tersimpan"
        SaveStatus.Saving -> Icons.Filled.CloudUpload to "Menyimpan"
        is SaveStatus.Failed -> Icons.Filled.CloudOff to "Gagal menyimpan"
    }
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = Color.White.copy(alpha = if (status == SaveStatus.Saved) 0.7f else 1f),
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}

@Composable
private fun SaveFailedBanner(message: String, onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) { Text("Coba lagi") }
        }
    }
}

@Composable
fun BottomSummary(total: Long, onTapTotal: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        onClick = onTapTotal
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // The app draws edge to edge, and a custom bottom bar gets no
                // insets from the Scaffold. The card's colour extends under the
                // navigation bar, while this padding keeps the total above it.
                .windowInsetsPadding(
                    WindowInsets.navigationBars
                        .union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                )
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
            Icon(imageVector = Icons.Filled.Savings, contentDescription = null, tint = Color.White)
        }
    }
}

/** Minimal screen for the loading and error states, with a working back arrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlainScreen(onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Project", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { content() }
    }
}
