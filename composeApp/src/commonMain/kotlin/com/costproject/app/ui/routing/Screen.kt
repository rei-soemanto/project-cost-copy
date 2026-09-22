package com.costproject.app.ui.routing

/**
 * Navigation destinations, following the reference project's convention:
 * a sealed class of route constants with a createRoute() helper on any
 * destination that takes arguments.
 */
sealed class Screen(val route: String) {

    /** Login / register. Wired up in Phase 7 once AuthViewModel exists. */
    data object Auth : Screen("auth")

    /** Project list, the start destination while there is no auth gate. */
    data object ProjectList : Screen("projects")

    data object ProjectDetail : Screen("project_detail/{$ARG_PROJECT_ID}") {
        fun createRoute(projectId: String) = "project_detail/$projectId"
    }

    companion object {
        const val ARG_PROJECT_ID = "projectId"
    }
}
