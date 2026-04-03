package com.pearsonmedia.lastlogged.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthMode { SIGN_IN, SIGN_UP, FORGOT_PASSWORD, EMAIL_CONFIRMATION }

data class AuthUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasAgreedToTerms: Boolean = false,
    val failedAttempts: Int = 0,
    val cooldownSeconds: Int = 0,
    val isSignedIn: Boolean = false,
    val currentUserEmail: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var cooldownJob: Job? = null

    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    val isEmailValid: Boolean
        get() = _uiState.value.email.isEmpty() || emailRegex.matches(_uiState.value.email)

    val isPasswordValid: Boolean
        get() {
            val pw = _uiState.value.password
            if (pw.isEmpty()) return true
            return pw.length >= 8
                && pw.any { it.isUpperCase() }
                && pw.any { it.isLowerCase() }
                && pw.any { it.isDigit() }
        }

    val passwordValidationMessages: List<String>
        get() {
            val pw = _uiState.value.password
            if (pw.isEmpty()) return emptyList()
            val messages = mutableListOf<String>()
            if (pw.length < 8) messages.add("At least 8 characters")
            if (!pw.any { it.isUpperCase() }) messages.add("One uppercase letter")
            if (!pw.any { it.isLowerCase() }) messages.add("One lowercase letter")
            if (!pw.any { it.isDigit() }) messages.add("One number")
            return messages
        }

    val canSubmit: Boolean
        get() {
            val state = _uiState.value
            if (state.cooldownSeconds > 0) return false
            if (state.isLoading) return false
            if (!emailRegex.matches(state.email)) return false

            return when (state.mode) {
                AuthMode.SIGN_IN -> state.password.isNotEmpty()
                AuthMode.SIGN_UP -> isPasswordValid && state.password.isNotEmpty() && state.hasAgreedToTerms
                AuthMode.FORGOT_PASSWORD -> true
                AuthMode.EMAIL_CONFIRMATION -> false
            }
        }

    fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(email = email, error = null)
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }

    fun toggleTermsAgreement() {
        _uiState.value = _uiState.value.copy(hasAgreedToTerms = !_uiState.value.hasAgreedToTerms)
    }

    fun setMode(mode: AuthMode) {
        _uiState.value = _uiState.value.copy(mode = mode, error = null, successMessage = null)
    }

    fun signIn() {
        if (!canSubmit) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // TODO: Integrate with SupabaseService
                // For now, simulate auth
                delay(1000)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSignedIn = true,
                    currentUserEmail = _uiState.value.email,
                    failedAttempts = 0
                )
            } catch (e: Exception) {
                handleAuthError(e)
            }
        }
    }

    fun signUp() {
        if (!canSubmit) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // TODO: Integrate with SupabaseService
                delay(1000)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    mode = AuthMode.EMAIL_CONFIRMATION
                )
            } catch (e: Exception) {
                handleAuthError(e)
            }
        }
    }

    fun resetPassword() {
        if (!canSubmit) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // TODO: Integrate with SupabaseService
                delay(1000)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = "Check your email for a reset link"
                )
            } catch (e: Exception) {
                handleAuthError(e)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _uiState.value = AuthUiState()
        }
    }

    private fun handleAuthError(error: Exception) {
        val currentState = _uiState.value
        val newAttempts = currentState.failedAttempts + 1

        _uiState.value = currentState.copy(
            isLoading = false,
            error = sanitizeAuthError(error.message ?: "Unknown error"),
            failedAttempts = newAttempts
        )

        if (newAttempts >= 5) {
            startCooldown()
        }
    }

    private fun startCooldown() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cooldownSeconds = 30)
            repeat(30) {
                delay(1000)
                _uiState.value = _uiState.value.copy(
                    cooldownSeconds = _uiState.value.cooldownSeconds - 1
                )
            }
            _uiState.value = _uiState.value.copy(cooldownSeconds = 0, failedAttempts = 0)
        }
    }

    private fun sanitizeAuthError(rawError: String): String {
        return when {
            rawError.contains("invalid", ignoreCase = true) ||
            rawError.contains("credentials", ignoreCase = true) ->
                "Invalid email or password. Please try again."
            rawError.contains("already", ignoreCase = true) ||
            rawError.contains("registered", ignoreCase = true) ->
                "This email is already registered. Try signing in instead."
            rawError.contains("network", ignoreCase = true) ||
            rawError.contains("connection", ignoreCase = true) ->
                "Network error. Please check your connection and try again."
            rawError.contains("rate", ignoreCase = true) ||
            rawError.contains("limit", ignoreCase = true) ->
                "Too many requests. Please wait and try again."
            else -> "Something went wrong. Please try again."
        }
    }
}
