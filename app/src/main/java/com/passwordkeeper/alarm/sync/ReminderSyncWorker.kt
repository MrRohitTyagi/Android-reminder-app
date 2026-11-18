package com.passwordkeeper.alarm.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.passwordkeeper.alarm.alarm.AlarmScheduler
import com.passwordkeeper.alarm.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReminderSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "ReminderSyncWorker"
        const val WORK_NAME = "passwordkeeper_periodic_sync"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("pk_alarm_prefs", Context.MODE_PRIVATE)
        val backendUrl = prefs.getString("backend_url", null)

        if (backendUrl.isNullOrEmpty()) {
            Log.d(TAG, "No backend URL configured, skipping auto-sync")
            return@withContext Result.success()
        }

        try {
            Log.d(TAG, "Starting background auto-sync from $backendUrl")
            val reminders = ApiClient.fetchReminders(backendUrl)

            // Save reminders locally
            val remindersJson = Gson().toJson(reminders)
            prefs.edit()
                .putString("cached_reminders_json", remindersJson)
                .putLong("last_sync_timestamp", System.currentTimeMillis())
                .apply()

            // Schedule alarms in AlarmManager
            reminders.forEach { reminder ->
                if (reminder.enabled) {
                    AlarmScheduler.scheduleReminder(context, reminder)
                } else {
                    AlarmScheduler.cancelReminder(context, reminder.id)
                }
            }

            // Ensure ntfy live sync listener is active
            val syncTopic = prefs.getString("sync_topic", null) ?: ApiClient.fetchSyncTopic(backendUrl)
            if (!syncTopic.isNullOrEmpty()) {
                prefs.edit().putString("sync_topic", syncTopic).apply()
                NtfySyncManager.start(context, syncTopic)
            }

            Log.d(TAG, "Auto-sync complete: scheduled ${reminders.size} reminders")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Auto-sync failed: ${e.message}", e)
            Result.retry()
        }
    }
}
