package com.costproject.app.ui.util

import androidx.compose.runtime.Composable

/**
 * Intercepts the system back gesture. Android only; iOS has no system back
 * button, so its implementation does nothing.
 *
 * Needed in common code because Compose Multiplatform 1.7 has no shared
 * BackHandler, and without one the navigation host pops the screen before the
 * editor can save.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
