package com.costproject.app.ui.routing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.costproject.app.data.container.AppContainer
import com.costproject.app.ui.ViewModelFactory
import com.costproject.app.ui.view.auth.AuthScreen
import com.costproject.app.ui.view.projectdetail.ProjectDetailScreen
import com.costproject.app.ui.view.projectlist.ProjectListScreen
import com.costproject.app.ui.viewmodel.AuthViewModel
import com.costproject.app.ui.viewmodel.ProjectDetailViewModel
import com.costproject.app.ui.viewmodel.ProjectListViewModel

/**
 * The navigation graph, gated on the session.
 *
 * Screens never navigate to or from login themselves. Signing in, logging out,
 * and a session dying because its refresh token was rejected all surface as
 * AuthRepository.hasSession changing, and this one effect reacts to it.
 */
@Composable
fun AppNavigation(
    container: AppContainer,
    navController: NavHostController = rememberNavController()
) {
    val hasSession by container.authRepository.hasSession.collectAsState()

    val startDestination = remember {
        if (container.authRepository.hasSession.value) Screen.ProjectList.route else Screen.Auth.route
    }

    // The route at the bottom of the back stack. Every session change below
    // replaces the whole stack with a single entry, so this is always known -
    // which lets it be cleared with popUpTo(route). The alternative,
    // popUpTo(navController.graph.id), compiles on Android but NavDestination.id
    // is not part of the multiplatform API, so it would break the iOS build.
    val rootRoute = remember { mutableStateOf(startDestination) }

    LaunchedEffect(hasSession) {
        val onAuthScreen = navController.currentDestination?.route == Screen.Auth.route
        val target = when {
            hasSession && onAuthScreen -> Screen.ProjectList.route
            !hasSession && !onAuthScreen -> Screen.Auth.route
            else -> return@LaunchedEffect
        }
        navController.navigate(target) {
            // Clear everything behind: after logout, back must not reveal the
            // previous account's projects; after login, back must not return to
            // the login form.
            popUpTo(rootRoute.value) { inclusive = true }
            launchSingleTop = true
        }
        rootRoute.value = target
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Auth.route) {
            val viewModel: AuthViewModel = viewModel(factory = ViewModelFactory.auth(container))
            AuthScreen(viewModel)
        }

        composable(Screen.ProjectList.route) {
            val viewModel: ProjectListViewModel = viewModel(factory = ViewModelFactory.projectList(container))
            ProjectListScreen(
                viewModel = viewModel,
                onOpenProject = { id -> navController.navigate(Screen.ProjectDetail.createRoute(id)) }
            )
        }

        composable(
            route = Screen.ProjectDetail.route,
            arguments = listOf(navArgument(Screen.ARG_PROJECT_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString(Screen.ARG_PROJECT_ID) ?: return@composable
            // Scoped to this back-stack entry, so each opened project gets its own ViewModel.
            val viewModel: ProjectDetailViewModel =
                viewModel(factory = ViewModelFactory.projectDetail(container, projectId))
            ProjectDetailScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
