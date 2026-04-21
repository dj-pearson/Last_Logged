package com.pearsonmedia.lastlogged.service

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushTokenService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabaseService: SupabaseService,
    private val secureStorageService: SecureStorageService
) {
    companion object {
        private const val TAG = "PushTokenService"
        private const val KEY_PENDING_FCM_TOKEN = "pending_fcm_token"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun initialize() {
        // Firebase may not be configured if google-services.json is a placeholder.
        // Swallow any failure so the app still launches in that case.
        val app = try {
            FirebaseApp.getInstance()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Firebase not initialized — skipping push token fetch")
            return
        }
        if (app == null) return

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task: Task<String> ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Failed to fetch FCM token: ${task.exception?.message}")
                return@addOnCompleteListener
            }
            val token = task.result ?: return@addOnCompleteListener
            onNewToken(token)
        }
    }

    fun onNewToken(token: String) {
        secureStorageService.setString(KEY_PENDING_FCM_TOKEN, token)
        uploadPendingToken()
    }

    fun uploadPendingToken() {
        val token = secureStorageService.getString(KEY_PENDING_FCM_TOKEN) ?: return
        scope.launch {
            supabaseService.registerDeviceToken(token, android.os.Build.MODEL)
        }
    }
}
