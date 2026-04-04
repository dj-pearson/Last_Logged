package com.pearsonmedia.lastlogged.service

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pearsonmedia.lastlogged.R
import com.pearsonmedia.lastlogged.data.local.entity.TrackerItem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorageService: SecureStorageService
) {
    companion object {
        private const val TAG = "NotificationService"
        const val CHANNEL_ID_REMINDERS = "tracker_reminders"
        const val CHANNEL_ID_OVERFLOW = "tracker_overflow"
        private const val MAX_NOTIFICATIONS = 64
        private const val REQUEST_CODE_BASE = 10000
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val reminderChannel = NotificationChannel(
            CHANNEL_ID_REMINDERS,
            "Tracker Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Reminders for overdue tracker items"
        }

        val overflowChannel = NotificationChannel(
            CHANNEL_ID_OVERFLOW,
            "Reminder Overflow",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications about skipped reminders due to limit"
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannels(listOf(reminderChannel, overflowChannel))
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun rescheduleAllNotifications(trackerItems: List<TrackerItem>) {
        if (!secureStorageService.getRemindersEnabled()) {
            cancelAllNotifications()
            return
        }

        if (!hasNotificationPermission()) {
            Log.w(TAG, "Notification permission not granted, skipping reschedule")
            return
        }

        // Cancel existing notifications
        cancelAllNotifications()

        val defaultHour = secureStorageService.getDefaultReminderHour()
        val defaultMinute = secureStorageService.getDefaultReminderMinute()

        // Sort by overdue priority (most overdue first)
        val sortedItems = trackerItems
            .filter { !it.isArchived }
            .sortedByDescending { item ->
                if (item.lastCompletedAt == null) {
                    Long.MAX_VALUE
                } else {
                    val daysSince = TimeUnit.MILLISECONDS.toDays(
                        System.currentTimeMillis() - item.lastCompletedAt
                    )
                    daysSince - item.reminderIntervalDays
                }
            }

        val itemsToSchedule = sortedItems.take(MAX_NOTIFICATIONS - 1)
        val skippedCount = (sortedItems.size - itemsToSchedule.size).coerceAtLeast(0)

        itemsToSchedule.forEachIndexed { index, item ->
            scheduleNotification(item, index, defaultHour, defaultMinute)
        }

        // If items were skipped, schedule overflow notification as the last slot
        if (skippedCount > 0) {
            scheduleOverflowNotification(skippedCount)
        }

        Log.d(TAG, "Scheduled ${itemsToSchedule.size} notifications, skipped $skippedCount")
    }

    private fun scheduleNotification(
        item: TrackerItem,
        index: Int,
        hour: Int,
        minute: Int
    ) {
        val dueDate = calculateDueDate(item, hour, minute)
        if (dueDate <= System.currentTimeMillis()) {
            // Already overdue — schedule for tomorrow at the default time
            val calendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            scheduleAlarm(item.id, item.name, calendar.timeInMillis, REQUEST_CODE_BASE + index)
        } else {
            scheduleAlarm(item.id, item.name, dueDate, REQUEST_CODE_BASE + index)
        }
    }

    private fun calculateDueDate(item: TrackerItem, hour: Int, minute: Int): Long {
        val lastCompleted = item.lastCompletedAt ?: item.createdAt
        val calendar = Calendar.getInstance().apply {
            timeInMillis = lastCompleted
            add(Calendar.DAY_OF_YEAR, item.reminderIntervalDays)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun scheduleAlarm(trackerId: String, trackerName: String, triggerAtMillis: Long, requestCode: Int) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("tracker_id", trackerId)
            putExtra("tracker_name", trackerName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } catch (e: SecurityException) {
            // SCHEDULE_EXACT_ALARM permission not granted on Android 12+
            Log.w(TAG, "Exact alarm permission not available, using inexact: ${e.message}")
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun scheduleOverflowNotification(skippedCount: Int) {
        // Show immediately — this is informational
        if (!hasNotificationPermission()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_OVERFLOW)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Reminder Limit Reached")
            .setContentText("You have $skippedCount trackers without reminders. Open Last Logged to review.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(99999, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed to show overflow notification: ${e.message}")
        }
    }

    fun cancelAllNotifications() {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        for (i in 0 until MAX_NOTIFICATIONS) {
            val intent = Intent(context, NotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_BASE + i,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let { alarmManager.cancel(it) }
        }
        NotificationManagerCompat.from(context).cancelAll()
    }
}

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val trackerId = intent.getStringExtra("tracker_id") ?: return
        val trackerName = intent.getStringExtra("tracker_name") ?: "a tracker"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = NotificationCompat.Builder(context, NotificationService.CHANNEL_ID_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Time to log: $trackerName")
            .setContentText("It's been a while since you last logged this.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            // Deep link to tracker detail would go here
            .build()

        try {
            NotificationManagerCompat.from(context).notify(trackerId.hashCode(), notification)
        } catch (e: SecurityException) {
            Log.w("NotificationReceiver", "Failed to show notification: ${e.message}")
        }
    }
}
