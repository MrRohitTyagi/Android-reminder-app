package com.passwordkeeper.alarm

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.passwordkeeper.alarm.alarm.AlarmActivity
import com.passwordkeeper.alarm.alarm.AlarmScheduler
import com.passwordkeeper.alarm.alarm.AlarmService
import com.passwordkeeper.alarm.model.Reminder
import com.passwordkeeper.alarm.network.ApiClient
import com.passwordkeeper.alarm.sync.NtfySyncManager
import com.passwordkeeper.alarm.sync.ReminderSyncWorker
import com.passwordkeeper.alarm.ui.ReminderAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var etUrl: EditText
    private lateinit var btnEditUrl: Button
    private lateinit var tvSelectedRingtone: TextView
    private lateinit var btnChooseSystemRingtone: Button
    private lateinit var btnChooseMusicFile: Button
    private lateinit var btnSync: Button
    private lateinit var btnTestAlarm: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvRemindersCount: TextView
    private lateinit var tvEmptyReminders: TextView
    private lateinit var rvReminders: RecyclerView
    private lateinit var reminderAdapter: ReminderAdapter

    private var isUrlEditing = false

    // System Ringtone Picker Launcher
    private val systemRingtoneLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }

            if (uri != null) {
                val ringtone = RingtoneManager.getRingtone(this, uri)
                val title = ringtone?.getTitle(this) ?: "System Ringtone"
                saveCustomRingtone(uri.toString(), title)
            } else {
                // User picked "Silent" or default
                clearCustomRingtone()
            }
        }
    }

    // Audio File / Song Picker Launcher
    private val musicFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                // Persist read permissions so AlarmService can read it after app restart
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)

                val displayName = queryFileName(uri) ?: "Selected Song"
                saveCustomRingtone(uri.toString(), displayName)
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to load audio: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissions()
        initViews()
        setupUrlSection()
        setupRingtoneSection()
        setupRemindersList()
        setupAutoSync()

        // Register instant push sync listener
        NtfySyncManager.onSyncReceived = {
            loadCachedReminders()
            tvStatus.text = "⚡ Instant push sync received! Alarms updated."
        }
        NtfySyncManager.onConnectionStateChanged = { isConnected ->
            if (isConnected && (tvStatus.text.isNullOrBlank() || tvStatus.text.contains("Syncing"))) {
                tvStatus.text = "🟢 Live real-time sync connected"
            }
        }

        val savedTopic = getSavedSyncTopic()
        if (!savedTopic.isNullOrEmpty()) {
            NtfySyncManager.start(this, savedTopic)
        }

        // Load cached reminders immediately so user sees them without waiting
        loadCachedReminders()

        // Trigger auto-sync on launch if URL is configured
        val savedUrl = getSavedUrl()
        if (!savedUrl.isNullOrEmpty()) {
            syncReminders(savedUrl, isAuto = true)
        }
    }

    override fun onResume() {
        super.onResume()
        val savedTopic = getSavedSyncTopic()
        if (!savedTopic.isNullOrEmpty()) {
            NtfySyncManager.start(this, savedTopic)
        }
        // Silently sync reminders if URL is in place
        val savedUrl = getSavedUrl()
        if (!savedUrl.isNullOrEmpty()) {
            syncReminders(savedUrl, isAuto = true)
        }
    }

    private fun initViews() {
        etUrl = findViewById(R.id.etBackendUrl)
        btnEditUrl = findViewById(R.id.btnEditUrl)
        tvSelectedRingtone = findViewById(R.id.tvSelectedRingtone)
        btnChooseSystemRingtone = findViewById(R.id.btnChooseSystemRingtone)
        btnChooseMusicFile = findViewById(R.id.btnChooseMusicFile)
        btnSync = findViewById(R.id.btnSyncReminders)
        btnTestAlarm = findViewById(R.id.btnTestAlarm)
        tvStatus = findViewById(R.id.tvStatus)
        tvRemindersCount = findViewById(R.id.tvRemindersCount)
        tvEmptyReminders = findViewById(R.id.tvEmptyReminders)
        rvReminders = findViewById(R.id.rvReminders)

        btnSync.setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Please enter your backend URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveUrl(url)
            lockUrlField()
            syncReminders(url, isAuto = false)
        }

        btnTestAlarm.setOnClickListener {
            Toast.makeText(this, "🔔 Alarm will fire in 5 seconds. Lock your phone now to test!", Toast.LENGTH_LONG).show()

            val testReminder = Reminder(
                id = "test-preview-alarm",
                title = "Test Alarm (Lockscreen Check)",
                time = "Now",
                recurrence = "once",
                enabled = true,
                notes = "Screen turns on and shows over lockscreen!"
            )

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = Intent(this, AlarmReceiver::class.java).apply {
                putExtra("REMINDER_ID", testReminder.id)
                putExtra("REMINDER_TITLE", testReminder.title)
                putExtra("REMINDER_TIME", testReminder.time)
                putExtra("REMINDER_RECURRENCE", "once")
                putExtra("REMINDER_NOTES", testReminder.notes)
            }
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                this,
                99999,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val showIntent = Intent(this, AlarmActivity::class.java).apply {
                putExtra("REMINDER_ID", testReminder.id)
                putExtra("REMINDER_TITLE", testReminder.title)
                putExtra("REMINDER_TIME", testReminder.time)
                putExtra("REMINDER_NOTES", testReminder.notes)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            val showPendingIntent = android.app.PendingIntent.getActivity(
                this,
                99998,
                showIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val triggerTime = System.currentTimeMillis() + 5000L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                alarmManager.setAlarmClock(
                    android.app.AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent),
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    android.app.AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        }
    }

    private fun setupUrlSection() {
        val savedUrl = getSavedUrl()
        if (!savedUrl.isNullOrEmpty()) {
            etUrl.setText(savedUrl)
            lockUrlField()
        } else {
            unlockUrlField()
        }

        btnEditUrl.setOnClickListener {
            if (isUrlEditing) {
                // Save and Lock
                val newUrl = etUrl.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    saveUrl(newUrl)
                    lockUrlField()
                    syncReminders(newUrl, isAuto = false)
                } else {
                    Toast.makeText(this, "URL cannot be empty", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Unlock for Editing
                unlockUrlField()
            }
        }
    }

    private fun lockUrlField() {
        etUrl.isEnabled = false
        etUrl.alpha = 0.6f
        btnEditUrl.text = "Edit"
        isUrlEditing = false
    }

    private fun unlockUrlField() {
        etUrl.isEnabled = true
        etUrl.alpha = 1.0f
        etUrl.requestFocus()
        btnEditUrl.text = "Save"
        isUrlEditing = true
    }

    private fun setupRingtoneSection() {
        updateRingtoneDisplay()

        btnChooseSystemRingtone.setOnClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                val currentUri = getSharedPreferences("pk_alarm_prefs", Context.MODE_PRIVATE)
                    .getString("custom_ringtone_uri", null)
                if (currentUri != null) {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(currentUri))
                }
            }
            systemRingtoneLauncher.launch(intent)
        }

        btnChooseMusicFile.setOnClickListener {
            musicFileLauncher.launch(arrayOf("audio/*"))
        }
    }

    private fun updateRingtoneDisplay() {
        val prefs = getSharedPreferences("pk_alarm_prefs", Context.MODE_PRIVATE)
        val title = prefs.getString("custom_ringtone_title", null)
        if (!title.isNullOrEmpty()) {
            tvSelectedRingtone.text = "🎵 Sound: $title"
        } else {
            tvSelectedRingtone.text = "🔔 Sound: Default Alarm Ringtone"
        }
    }

    private fun saveCustomRingtone(uriStr: String, title: String) {
        val prefs = getSharedPreferences("pk_alarm_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("custom_ringtone_uri", uriStr)
            .putString("custom_ringtone_title", title)
            .apply()
        updateRingtoneDisplay()
        Toast.makeText(this, "Selected: $title", Toast.LENGTH_SHORT).show()
    }

    private fun clearCustomRingtone() {
        val prefs = getSharedPreferences("pk_alarm_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("custom_ringtone_uri")
            .remove("custom_ringtone_title")
            .apply()
        updateRingtoneDisplay()
    }

    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        return name
    }

    private fun setupRemindersList() {
        reminderAdapter = ReminderAdapter()
        rvReminders.layoutManager = LinearLayoutManager(this)
        rvReminders.adapter = reminderAdapter
    }

    private fun isOneTimePassed(reminder: Reminder): Boolean {
        if (reminder.recurrence != "once") return false
        val timeParts = reminder.time.split(":")
        if (timeParts.size < 2) return false
        val targetHour = timeParts[0].toIntOrNull() ?: return false
        val targetMin = timeParts[1].toIntOrNull() ?: return false

        val now = java.util.Calendar.getInstance()
        val cal = java.util.Calendar.getInstance().apply {
            if (!reminder.date.isNullOrEmpty()) {
                val dParts = reminder.date.split("-")
                if (dParts.size == 3) {
                    val y = dParts[0].toIntOrNull() ?: return@apply
                    val m = dParts[1].toIntOrNull() ?: return@apply
                    val d = dParts[2].toIntOrNull() ?: return@apply
                    set(java.util.Calendar.YEAR, y)
                    set(java.util.Calendar.MONTH, m - 1)
                    set(java.util.Calendar.DAY_OF_MONTH, d)
                }
            }
            set(java.util.Calendar.HOUR_OF_DAY, targetHour)
            set(java.util.Calendar.MINUTE, targetMin)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return now.after(cal)
    }

    private fun loadCachedReminders() {
        val prefs = getSharedPreferences("pk_alarm_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("cached_reminders_json", null) ?: return
        try {
            val listType = object : TypeToken<List<Reminder>>() {}.type
            val cached: List<Reminder> = Gson().fromJson(json, listType) ?: return
            val activeOnly = cached.filter { !isOneTimePassed(it) }
            if (activeOnly.size != cached.size) {
                prefs.edit().putString("cached_reminders_json", Gson().toJson(activeOnly)).apply()
            }
            displayReminders(activeOnly)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun displayReminders(reminders: List<Reminder>) {
        if (reminders.isEmpty()) {
            tvEmptyReminders.visibility = View.VISIBLE
            rvReminders.visibility = View.GONE
            tvRemindersCount.text = "0 active"
        } else {
            tvEmptyReminders.visibility = View.GONE
            rvReminders.visibility = View.VISIBLE
            reminderAdapter.submitList(reminders)
            val activeCount = reminders.count { it.enabled }
            tvRemindersCount.text = "$activeCount active"
        }
    }

    private fun setupAutoSync() {
        // Periodic background auto-sync using WorkManager (every 1 hour)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicSyncRequest = PeriodicWorkRequestBuilder<ReminderSyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            ReminderSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicSyncRequest
        )
    }

    private fun syncReminders(url: String, isAuto: Boolean = false) {
        if (!isAuto) {
            tvStatus.text = "Syncing reminders from server..."
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminders = ApiClient.fetchReminders(url)

                // Cache reminders locally
                val json = Gson().toJson(reminders)
                getSharedPreferences("pk_alarm_prefs", Context.MODE_PRIVATE).edit()
                    .putString("cached_reminders_json", json)
                    .putLong("last_sync_timestamp", System.currentTimeMillis())
                    .apply()

                // Schedule alarms into Android OS clock
                reminders.forEach { reminder ->
                    if (reminder.enabled) {
                        AlarmScheduler.scheduleReminder(this@MainActivity, reminder)
                    } else {
                        AlarmScheduler.cancelReminder(this@MainActivity, reminder.id)
                    }
                }

                // Ensure real-time ntfy sync listener is active
                val topic = ApiClient.fetchSyncTopic(url) ?: getSavedSyncTopic()
                if (!topic.isNullOrEmpty()) {
                    saveSyncTopic(topic)
                    NtfySyncManager.start(this@MainActivity, topic)
                }

                withContext(Dispatchers.Main) {
                    displayReminders(reminders)
                    tvStatus.text = "✅ Synced ${reminders.size} reminders directly into your phone clock!"
                    if (!isAuto) {
                        Toast.makeText(this@MainActivity, "Synced ${reminders.size} alarms!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isAuto) {
                        tvStatus.text = "❌ Sync failed: ${e.message}"
                    }
                }
            }
        }
    }

    private fun getSavedUrl(): String? {
        val prefs = getSharedPreferences("pk_alarm_prefs", Context.MODE_PRIVATE)
        return prefs.getString("backend_url", null)
    }

    private fun saveUrl(url: String) {
        val prefs = getSharedPreferences("pk_alarm_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("backend_url", url).apply()
    }

    private fun getSavedSyncTopic(): String? {
        val prefs = getSharedPreferences("pk_alarm_prefs", Context.MODE_PRIVATE)
        return prefs.getString("sync_topic", null)
    }

    private fun saveSyncTopic(topic: String) {
        val prefs = getSharedPreferences("pk_alarm_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("sync_topic", topic).apply()
    }

    private fun checkPermissions() {
        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }

        // Exact Alarm permission for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
            if (alarmManager?.canScheduleExactAlarms() == false) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {}
            }
        }

        // Full Screen Intent permission for Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            if (notificationManager?.canUseFullScreenIntent() == false) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {}
            }
        }

        // Request exemption from battery optimizations to prevent background kills
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }
}
