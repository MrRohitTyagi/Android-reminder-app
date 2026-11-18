package com.passwordkeeper.alarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.passwordkeeper.alarm.model.Reminder
import java.util.Calendar

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"

    fun scheduleReminder(context: Context, reminder: Reminder) {
        if (!reminder.enabled) {
            cancelReminder(context, reminder.id)
            return
        }

        val triggerTimeMs = calculateNextTriggerTime(reminder) ?: return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("REMINDER_ID", reminder.id)
            putExtra("REMINDER_TITLE", reminder.title)
            putExtra("REMINDER_TIME", reminder.time)
            putExtra("REMINDER_RECURRENCE", reminder.recurrence ?: "once")
            putExtra("REMINDER_NOTES", reminder.notes ?: "")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm permission not granted, falling back to standard alarm: ${e.message}")
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMs,
                pendingIntent
            )
        }
    }

    fun cancelReminder(context: Context, reminderId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun scheduleSnooze(context: Context, reminderId: String, title: String, timeStr: String, minutes: Int = 10) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerTimeMs = System.currentTimeMillis() + (minutes * 60 * 1000)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("REMINDER_ID", reminderId)
            putExtra("REMINDER_TITLE", "[Snoozed] $title")
            putExtra("REMINDER_TIME", timeStr)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm permission missing on snooze, falling back: ${e.message}")
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMs,
                pendingIntent
            )
        }
    }

    private fun calculateNextTriggerTime(reminder: Reminder): Long? {
        val timeParts = reminder.time.split(":")
        if (timeParts.size < 2) return null
        val targetHour = timeParts[0].toIntOrNull() ?: return null
        val targetMin = timeParts[1].toIntOrNull() ?: return null

        val now = Calendar.getInstance()

        when (reminder.recurrence) {
            "once" -> {
                val cal = Calendar.getInstance().apply {
                    if (!reminder.date.isNullOrEmpty()) {
                        val dParts = reminder.date.split("-")
                        if (dParts.size == 3) {
                            val y = dParts[0].toIntOrNull() ?: return@apply
                            val m = dParts[1].toIntOrNull() ?: return@apply
                            val d = dParts[2].toIntOrNull() ?: return@apply
                            set(Calendar.YEAR, y)
                            set(Calendar.MONTH, m - 1)
                            set(Calendar.DAY_OF_MONTH, d)
                        }
                    }
                    set(Calendar.HOUR_OF_DAY, targetHour)
                    set(Calendar.MINUTE, targetMin)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                return if (cal.after(now)) cal.timeInMillis else null
            }
            "daily" -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, targetHour)
                    set(Calendar.MINUTE, targetMin)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (!cal.after(now)) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                return cal.timeInMillis
            }
            "custom", "weekdays" -> {
                val allowedDays = if (reminder.recurrence == "weekdays") {
                    listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
                } else {
                    reminder.customDays?.map { dayIndex ->
                        // 0 = Sun, 1 = Mon ...
                        if (dayIndex == 0) Calendar.SUNDAY else dayIndex + 1
                    } ?: listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
                }

                for (dayOffset in 0..7) {
                    val cal = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, dayOffset)
                        set(Calendar.HOUR_OF_DAY, targetHour)
                        set(Calendar.MINUTE, targetMin)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                    if (allowedDays.contains(dayOfWeek) && cal.after(now)) {
                        return cal.timeInMillis
                    }
                }
                return null
            }
            else -> {
                val cal = Calendar.getInstance().apply {
                    if (!reminder.date.isNullOrEmpty()) {
                        val dParts = reminder.date.split("-")
                        if (dParts.size == 3) {
                            val y = dParts[0].toIntOrNull() ?: return@apply
                            val m = dParts[1].toIntOrNull() ?: return@apply
                            val d = dParts[2].toIntOrNull() ?: return@apply
                            set(Calendar.YEAR, y)
                            set(Calendar.MONTH, m - 1)
                            set(Calendar.DAY_OF_MONTH, d)
                        }
                    }
                    set(Calendar.HOUR_OF_DAY, targetHour)
                    set(Calendar.MINUTE, targetMin)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (!cal.after(now)) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                return cal.timeInMillis
            }
        }
    }
}
