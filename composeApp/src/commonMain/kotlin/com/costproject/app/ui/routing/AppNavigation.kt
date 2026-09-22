package com.costproject.app.ui.routing

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.costproject.app.ui.view.projectdetail.ProjectView
import com.costproject.app.ui.view.projectlist.HomeTopBar
import com.costproject.app.ui.view.projectlist.ProjectHomeScreen
import com.costproject.app.ui.viewmodel.ProjectViewModel

/**
 * Navigation graph for the app.
 *
 * The ProjectViewModel is created once here and shared by both destinations, so
 * the list and the detail screen read the same project data. Phase 7 splits this
 * into per-screen ViewModels backed by a repository.
 */
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    viewModel: ProjectViewModel = viewModel()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.ProjectList.route
    ) {
        composable(Screen.ProjectList.route) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = { HomeTopBar() }
            ) { padding ->
                ProjectHomeScreen(
                    projects = viewModel.projects,
                    onCreate = { name, customer, pic, contract ->
                        val id = viewModel.createProject(name, customer, pic, contract)
                        navController.navigate(Screen.ProjectDetail.createRoute(id))
                    },
                    onOpen = { id ->
                        navController.navigate(Screen.ProjectDetail.createRoute(id))
                    },
                    onDelete = viewModel::deleteProject,
                    modifier = Modifier.padding(padding)
                )
            }
        }

        composable(
            route = Screen.ProjectDetail.route,
            arguments = listOf(navArgument(Screen.ARG_PROJECT_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString(Screen.ARG_PROJECT_ID)

            // Point the shared ViewModel at the project this destination is showing.
            LaunchedEffect(projectId) {
                if (projectId != null) viewModel.openProject(projectId)
            }

            val project = viewModel.projects.find { it.id == projectId }
            if (project == null) {
                // The project was deleted (or the id is unknown) - fall back to the list
                // rather than rendering an empty detail screen.
                LaunchedEffect(projectId) { navController.popBackStack() }
            } else {
                ProjectView(
                    project = project,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
