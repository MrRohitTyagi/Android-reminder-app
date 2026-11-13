package com.passwordkeeper.alarm

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.passwordkeeper.alarm.alarm.AlarmActivity
import com.passwordkeeper.alarm.alarm.AlarmScheduler
import com.passwordkeeper.alarm.alarm.AlarmService
import com.passwordkeeper.alarm.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var etUrl: EditText
    private lateinit var btnSync: Button
    private lateinit var btnTestAlarm: Button
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissions()

        etUrl = findViewById(R.id.etBackendUrl)
        btnSync = findViewById(R.id.btnSyncReminders)
        btnTestAlarm = findViewById(R.id.btnTestAlarm)
        tvStatus = findViewById(R.id.tvStatus)

        val prefs = getSharedPreferences("pk_alarm_prefs", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("backend_url", "https://your-passwordkeeper-url.pages.dev")
        etUrl.setText(savedUrl)

        btnSync.setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Please enter your backend URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString("backend_url", url).apply()
            syncReminders(url)
        }

        btnTestAlarm.setOnClickListener {
            // Test Ringtone & Vibration immediately
            val serviceIntent = Intent(this, AlarmService::class.java).apply {
                putExtra("REMINDER_TITLE", "Test Strict Reminder")
                putExtra("REMINDER_TIME", "Now")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            val activityIntent = Intent(this, AlarmActivity::class.java).apply {
                putExtra("REMINDER_TITLE", "Test Strict Reminder")
                putExtra("REMINDER_TIME", "Now")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(activityIntent)
        }
    }

    private fun checkPermissions() {
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
    }

    private fun syncReminders(url: String) {
        tvStatus.text = "Syncing reminders from server..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminders = ApiClient.fetchReminders(url)
                reminders.forEach { reminder ->
                    if (reminder.enabled) {
                        AlarmScheduler.scheduleReminder(this@MainActivity, reminder)
                    } else {
                        AlarmScheduler.cancelReminder(this@MainActivity, reminder.id)
                    }
                }
                withContext(Dispatchers.Main) {
                    tvStatus.text = "✅ Successfully synced and scheduled ${reminders.size} reminders directly into your phone clock!"
                    Toast.makeText(this@MainActivity, "Synced ${reminders.size} alarms!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "❌ Sync failed: ${e.message}"
                }
            }
        }
    }
}
