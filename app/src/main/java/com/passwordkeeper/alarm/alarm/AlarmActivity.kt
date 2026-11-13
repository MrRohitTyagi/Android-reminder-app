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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAndUnlockScreen()
        setContentView(R.layout.activity_alarm)

        reminderId = intent.getStringExtra("REMINDER_ID") ?: "default"
        reminderTitle = intent.getStringExtra("REMINDER_TITLE") ?: "Reminder"
        reminderTime = intent.getStringExtra("REMINDER_TIME") ?: ""

        val tvTitle = findViewById<TextView>(R.id.tvAlarmTitle)
        val tvTime = findViewById<TextView>(R.id.tvAlarmTime)
        val btnDismiss = findViewById<Button>(R.id.btnDismiss)
        val btnSnooze = findViewById<Button>(R.id.btnSnooze)

        tvTitle.text = reminderTitle
        tvTime.text = reminderTime

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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
