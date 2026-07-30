package com.pearsonmedia.lastlogged.service

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import com.pearsonmedia.lastlogged.R

@Singleton
class BiometricService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorageService: SecureStorageService
) {
    companion object {
        private const val TAG = "BiometricService"
    }

    private val _isUnlocked = MutableStateFlow(true)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    val isEnabled: Boolean
        get() = secureStorageService.getBiometricEnabled()

    val isBiometricAvailable: Boolean
        get() {
            val biometricManager = BiometricManager.from(context)
            return biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            ) == BiometricManager.BIOMETRIC_SUCCESS
        }

    val canUseBiometricOrDeviceCredential: Boolean
        get() {
            val biometricManager = BiometricManager.from(context)
            return biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            ) == BiometricManager.BIOMETRIC_SUCCESS
        }

    val biometricTypeName: String
        get() {
            // Android doesn't distinguish face/fingerprint easily via BiometricManager
            // Return generic name
            return if (isBiometricAvailable) "Biometric" else "Device Credential"
        }

    fun setEnabled(enabled: Boolean) {
        secureStorageService.setBiometricEnabled(enabled)
        if (!enabled) {
            _isUnlocked.value = true
        }
    }

    fun lock() {
        if (isEnabled) {
            _isUnlocked.value = false
        }
    }

    fun unlock() {
        _isUnlocked.value = true
    }

    fun authenticate(activity: FragmentActivity, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.d(TAG, "Authentication succeeded")
                _isUnlocked.value = true
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.w(TAG, "Authentication error: $errorCode - $errString")
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED -> {
                        // User cancelled — don't show error, just stay locked
                        onFailure("")
                    }
                    BiometricPrompt.ERROR_LOCKOUT,
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                        onFailure(context.getString(R.string.biometric_too_many_attempts))
                    }
                    else -> {
                        onFailure(errString.toString())
                    }
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.d(TAG, "Authentication failed (biometric not recognized)")
                // Don't dismiss — user can retry
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.biometric_prompt_title))
            .setSubtitle(context.getString(R.string.biometric_prompt_subtitle))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
