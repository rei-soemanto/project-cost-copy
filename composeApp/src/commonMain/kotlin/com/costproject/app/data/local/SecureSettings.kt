package com.costproject.app.data.local

import com.russhwolf.settings.Settings

/**
 * Settings store for credentials. The one platform boundary in the data layer:
 * the Keychain on iOS, app-private SharedPreferences on Android.
 */
expect fun createSecureSettings(): Settings
