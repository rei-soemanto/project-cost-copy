package com.costproject.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.costproject.app.data.DataError
import com.costproject.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { Login, Register }

/**
 * A form rather than a loaded resource, so this is a data class of field values
 * plus submission status, not a Loading / Success / Error sealed class.
 */
data class AuthUiState(
    val mode: AuthMode = AuthMode.Login,
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    /** Form-level message, e.g. wrong credentials or no connection. */
    val errorMessage: String? = null,
    /** Per-field messages keyed by "fullName", "email", "password". */
    val fieldErrors: Map<String, String> = emptyMap()
)

/**
 * Login and registration.
 *
 * Success does not navigate from here: a successful sign-in flips
 * AuthRepository.hasSession, and the navigation graph reacts to that. One signal
 * drives both signing in and being signed out by an expired session.
 */
class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onFullNameChange(value: String) = _uiState.update { it.withField("fullName") { copy(fullName = value) } }

    fun onEmailChange(value: String) = _uiState.update { it.withField("email") { copy(email = value) } }

    fun onPasswordChange(value: String) = _uiState.update { it.withField("password") { copy(password = value) } }

    fun toggleMode() = _uiState.update {
        it.copy(
            mode = if (it.mode == AuthMode.Login) AuthMode.Register else AuthMode.Login,
            errorMessage = null,
            fieldErrors = emptyMap()
        )
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return

        val fieldErrors = validate(state)
        if (fieldErrors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = fieldErrors, errorMessage = null) }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            val result = when (state.mode) {
                AuthMode.Login -> authRepository.login(state.email, state.password)
                AuthMode.Register -> authRepository.register(state.fullName, state.email, state.password)
            }
            result
                .onSuccess { _uiState.update { it.copy(isSubmitting = false, password = "") } }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = error.message,
                            fieldErrors = (error as? DataError.Validation)?.fieldErrors.orEmpty()
                        )
                    }
                }
        }
    }

    /**
     * Instant feedback for the obvious mistakes. The server validates again; this
     * only saves a round trip and gives Indonesian messages.
     */
    private fun validate(state: AuthUiState): Map<String, String> = buildMap {
        if (state.mode == AuthMode.Register && state.fullName.isBlank()) {
            put("fullName", "Nama wajib diisi")
        }
        if (state.email.isBlank()) {
            put("email", "Email wajib diisi")
        } else if (!EMAIL_SHAPE.matches(state.email.trim())) {
            put("email", "Format email tidak valid")
        }
        if (state.password.isEmpty()) {
            put("password", "Kata sandi wajib diisi")
        } else if (state.mode == AuthMode.Register && state.password.length < MIN_PASSWORD_LENGTH) {
            put("password", "Kata sandi minimal $MIN_PASSWORD_LENGTH karakter")
        }
    }

    /** Applies a field edit and clears that field's error, since the user is fixing it. */
    private inline fun AuthUiState.withField(name: String, edit: AuthUiState.() -> AuthUiState) =
        edit().copy(fieldErrors = fieldErrors - name, errorMessage = null)

    private companion object {
        const val MIN_PASSWORD_LENGTH = 8
        val EMAIL_SHAPE = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
