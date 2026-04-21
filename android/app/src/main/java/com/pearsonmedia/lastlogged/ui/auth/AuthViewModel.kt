package com.pearsonmedia.lastlogged.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pearsonmedia.lastlogged.service.GoogleSignInException
import com.pearsonmedia.lastlogged.service.GoogleSignInService
import com.pearsonmedia.lastlogged.service.PushTokenService
import com.pearsonmedia.lastlogged.service.RevenueCatService
import com.pearsonmedia.lastlogged.service.SupabaseService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
class AuthViewModel @Inject constructor(
    private val supabaseService: SupabaseService,
    private val revenueCatService: RevenueCatService,
    private val pushTokenService: PushTokenService,
    private val googleSignInService: GoogleSignInService
) : ViewModel() {

    val isGoogleSignInAvailable: Boolean
        get() = googleSignInService.isConfigured

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var cooldownJob: Job? = null

    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    init {
        viewModelScope.launch {
            combine(supabaseService.isSignedIn, supabaseService.currentUserEmail) { signedIn, email ->
                signedIn to email
            }.collect { (signedIn, email) ->
                _uiState.value = _uiState.value.copy(
                    isSignedIn = signedIn,
                    currentUserEmail = email ?: _uiState.value.currentUserEmail
                )
                if (signedIn) {
                    pushTokenService.uploadPendingToken()
                }
            }
        }
    }

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
        val email = _uiState.value.email
        val password = _uiState.value.password
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                supabaseService.signInEmail(email, password)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    failedAttempts = 0,
                    password = ""
                )
            } catch (e: Exception) {
                handleAuthError(e)
            }
        }
    }

    fun signUp() {
        if (!canSubmit) return
        val email = _uiState.value.email
        val password = _uiState.value.password
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                supabaseService.signUpEmail(email, password)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    mode = AuthMode.EMAIL_CONFIRMATION,
                    password = ""
                )
            } catch (e: Exception) {
                handleAuthError(e)
            }
        }
    }

    fun resetPassword() {
        if (!canSubmit) return
        val email = _uiState.value.email
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                supabaseService.resetPassword(email)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = "Check your email for a reset link"
                )
            } catch (e: Exception) {
                handleAuthError(e)
            }
        }
    }

    fun resendConfirmation() {
        val email = _uiState.value.email
        if (email.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                supabaseService.resendConfirmation(email)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = "Confirmation email sent"
                )
            } catch (e: Exception) {
                handleAuthError(e)
            }
        }
    }

    fun continueWithGoogle(activityContext: Context) {
        if (!isGoogleSignInAvailable) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val idToken = googleSignInService.getGoogleIdToken(activityContext)
                supabaseService.signInWithGoogle(idToken)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    failedAttempts = 0
                )
            } catch (e: GoogleSignInException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Google Sign-In failed. Please try again."
                )
            } catch (e: Exception) {
                handleAuthError(e)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                supabaseService.signOut()
            } catch (_: Exception) { }
            revenueCatService.onSignOut()
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
