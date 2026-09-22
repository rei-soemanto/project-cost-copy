package com.costproject.app.ui.routing

/**
 * Navigation destinations, following the reference project's convention:
 * a sealed class of route constants with a createRoute() helper on any
 * destination that takes arguments.
 */
sealed class Screen(val route: String) {

    /** Login / register. The start destination when there is no session. */
    data object Auth : Screen("auth")

    /** Project list. The start destination when signed in. */
    data object ProjectList : Screen("projects")

    data object ProjectDetail : Screen("project_detail/{$ARG_PROJECT_ID}") {
        fun createRoute(projectId: String) = "project_detail/$projectId"
    }

    companion object {
        const val ARG_PROJECT_ID = "projectId"
    }
}
