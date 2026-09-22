package com.costproject.app.data.repository

import com.costproject.app.data.dto.AuthResponseDto
import com.costproject.app.data.dto.LoginRequest
import com.costproject.app.data.dto.RegisterRequest
import com.costproject.app.data.local.AuthTokens
import com.costproject.app.data.local.TokenStorage
import com.costproject.app.data.remote.AuthApiService
import com.costproject.app.data.remote.safeApiCall
import com.costproject.app.domain.model.User
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.flow.StateFlow

/** The signed-in session. ViewModels depend on this interface, so tests can fake it. */
interface AuthRepository {
    /** False once the session ends - by logout or by a rejected refresh. */
    val hasSession: StateFlow<Boolean>
    val currentUser: User?
    suspend fun register(fullName: String, email: String, password: String): Result<User>
    suspend fun login(email: String, password: String): Result<User>
    fun logout()
}

/**
 * Server-backed [AuthRepository].
 *
 * @param onSessionChanged called after login and logout so the authenticated
 *   HTTP client drops the tokens it has cached in memory.
 */
class DefaultAuthRepository(
    private val api: AuthApiService,
    private val tokenStorage: TokenStorage,
    private val onSessionChanged: () -> Unit
) : AuthRepository {
    override val hasSession: StateFlow<Boolean> get() = tokenStorage.hasSession

    override val currentUser: User? get() = tokenStorage.loadUser()

    override suspend fun register(fullName: String, email: String, password: String): Result<User> =
        safeApiCall { api.register(RegisterRequest(fullName.trim(), email.trim(), password)) }.map(::startSession)

    override suspend fun login(email: String, password: String): Result<User> =
        safeApiCall { api.login(LoginRequest(email.trim(), password)) }.map(::startSession)

    override fun logout() {
        tokenStorage.clear()
        onSessionChanged()
    }

    /**
     * Exchanges a refresh token for a new pair. Invoked by the authenticated
     * HTTP client when an access token has expired.
     *
     * - Success: stores and returns the new pair.
     * - 4xx (token invalid, expired, or reused): the session is over. Storage is
     *   cleared, which flips [hasSession] and sends the UI to the login screen.
     * - Network failure: rethrown, not treated as a logout. The request that
     *   needed the refresh fails with a network error and the user stays signed
     *   in, so a dropped connection never costs them their session.
     */
    suspend fun refreshSession(refreshToken: String): AuthTokens? {
        val pair = try {
            api.refresh(refreshToken)
        } catch (_: ClientRequestException) {
            tokenStorage.clear()
            return null
        }
        return AuthTokens(pair.accessToken, pair.refreshToken).also(tokenStorage::saveTokens)
    }

    private fun startSession(response: AuthResponseDto): User {
        val user = User(response.user.id, response.user.email, response.user.fullName)
        tokenStorage.saveSession(AuthTokens(response.accessToken, response.refreshToken), user)
        onSessionChanged()
        return user
    }
}
