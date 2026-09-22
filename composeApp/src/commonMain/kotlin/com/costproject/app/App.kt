package com.costproject.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.costproject.app.ui.theme.CostProjectTheme
import com.costproject.app.ui.view.projectdetail.ProjectView
import com.costproject.app.ui.view.projectlist.HomeTopBar
import com.costproject.app.ui.view.projectlist.ProjectHomeScreen
import com.costproject.app.ui.viewmodel.ProjectViewModel

@Composable
fun App() {
    CostProjectTheme {
        CostProjectApp()
    }
}

@Composable
fun CostProjectApp(viewModel: ProjectViewModel = viewModel()) {
    val project = viewModel.activeProject

    if (project == null) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = { HomeTopBar() }
        ) { padding ->
            ProjectHomeScreen(
                projects = viewModel.projects,
                onCreate = viewModel::createProject,
                onOpen = viewModel::openProject,
                onDelete = viewModel::deleteProject,
                modifier = Modifier.padding(padding)
            )
        }
    } else {
        ProjectView(project, viewModel)
    }
}
