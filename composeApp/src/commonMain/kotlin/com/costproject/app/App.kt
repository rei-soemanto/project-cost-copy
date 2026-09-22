package com.costproject.app

import androidx.compose.runtime.Composable
import com.costproject.app.data.container.AppContainer
import com.costproject.app.ui.routing.AppNavigation
import com.costproject.app.ui.theme.CostProjectTheme

/** Root composable, shared by Android and iOS. Each platform supplies its single [AppContainer]. */
@Composable
fun App(container: AppContainer) {
    CostProjectTheme {
        AppNavigation(container)
    }
}
