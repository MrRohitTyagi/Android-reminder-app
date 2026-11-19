package com.passwordkeeper.alarm.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.passwordkeeper.alarm.R

class AlarmActivity : AppCompatActivity() {

    private lateinit var reminderId: String
    private lateinit var reminderTitle: String
    private lateinit var reminderTime: String
    private lateinit var tvTitle: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvNotes: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        wakeAndUnlockScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm)

        tvTitle = findViewById(R.id.tvAlarmTitle)
        tvTime = findViewById(R.id.tvAlarmTime)
        tvNotes = findViewById(R.id.tvAlarmNotes)
        val btnDismiss = findViewById<Button>(R.id.btnDismiss)
        val btnSnooze = findViewById<Button>(R.id.btnSnooze)

        extractAndDisplayData(intent)

        btnDismiss.setOnClickListener {
            stopAlarmService()
            finish()
        }

        btnSnooze.setOnClickListener {
            stopAlarmService()
            AlarmScheduler.scheduleSnooze(this, reminderId, reminderTitle, reminderTime, 10)
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        wakeAndUnlockScreen()
        extractAndDisplayData(intent)
    }

    override fun onResume() {
        super.onResume()
        wakeAndUnlockScreen()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
    }

    private fun extractAndDisplayData(intent: Intent) {
        reminderId = intent.getStringExtra("REMINDER_ID") ?: "default"
        reminderTitle = intent.getStringExtra("REMINDER_TITLE") ?: "Reminder"
        reminderTime = intent.getStringExtra("REMINDER_TIME") ?: ""

        tvTitle.text = reminderTitle
        tvTime.text = reminderTime

        val notes = intent.getStringExtra("REMINDER_NOTES")
        if (!notes.isNullOrEmpty()) {
            tvNotes.text = notes
        } else {
            tvNotes.text = "PasswordKeeper Alarm"
        }
    }

    private fun stopAlarmService() {
        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP
        }
        startService(stopIntent)
    }

    private fun wakeAndUnlockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
    }
}
