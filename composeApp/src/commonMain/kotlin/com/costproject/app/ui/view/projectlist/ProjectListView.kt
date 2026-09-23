package com.costproject.app.ui.view.projectlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.costproject.app.domain.model.Project
import com.costproject.app.ui.util.XLSX_MIME_TYPE
import com.costproject.app.ui.util.rememberFileSaver
import com.costproject.app.ui.view.common.AddButton
import com.costproject.app.ui.viewmodel.ProjectListEvent
import com.costproject.app.ui.viewmodel.ProjectListUiState
import com.costproject.app.ui.viewmodel.ProjectListViewModel
import kotlinx.coroutines.launch

@Composable
fun ProjectListScreen(
    viewModel: ProjectListViewModel,
    onOpenProject: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val fileSaver = rememberFileSaver(
        mimeType = XLSX_MIME_TYPE,
        content = { viewModel.pendingExportBytes },
        onResult = viewModel::onExportSaved
    )
    val scope = rememberCoroutineScope()

    var showCreate by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Project?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ProjectListEvent.OpenProject -> onOpenProject(event.id)
                // Launched separately: showSnackbar suspends until dismissed, which
                // would otherwise hold up the next event.
                is ProjectListEvent.ShowMessage -> scope.launch { snackbarHostState.showSnackbar(event.message) }
                is ProjectListEvent.SaveFile -> fileSaver.launch(event.fileName)
            }
        }
    }

    // Tells the ViewModel when the list is actually on screen, so it refreshes
    // on return from a project but not on every autosave while one is open.
    DisposableEffect(viewModel) {
        viewModel.onVisible()
        onDispose { viewModel.onHidden() }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            HomeTopBar(
                userName = viewModel.currentUser?.fullName,
                isAdmin = isAdmin,
                isExporting = isExporting,
                onRefresh = viewModel::refresh,
                onExport = viewModel::export,
                onLogout = viewModel::logout
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        when (val s = state) {
            ProjectListUiState.Loading -> CenteredBox(innerPadding) { CircularProgressIndicator() }

            is ProjectListUiState.Error -> CenteredBox(innerPadding) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = viewModel::load) { Text("Coba lagi") }
                }
            }

            is ProjectListUiState.Success -> ProjectListContent(
                projects = s.projects,
                isRefreshing = s.isRefreshing,
                contentPadding = innerPadding,
                onCreateClick = { showCreate = true },
                onOpen = onOpenProject,
                onDelete = { pendingDelete = it }
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
                viewModel.createProject(name, customer, pic, contract)
                showCreate = false
            }
        )
    }

    // Deleting now removes the project from the server for good, so it is
    // confirmed first. Previously a single tap on the icon deleted it.
    pendingDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Hapus Project?") },
            text = { Text("\"${project.name.ifBlank { "Project tanpa nama" }}\" dan semua rincian biayanya akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProject(project.id)
                    pendingDelete = null
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Batal") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    userName: String?,
    isAdmin: Boolean,
    isExporting: Boolean,
    onRefresh: () -> Unit,
    onExport: (all: Boolean) -> Unit,
    onLogout: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(text = "COST PROJECT", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = userName?.let { "Masuk sebagai $it" } ?: "Kalkulator Biaya Pekerjaan",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Muat ulang", tint = Color.White)
            }
            ExportButton(isAdmin = isAdmin, isExporting = isExporting, onExport = onExport)
            IconButton(onClick = onLogout) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Keluar", tint = Color.White)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = Color.White
        )
    )
}

@Composable
private fun ProjectListContent(
    projects: List<Project>,
    isRefreshing: Boolean,
    contentPadding: PaddingValues,
    onCreateClick: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (Project) -> Unit
) {
    Box(Modifier.fillMaxSize().consumeWindowInsets(contentPadding)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // The Scaffold's insets go into contentPadding rather than a padding
            // modifier, so the list scrolls behind the transparent navigation bar
            // instead of being clipped above it.
            contentPadding = contentPadding.plus(16.dp),
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
            item { AddButton(text = "+ Buat Project Baru", onClick = onCreateClick) }
            if (projects.isEmpty()) {
                item { EmptyProjectsCard() }
            }
            items(projects, key = { it.id }) { project ->
                ProjectCard(project = project, onClick = { onOpen(project.id) }, onDelete = { onDelete(project) })
            }
        }
        if (isRefreshing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(top = contentPadding.calculateTopPadding())
            )
        }
    }
}

@Composable
private fun EmptyProjectsCard() {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Belum ada project", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tekan \"Buat Project Baru\" untuk mulai menghitung biaya pekerjaan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CenteredBox(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { content() }
}

/** Adds [extra] on every side of these insets. */
@Composable
private fun PaddingValues.plus(extra: androidx.compose.ui.unit.Dp): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction) + extra,
        top = calculateTopPadding() + extra,
        end = calculateEndPadding(direction) + extra,
        bottom = calculateBottomPadding() + extra
    )
}

/**
 * Downloads an Excel backup. Everyone exports their own projects in one tap;
 * admins get a menu with the all-users export as well.
 */
@Composable
private fun ExportButton(isAdmin: Boolean, isExporting: Boolean, onExport: (all: Boolean) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { if (isAdmin) menuOpen = true else onExport(false) },
            enabled = !isExporting
        ) {
            if (isExporting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Icon(Icons.Filled.FileDownload, contentDescription = "Ekspor Excel", tint = Color.White)
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Ekspor project saya") },
                onClick = {
                    menuOpen = false
                    onExport(false)
                }
            )
            DropdownMenuItem(
                text = { Text("Ekspor semua data (admin)") },
                onClick = {
                    menuOpen = false
                    onExport(true)
                }
            )
        }
    }
}
