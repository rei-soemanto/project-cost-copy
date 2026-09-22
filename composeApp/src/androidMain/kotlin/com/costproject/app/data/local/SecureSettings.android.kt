package com.costproject.app.data.local

import com.russhwolf.settings.Settings

/** App-private SharedPreferences, sandboxed to this app (the same level the reference project uses via DataStore). */
actual fun createSecureSettings(): Settings = Settings()
