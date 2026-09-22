package com.costproject.app

import androidx.compose.runtime.Composable
import com.costproject.app.ui.routing.AppNavigation
import com.costproject.app.ui.theme.CostProjectTheme

@Composable
fun App() {
    CostProjectTheme {
        AppNavigation()
    }
}
