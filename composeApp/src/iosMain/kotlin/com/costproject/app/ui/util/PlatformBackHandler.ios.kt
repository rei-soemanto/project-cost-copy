package com.costproject.app.ui.util

import androidx.compose.runtime.Composable

/** iOS has no system back button; the on-screen back arrow covers leaving the screen. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
