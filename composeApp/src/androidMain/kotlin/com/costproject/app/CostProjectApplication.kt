package com.costproject.app

import android.app.Application
import com.costproject.app.data.container.AppContainer

/**
 * Holds the one AppContainer for the process. Created here rather than in the
 * Activity so it survives configuration changes such as rotation, which would
 * otherwise rebuild the HTTP clients and split the session state.
 */
class CostProjectApplication : Application() {
    val container: AppContainer by lazy { AppContainer(enableHttpLogging = BuildConfig.DEBUG) }
}
