package com.pearsonmedia.lastlogged.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pearsonmedia.lastlogged.data.repository.TrackerRepository
import com.pearsonmedia.lastlogged.service.BiometricService
import com.pearsonmedia.lastlogged.service.SecureStorageService
import com.pearsonmedia.lastlogged.service.SupabaseService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isSignedIn: Boolean = false,
    val userEmail: String? = null,
    val subscriptionTier: String = "Free",
    val isPremium: Boolean = false,
    val remindersEnabled: Boolean = true,
    val defaultReminderHour: Int = 9,
    val defaultReminderMinute: Int = 0,
    val biometricLockEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val successSoundEnabled: Boolean = false,
    val appVersion: String = "",
    val appBuildNumber: Int = 0,
    val isExporting: Boolean = false,
    val isClearing: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val error: String? = null,
    val showDeleteConfirmation: Boolean = false,
    val deleteConfirmText: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: TrackerRepository,
    private val supabaseService: SupabaseService,
    private val secureStorageService: SecureStorageService,
    private val biometricService: BiometricService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        observeAuthState()
    }

    private fun loadSettings() {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            _uiState.value = _uiState.value.copy(
                appVersion = packageInfo.versionName ?: "1.0.0",
                appBuildNumber = packageInfo.longVersionCode.toInt(),
                remindersEnabled = secureStorageService.getRemindersEnabled(),
                defaultReminderHour = secureStorageService.getDefaultReminderHour(),
                defaultReminderMinute = secureStorageService.getDefaultReminderMinute(),
                biometricLockEnabled = biometricService.isEnabled,
                biometricAvailable = biometricService.canUseBiometricOrDeviceCredential,
                successSoundEnabled = secureStorageService.getSuccessSoundEnabled(),
                isPremium = secureStorageService.getIsPremium(),
                subscriptionTier = secureStorageService.getSubscriptionTier()
                    .replaceFirstChar { it.uppercase() }
            )
        } catch (_: Exception) { }
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            supabaseService.isSignedIn.collect { signedIn ->
                _uiState.value = _uiState.value.copy(
                    isSignedIn = signedIn,
                    userEmail = supabaseService.currentUserEmail.value
                )
            }
        }
    }

    fun toggleReminders(enabled: Boolean) {
        secureStorageService.setRemindersEnabled(enabled)
        _uiState.value = _uiState.value.copy(remindersEnabled = enabled)
    }

    fun setDefaultReminderTime(hour: Int, minute: Int) {
        secureStorageService.setDefaultReminderHour(hour)
        secureStorageService.setDefaultReminderMinute(minute)
        _uiState.value = _uiState.value.copy(
            defaultReminderHour = hour,
            defaultReminderMinute = minute
        )
    }

    fun toggleBiometricLock(enabled: Boolean) {
        biometricService.setEnabled(enabled)
        _uiState.value = _uiState.value.copy(biometricLockEnabled = enabled)
    }

    fun toggleSuccessSound(enabled: Boolean) {
        secureStorageService.setSuccessSoundEnabled(enabled)
        _uiState.value = _uiState.value.copy(successSoundEnabled = enabled)
    }

    fun exportData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true)
            try {
                // TODO: Implement JSON export to file
                _uiState.value = _uiState.value.copy(isExporting = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    error = "Export failed: ${e.message}"
                )
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isClearing = true)
            try {
                repository.clearAllData()
                _uiState.value = _uiState.value.copy(isClearing = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isClearing = false,
                    error = "Clear data failed: ${e.message}"
                )
            }
        }
    }

    fun showDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(showDeleteConfirmation = true, deleteConfirmText = "")
    }

    fun hideDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(showDeleteConfirmation = false, deleteConfirmText = "")
    }

    fun updateDeleteConfirmText(text: String) {
        _uiState.value = _uiState.value.copy(deleteConfirmText = text)
    }

    fun deleteAccount() {
        if (_uiState.value.deleteConfirmText != "DELETE") return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeletingAccount = true)
            try {
                repository.clearAllData()
                supabaseService.deleteAccount()
                secureStorageService.clearAll()
                _uiState.value = _uiState.value.copy(
                    isDeletingAccount = false,
                    showDeleteConfirmation = false,
                    isSignedIn = false,
                    userEmail = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDeletingAccount = false,
                    error = "Account deletion failed: ${e.message}"
                )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                supabaseService.signOut()
                _uiState.value = _uiState.value.copy(isSignedIn = false, userEmail = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Sign out failed: ${e.message}")
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
