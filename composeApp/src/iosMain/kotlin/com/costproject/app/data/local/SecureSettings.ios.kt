package com.costproject.app.data.local

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.Settings

/** Tokens live in the iOS Keychain rather than NSUserDefaults. */
@OptIn(ExperimentalSettingsImplementation::class)
actual fun createSecureSettings(): Settings = KeychainSettings(service = "com.costproject.app.auth")
