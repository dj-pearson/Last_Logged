package com.pearsonmedia.lastlogged.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pearsonmedia.lastlogged.data.repository.TrackerRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Re-arms reminder alarms after the device reboots or the app is updated.
 *
 * Android clears every [android.app.AlarmManager] alarm on reboot, and
 * `NotificationService` schedules reminders exclusively through AlarmManager.
 * Without this receiver, a user's reminders stopped firing permanently after
 * their first restart, with nothing in the UI to indicate it.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationService: NotificationService
    @Inject lateinit var secureStorageService: SecureStorageService
    @Inject lateinit var repository: TrackerRepository

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // LOCKED_BOOT_COMPLETED is deliberately not handled: SecureStorageService
        // is backed by EncryptedSharedPreferences, which is unreadable in direct
        // boot, hence directBootAware="false" in the manifest.
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }

        // Nothing to restore if the user turned reminders off — avoids waking
        // the database on every boot for those users.
        if (!secureStorageService.getRemindersEnabled()) {
            Log.d(TAG, "Reminders disabled; skipping reschedule")
            return
        }

        // Reading Room and rescheduling is far too slow for onReceive's main-thread
        // budget, so keep the broadcast alive while it happens.
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val items = repository.getActiveItems().first()
                notificationService.rescheduleAllNotifications(items)
                Log.d(TAG, "Rescheduled reminders for ${items.size} trackers after ${intent.action}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to reschedule after ${intent.action}: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
