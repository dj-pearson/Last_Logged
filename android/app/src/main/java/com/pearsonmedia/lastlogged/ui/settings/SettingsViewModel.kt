package com.pearsonmedia.lastlogged.ui.settings

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pearsonmedia.lastlogged.data.repository.TrackerRepository
import com.pearsonmedia.lastlogged.service.BiometricService
import com.pearsonmedia.lastlogged.service.SecureStorageService
import com.pearsonmedia.lastlogged.service.SupabaseService
import com.pearsonmedia.lastlogged.util.DataExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
    /** Set when an export file is ready; the screen consumes it to open the share sheet. */
    val exportedFileUri: Uri? = null,
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

    private companion object {
        const val EXPORT_DIR = "exports"
    }

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

    /**
     * Writes every local tracker, category, and completion log to a JSON file in
     * the app cache and hands it to the system share sheet.
     *
     * Emits [SettingsUiState.exportedFileUri] rather than launching the chooser
     * itself — starting an Activity needs a UI context, which the ViewModel
     * should not hold.
     */
    fun exportData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, error = null)
            try {
                val uri = withContext(Dispatchers.IO) {
                    val (categories, items, logs) = repository.exportSnapshot()
                    val json = DataExporter.buildExportJson(categories, items, logs)

                    val exportDir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
                    // Only the newest export is kept; stale copies of a user's
                    // full history should not linger in the cache.
                    exportDir.listFiles()?.forEach { it.delete() }

                    val file = File(exportDir, DataExporter.fileName())
                    file.writeText(json)

                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                }

                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportedFileUri = uri
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    error = "Export failed: ${e.message}"
                )
            }
        }
    }

    /** Call once the share sheet has been launched so it is not shown twice. */
    fun clearExportedFile() {
        _uiState.value = _uiState.value.copy(exportedFileUri = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
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
