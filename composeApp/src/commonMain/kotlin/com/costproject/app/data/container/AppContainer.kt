package com.costproject.app.data.container

import com.costproject.app.data.local.LegacyProjectStore
import com.costproject.app.data.local.SettingsTokenStorage
import com.costproject.app.data.local.TokenStorage
import com.costproject.app.data.local.createSecureSettings
import com.costproject.app.data.remote.AuthApiService
import com.costproject.app.data.remote.ProjectApiService
import com.costproject.app.data.remote.clearCachedBearerTokens
import com.costproject.app.data.remote.createAuthenticatedHttpClient
import com.costproject.app.data.remote.createHttpEngine
import com.costproject.app.data.remote.createPublicHttpClient
import com.costproject.app.data.remote.defaultApiBaseUrl
import com.costproject.app.data.repository.AuthRepository
import com.costproject.app.data.repository.DefaultAuthRepository
import com.costproject.app.data.repository.DefaultProjectRepository
import com.costproject.app.data.repository.ProjectRepository
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency injection, following the reference project: builds the
 * HTTP clients, services and repositories once and exposes the repositories.
 *
 * Create exactly one per process so every screen shares the same session and
 * HTTP connection pool.
 */
class AppContainer(
    engine: HttpClientEngine = createHttpEngine(),
    baseUrl: String = defaultApiBaseUrl,
    secureSettings: Settings = createSecureSettings(),
    legacySettings: Settings = Settings(),
    enableHttpLogging: Boolean = false,
    httpMaxRetries: Int = 2
) {
    val tokenStorage: TokenStorage = SettingsTokenStorage(secureSettings)

    /**
     * Outlives every screen. A save still in flight when the user navigates away
     * finishes here instead of being cancelled along with the screen's
     * ViewModel. SupervisorJob so one failed save cannot cancel the others.
     */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val authApi = AuthApiService(createPublicHttpClient(engine, baseUrl, enableHttpLogging))

    // The authenticated client and the auth repository refer to each other: the
    // client asks the repository to refresh, the repository clears the client's
    // token cache. Both are lazy and each touches the other only when invoked,
    // after construction, so there is no initialisation cycle.
    private val authenticatedClient: HttpClient by lazy {
        createAuthenticatedHttpClient(
            engine = engine,
            baseUrl = baseUrl,
            tokenStorage = tokenStorage,
            refreshSession = { defaultAuthRepository.refreshSession(it) },
            enableLogging = enableHttpLogging,
            maxRetries = httpMaxRetries
        )
    }

    private val defaultAuthRepository: DefaultAuthRepository by lazy {
        DefaultAuthRepository(authApi, tokenStorage, onSessionChanged = { authenticatedClient.clearCachedBearerTokens() })
    }

    val authRepository: AuthRepository get() = defaultAuthRepository

    val projectRepository: ProjectRepository by lazy {
        DefaultProjectRepository(ProjectApiService(authenticatedClient), LegacyProjectStore(legacySettings))
    }
}
