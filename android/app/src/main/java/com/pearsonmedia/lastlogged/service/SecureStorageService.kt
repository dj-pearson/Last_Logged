package com.pearsonmedia.lastlogged.service

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorageService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SecureStorageService"
        private const val PREFS_NAME = "lastlogged_secure_prefs"
        private const val KEY_IS_PREMIUM = "is_premium"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_lock_enabled"
        private const val KEY_SUBSCRIPTION_TIER = "subscription_tier"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_REMINDERS_ENABLED = "reminders_enabled"
        private const val KEY_REMINDER_HOUR = "default_reminder_hour"
        private const val KEY_REMINDER_MINUTE = "default_reminder_minute"
        private const val KEY_SUCCESS_SOUND_ENABLED = "success_sound_enabled"
    }

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private fun createEncryptedPrefs() = EncryptedSharedPreferences.create(
        PREFS_NAME,
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val prefs by lazy {
        try {
            createEncryptedPrefs()
        } catch (e: Exception) {
            // Typically an undecryptable file: the AndroidKeyStore master key is
            // hardware-bound and does not survive a restore or a keystore reset.
            // Drop the unreadable file and re-create rather than falling straight
            // through to PLAINTEXT prefs under the same name — the values here
            // are auth and subscription state.
            Log.w(TAG, "EncryptedSharedPreferences unreadable, recreating: ${e.message}")
            context.deleteSharedPreferences(PREFS_NAME)

            try {
                createEncryptedPrefs()
            } catch (retry: Exception) {
                Log.e(TAG, "Encrypted prefs unavailable on this device: ${retry.message}")
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        }
    }

    // --- Premium Status ---

    fun getIsPremium(): Boolean = prefs.getBoolean(KEY_IS_PREMIUM, false)

    fun setIsPremium(isPremium: Boolean) {
        prefs.edit().putBoolean(KEY_IS_PREMIUM, isPremium).apply()
    }

    // --- Subscription Tier ---

    fun getSubscriptionTier(): String = prefs.getString(KEY_SUBSCRIPTION_TIER, "free") ?: "free"

    fun setSubscriptionTier(tier: String) {
        prefs.edit().putString(KEY_SUBSCRIPTION_TIER, tier).apply()
    }

    // --- Sync Timestamp ---

    fun getLastSyncTimestamp(): Long = prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0)

    fun setLastSyncTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_TIMESTAMP, timestamp).apply()
    }

    // --- Biometric Lock ---

    fun getBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    // --- Onboarding ---

    fun getOnboardingCompleted(): Boolean = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    // --- Notification Preferences ---

    fun getRemindersEnabled(): Boolean = prefs.getBoolean(KEY_REMINDERS_ENABLED, true)

    fun setRemindersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDERS_ENABLED, enabled).apply()
    }

    fun getDefaultReminderHour(): Int = prefs.getInt(KEY_REMINDER_HOUR, 9)

    fun setDefaultReminderHour(hour: Int) {
        prefs.edit().putInt(KEY_REMINDER_HOUR, hour).apply()
    }

    fun getDefaultReminderMinute(): Int = prefs.getInt(KEY_REMINDER_MINUTE, 0)

    fun setDefaultReminderMinute(minute: Int) {
        prefs.edit().putInt(KEY_REMINDER_MINUTE, minute).apply()
    }

    // Opt-in success chime when logging a completion (off by default).
    fun getSuccessSoundEnabled(): Boolean = prefs.getBoolean(KEY_SUCCESS_SOUND_ENABLED, false)

    fun setSuccessSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SUCCESS_SOUND_ENABLED, enabled).apply()
    }

    // --- Bulk Clear ---

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    // --- Generic Access ---

    fun getString(key: String, default: String? = null): String? = prefs.getString(key, default)

    fun setString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getLong(key: String, default: Long = 0): Long = prefs.getLong(key, default)

    fun setLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean = prefs.getBoolean(key, default)

    fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}
