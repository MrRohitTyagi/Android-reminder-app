package com.passwordkeeper.alarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.passwordkeeper.alarm.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val prefs = context.getSharedPreferences("pk_alarm_prefs", Context.MODE_PRIVATE)
            val backendUrl = prefs.getString("backend_url", null) ?: return

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val reminders = ApiClient.fetchReminders(backendUrl)
                    reminders.forEach { reminder ->
                        if (reminder.enabled) {
                            AlarmScheduler.scheduleReminder(context, reminder)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
