package com.costproject.app.data.local

import com.costproject.app.domain.model.User
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AuthTokens(val accessToken: String, val refreshToken: String)

/**
 * Persists the signed-in session. An interface so tests can substitute an
 * in-memory version without touching platform storage.
 */
interface TokenStorage {
    /**
     * True while a session exists. Becomes false when the session is cleared -
     * including when a token refresh is rejected - which is how the UI learns it
     * must send the user back to the login screen.
     */
    val hasSession: StateFlow<Boolean>

    fun loadTokens(): AuthTokens?
    fun loadUser(): User?
    fun saveTokens(tokens: AuthTokens)
    fun saveSession(tokens: AuthTokens, user: User)
    fun clear()
}

class SettingsTokenStorage(private val settings: Settings) : TokenStorage {

    private val _hasSession = MutableStateFlow(loadTokens() != null)
    override val hasSession: StateFlow<Boolean> = _hasSession.asStateFlow()

    override fun loadTokens(): AuthTokens? {
        val access = settings.getStringOrNull(KEY_ACCESS) ?: return null
        val refresh = settings.getStringOrNull(KEY_REFRESH) ?: return null
        return AuthTokens(access, refresh)
    }

    override fun loadUser(): User? {
        val id = settings.getStringOrNull(KEY_USER_ID) ?: return null
        return User(
            id = id,
            email = settings.getString(KEY_USER_EMAIL, ""),
            fullName = settings.getString(KEY_USER_NAME, "")
        )
    }

    override fun saveTokens(tokens: AuthTokens) {
        settings.putString(KEY_ACCESS, tokens.accessToken)
        settings.putString(KEY_REFRESH, tokens.refreshToken)
        _hasSession.value = true
    }

    override fun saveSession(tokens: AuthTokens, user: User) {
        settings.putString(KEY_USER_ID, user.id)
        settings.putString(KEY_USER_EMAIL, user.email)
        settings.putString(KEY_USER_NAME, user.fullName)
        saveTokens(tokens)
    }

    override fun clear() {
        listOf(KEY_ACCESS, KEY_REFRESH, KEY_USER_ID, KEY_USER_EMAIL, KEY_USER_NAME).forEach(settings::remove)
        _hasSession.value = false
    }

    private companion object {
        const val KEY_ACCESS = "auth.accessToken"
        const val KEY_REFRESH = "auth.refreshToken"
        const val KEY_USER_ID = "auth.user.id"
        const val KEY_USER_EMAIL = "auth.user.email"
        const val KEY_USER_NAME = "auth.user.fullName"
    }
}
