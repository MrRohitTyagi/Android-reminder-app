package com.passwordkeeper.alarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import com.passwordkeeper.alarm.R

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val TAG = "AlarmService"
        const val CHANNEL_ID = "passwordkeeper_alarm_channel"
        const val NOTIFICATION_ID = 9001
        const val ACTION_STOP = "com.passwordkeeper.alarm.ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAlarm()
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra("REMINDER_TITLE") ?: "Alarm"
        val time = intent?.getStringExtra("REMINDER_TIME") ?: ""

        startForeground(NOTIFICATION_ID, buildNotification(title, time))
        acquireWakeLock()
        playContinuousAlarm()

        return START_STICKY
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "PasswordKeeper:AlarmServiceWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // 10 minutes max timeout to save battery if unattended
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock: ${e.message}")
        }
    }

    private fun playContinuousAlarm() {
        val prefs = getSharedPreferences("pk_alarm_prefs", Context.MODE_PRIVATE)
        val customUriStr = prefs.getString("custom_ringtone_uri", null)

        var playedCustom = false

        // 1. Try playing custom audio / ringtone via MediaPlayer
        if (!customUriStr.isNullOrEmpty()) {
            try {
                val soundUri = Uri.parse(customUriStr)
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setDataSource(applicationContext, soundUri)
                    isLooping = true
                    prepare()
                    start()
                }
                playedCustom = true
                Log.d(TAG, "Playing custom alarm sound via MediaPlayer: $customUriStr")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to play custom sound ($customUriStr), falling back to system alarm: ${e.message}")
                mediaPlayer?.release()
                mediaPlayer = null
            }
        }

        // 2. Fallback to native system alarm ringtone
        if (!playedCustom) {
            try {
                var alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                if (alarmUri == null) {
                    alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                }
                ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
                ringtone?.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ringtone?.isLooping = true
                }
                ringtone?.play()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play system ringtone: ${e.message}")
            }
        }

        // 3. Continuous Vibration
        try {
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            val pattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0 means repeat
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start vibration: ${e.message}")
        }
    }

    private fun stopAlarm() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.stop()
                }
                mediaPlayer?.release()
                mediaPlayer = null
            }
            ringtone?.stop()
            vibrator?.cancel()

            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                wakeLock = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping alarm: ${e.message}")
        }
    }

    private fun buildNotification(title: String, time: String): Notification {
        val openIntent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔔 $title")
            .setContentText("Alarm firing ($time)")
            .setSmallIcon(R.drawable.ic_launcher_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingOpen, true)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PasswordKeeper Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Strict ringtone and vibration alarms"
                enableVibration(true)
                setSound(null, null) // Sound is handled directly by MediaPlayer / RingtoneManager
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
