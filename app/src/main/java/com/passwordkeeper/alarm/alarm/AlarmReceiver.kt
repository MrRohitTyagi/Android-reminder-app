package com.passwordkeeper.alarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.passwordkeeper.alarm.model.Reminder
import com.passwordkeeper.alarm.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra("REMINDER_ID") ?: "default"
        val title = intent.getStringExtra("REMINDER_TITLE") ?: "Reminder"
        val time = intent.getStringExtra("REMINDER_TIME") ?: ""
        val notes = intent.getStringExtra("REMINDER_NOTES") ?: ""
        val recurrence = intent.getStringExtra("REMINDER_RECURRENCE") ?: "once"

        // 1. Start foreground service to play continuous ringtone & vibrate
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("REMINDER_ID", reminderId)
            putExtra("REMINDER_TITLE", title)
            putExtra("REMINDER_TIME", time)
            putExtra("REMINDER_NOTES", notes)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // 2. Launch full-screen wake activity on top of lockscreen
        val activityIntent = Intent(context, AlarmActivity::class.java).apply {
            putExtra("REMINDER_ID", reminderId)
            putExtra("REMINDER_TITLE", title)
            putExtra("REMINDER_TIME", time)
            putExtra("REMINDER_NOTES", notes)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(activityIntent)

        // 3. If this was a one-time reminder, automatically delete it from DB and local cache
        if (recurrence == "once" && reminderId != "default") {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = context.getSharedPreferences("pk_alarm_prefs", Context.MODE_PRIVATE)
                    val backendUrl = prefs.getString("backend_url", null)
                    if (!backendUrl.isNullOrEmpty()) {
                        ApiClient.deleteReminder(backendUrl, reminderId)
                    }

                    val cachedJson = prefs.getString("cached_reminders_json", null)
                    if (!cachedJson.isNullOrEmpty()) {
                        val listType = object : TypeToken<List<Reminder>>() {}.type
                        val list: List<Reminder>? = Gson().fromJson(cachedJson, listType)
                        if (list != null) {
                            val updated = list.filter { it.id != reminderId }
                            prefs.edit().putString("cached_reminders_json", Gson().toJson(updated)).apply()
                        }
                    }
                } catch (_: Exception) {
                    // Ignore deletion network error
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
