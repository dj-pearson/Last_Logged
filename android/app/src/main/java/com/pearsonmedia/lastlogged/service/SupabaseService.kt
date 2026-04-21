package com.pearsonmedia.lastlogged.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.pearsonmedia.lastlogged.BuildConfig
import com.pearsonmedia.lastlogged.data.local.dao.CompletionLogDao
import com.pearsonmedia.lastlogged.data.local.dao.TrackerCategoryDao
import com.pearsonmedia.lastlogged.data.local.dao.TrackerItemDao
import com.pearsonmedia.lastlogged.data.local.entity.SyncStatus
import com.pearsonmedia.lastlogged.data.local.entity.TrackerItem
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.Google
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.providers.builtin.IDToken
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class TrackerItemRow(
    val id: String,
    val name: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("reminder_interval_days") val reminderIntervalDays: Int = 30,
    @SerialName("last_completed_at") val lastCompletedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("icon_name") val iconName: String = "checklist",
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class TrackerCategoryRow(
    val id: String,
    val name: String,
    @SerialName("icon_name") val iconName: String = "category",
    @SerialName("color_hex") val colorHex: String = "#4f46e5",
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_default") val isDefault: Boolean = false
)

@Serializable
data class CompletionLogRow(
    val id: String,
    @SerialName("tracker_item_id") val trackerItemId: String,
    @SerialName("completed_at") val completedAt: String? = null,
    val notes: String? = null
)

@Singleton
class SupabaseService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackerItemDao: TrackerItemDao,
    private val completionLogDao: CompletionLogDao,
    private val trackerCategoryDao: TrackerCategoryDao,
    private val secureStorageService: SecureStorageService
) {
    companion object {
        private const val TAG = "SupabaseService"
        private const val SYNC_DEBOUNCE_MS = 2000L
        private const val MAX_RETRY_ATTEMPTS = 4
        private val RETRY_DELAYS_MS = longArrayOf(2000, 4000, 8000, 16000)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private var debouncedSyncJob: Job? = null

    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
    }

    private val _isSignedIn = MutableStateFlow(false)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    private val _currentUserEmail = MutableStateFlow<String?>(null)
    val currentUserEmail: StateFlow<String?> = _currentUserEmail.asStateFlow()

    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    private var cachedUserId: String? = null

    init {
        setupNetworkMonitoring()
        setupAuthStateListener()
    }

    // --- Network Monitoring ---

    private fun setupNetworkMonitoring() {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isConnected.value = true
                // Trigger sync when connectivity returns
                scheduleDebouncedSync()
            }

            override fun onLost(network: Network) {
                _isConnected.value = false
            }
        })
    }

    // --- Auth State Listener ---

    private fun setupAuthStateListener() {
        scope.launch {
            client.auth.sessionStatus.collect { status ->
                when (status) {
                    is io.github.jan.supabase.gotrue.SessionStatus.Authenticated -> {
                        _isSignedIn.value = true
                        _sessionExpired.value = false
                        _currentUserEmail.value = status.session.user?.email
                        cachedUserId = null // Clear cache, will be refreshed on next sync
                    }
                    is io.github.jan.supabase.gotrue.SessionStatus.NotAuthenticated -> {
                        _isSignedIn.value = false
                        _currentUserEmail.value = null
                        cachedUserId = null
                        if (_sessionExpired.value.not()) {
                            // Only set expired if we were previously signed in
                        }
                    }
                    else -> { /* Loading state */ }
                }
            }
        }
    }

    // --- Auth Methods ---

    suspend fun signInEmail(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signUpEmail(email: String, password: String) {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun resetPassword(email: String) {
        client.auth.resetPasswordForEmail(email)
    }

    suspend fun resendConfirmation(email: String) {
        client.auth.resendEmail(io.github.jan.supabase.gotrue.providers.builtin.Email.Config.ResendType.SIGNUP, email)
    }

    suspend fun signInWithGoogle(idToken: String) {
        client.auth.signInWith(IDToken) {
            this.idToken = idToken
            this.provider = Google
        }
    }

    suspend fun signOut() {
        client.auth.signOut()
        cachedUserId = null
        _isSignedIn.value = false
        _currentUserEmail.value = null
        _sessionExpired.value = false
    }

    suspend fun restoreSession() {
        try {
            client.auth.retrieveUser()
            client.auth.refreshCurrentSession()
            _isSignedIn.value = client.auth.currentSessionOrNull() != null
            _currentUserEmail.value = client.auth.currentUserOrNull()?.email
        } catch (e: Exception) {
            Log.w(TAG, "Session restore failed: ${e.message}")
            _isSignedIn.value = false
        }
    }

    suspend fun deleteAccount() {
        // Call edge function to delete all data + auth user
        client.postgrest.rpc("delete_user_account")
        signOut()
    }

    // --- Push Token Registration ---

    @Serializable
    private data class UserDeviceRow(
        @SerialName("user_id") val userId: String,
        @SerialName("device_token") val deviceToken: String,
        @SerialName("device_name") val deviceName: String? = null,
        val platform: String = "android",
        @SerialName("last_seen_at") val lastSeenAt: String? = null
    )

    suspend fun registerDeviceToken(token: String, deviceName: String? = null) {
        if (!_isSignedIn.value) return
        val userId = cachedUserId ?: return
        try {
            val row = UserDeviceRow(
                userId = userId,
                deviceToken = token,
                deviceName = deviceName,
                platform = "android",
                lastSeenAt = java.time.Instant.now().toString()
            )
            client.postgrest.from("user_devices").upsert(row) {
                onConflict = "device_token"
            }
        } catch (e: Exception) {
            Log.w(TAG, "registerDeviceToken failed: ${e.message}")
        }
    }

    // --- Sync Methods ---

    fun scheduleDebouncedSync() {
        debouncedSyncJob?.cancel()
        debouncedSyncJob = scope.launch {
            delay(SYNC_DEBOUNCE_MS)
            performSync()
        }
    }

    fun syncOnForeground() {
        scope.launch { performSync() }
    }

    private suspend fun performSync() {
        if (!_isSignedIn.value || !_isConnected.value) return

        syncMutex.withLock {
            _isSyncing.value = true
            _syncError.value = null

            try {
                pushPendingItems()
                pullRemoteChanges()
                secureStorageService.setLastSyncTimestamp(System.currentTimeMillis())
            } catch (e: Exception) {
                handleSyncError(e)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private suspend fun pushPendingItems() {
        val pendingItems = trackerItemDao.getItemsBySyncStatus(SyncStatus.PENDING)
        if (pendingItems.isEmpty()) return

        withRetry("push") {
            val rows = pendingItems.map { item ->
                TrackerItemRow(
                    id = item.id,
                    name = item.name,
                    categoryId = item.categoryId,
                    reminderIntervalDays = item.reminderIntervalDays,
                    sortOrder = item.sortOrder,
                    iconName = item.iconName,
                    isArchived = item.isArchived
                )
            }

            client.postgrest.from("tracker_items").upsert(rows)

            // Mark items as synced
            pendingItems.forEach { item ->
                trackerItemDao.update(item.copy(syncStatus = SyncStatus.SYNCED))
            }
        }
    }

    private suspend fun pullRemoteChanges() {
        val lastSync = secureStorageService.getLastSyncTimestamp()

        withRetry("pull") {
            val remoteItems = if (lastSync > 0) {
                client.postgrest.from("tracker_items")
                    .select()
                    .decodeList<TrackerItemRow>()
            } else {
                client.postgrest.from("tracker_items")
                    .select()
                    .decodeList<TrackerItemRow>()
            }

            for (remoteRow in remoteItems) {
                val localItem = trackerItemDao.getItemById(remoteRow.id)
                if (localItem == null) {
                    // New item from server — insert
                    trackerItemDao.insert(
                        TrackerItem(
                            id = remoteRow.id,
                            name = remoteRow.name,
                            categoryId = remoteRow.categoryId,
                            reminderIntervalDays = remoteRow.reminderIntervalDays,
                            sortOrder = remoteRow.sortOrder,
                            iconName = remoteRow.iconName,
                            isArchived = remoteRow.isArchived,
                            syncStatus = SyncStatus.SYNCED
                        )
                    )
                } else if (localItem.syncStatus == SyncStatus.SYNCED) {
                    // No local changes — safe to overwrite with remote
                    trackerItemDao.update(
                        localItem.copy(
                            name = remoteRow.name,
                            categoryId = remoteRow.categoryId,
                            reminderIntervalDays = remoteRow.reminderIntervalDays,
                            sortOrder = remoteRow.sortOrder,
                            iconName = remoteRow.iconName,
                            isArchived = remoteRow.isArchived,
                            syncStatus = SyncStatus.SYNCED
                        )
                    )
                } else {
                    // Local has pending changes — conflict detection via updatedAt
                    // Keep local version, mark as pending for next push
                    Log.d(TAG, "Conflict detected for item ${remoteRow.id}, keeping local version")
                }
            }
        }
    }

    // --- Retry Logic ---

    private suspend fun <T> withRetry(operation: String, block: suspend () -> T): T {
        var lastException: Exception? = null

        for (attempt in 0 until MAX_RETRY_ATTEMPTS) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                val errorMessage = e.message ?: ""

                // Check for auth errors — don't retry, trigger re-auth
                if (errorMessage.contains("401") || errorMessage.contains("403") ||
                    errorMessage.contains("JWT", ignoreCase = true)) {
                    _sessionExpired.value = true
                    throw e
                }

                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    Log.w(TAG, "Sync $operation attempt ${attempt + 1} failed, retrying in ${RETRY_DELAYS_MS[attempt]}ms: ${e.message}")
                    delay(RETRY_DELAYS_MS[attempt])
                }
            }
        }

        throw lastException ?: Exception("Sync $operation failed after $MAX_RETRY_ATTEMPTS attempts")
    }

    private fun handleSyncError(error: Exception) {
        val message = error.message ?: "Unknown sync error"
        Log.e(TAG, "Sync error: $message")
        _syncError.value = when {
            message.contains("401") || message.contains("403") -> {
                _sessionExpired.value = true
                "Session expired. Please sign in again."
            }
            message.contains("network", ignoreCase = true) ||
            message.contains("connection", ignoreCase = true) ->
                "Sync failed: No network connection. Changes saved locally."
            else -> "Sync failed: $message"
        }
    }
}
