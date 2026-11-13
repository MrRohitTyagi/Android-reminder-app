# PasswordKeeper Companion Alarm App — Complete Implementation Plan

This document contains the **100% complete, production-ready specification and code** for building a native Android Companion Alarm app in a separate repository.

- **Cost**: **$0.00 forever** (zero servers, zero telecom fees, zero app store fees).
- **Zero Local Dev Headaches**: **No Android Studio or React Native needed** on your machine.
- **Strict Alarm Behavior**: Rings with your phone's continuous alarm ringtone and strong vibration until you physically tap **Dismiss** or **Snooze**.
- **Automated Cloud Builds**: GitHub Actions compiles the `.apk` in 2 minutes. You simply download and install it on your phone.

---

## 1. System Architecture

```mermaid
flowchart TD
    CF["Cloudflare Backend<br/>(/api/reminders)"]
    
    subgraph Android Phone
        Sync["Background Sync & UI<br/>(MainActivity / WorkManager)"]
        AM["Android Native AlarmManager<br/>(setExactAndAllowWhileIdle)"]
        Rec["AlarmReceiver<br/>(BroadcastReceiver)"]
        Act["Full-Screen AlarmActivity<br/>(WakeLock + ShowWhenLocked)"]
        Svc["AlarmService<br/>(Looping Ringtone + Vibration)"]
    end
    
    CF -->|Fetch reminders JSON| Sync
    Sync -->|Schedules exact alarms into phone OS| AM
    AM -->|Triggers at exact HH:mm| Rec
    Rec -->|Starts| Svc
    Rec -->|Wakes screen & launches| Act
    Act -->|Dismiss / Snooze| Svc
```

### Key Technical Pillars:
1. **Exact Timing (`AlarmManager.setExactAndAllowWhileIdle`)**: Bypasses Android's Doze mode and battery optimization to fire at the exact minute.
2. **Full-Screen Turn On (`showWhenLocked` + `turnScreenOn`)**: Wakes the phone screen immediately even when locked and in your pocket.
3. **Continuous Audio Loop (`RingtoneManager.TYPE_ALARM`)**: Uses the Android Alarm audio stream (which respects alarm volume, not media/silent volume) and loops continuously until dismissed.
4. **Vibrator Pattern**: Rhythmic pulses (`[0, 1000, 500, 1000]`) to ensure physical awareness.
5. **Offline Support**: Alarms are scheduled directly into the Android system clock. Once synced, it rings even if your phone has zero internet connection.
6. **Reboot Persistence (`RECEIVE_BOOT_COMPLETED`)**: Reschedules all alarms automatically if the phone reboots.

---

## 2. Target Repository Directory Structure

Create a new GitHub repository (e.g. `passwordkeeper-alarm-app`) with this exact structure:

```
passwordkeeper-alarm-app/
├── .github/
│   └── workflows/
│       └── build-apk.yml              # Cloud build workflow to compile APK
├── app/
│   ├── build.gradle.kts               # App dependencies and build settings
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml    # Permissions and components
│           ├── java/com/passwordkeeper/alarm/
│           │   ├── MainActivity.kt    # Main UI: Backend URL, Sync, Test Alarm
│           │   ├── model/
│           │   │   └── Reminder.kt    # Data model
│           │   ├── network/
│           │   │   └── ApiClient.kt   # Fetches reminders from Cloudflare
│           │   ├── alarm/
│           │   │   ├── AlarmScheduler.kt # Calculates next trigger timestamp
│           │   │   ├── AlarmReceiver.kt  # Receives OS alarm signal
│           │   │   ├── AlarmService.kt   # Plays looping sound & vibrates
│           │   │   ├── AlarmActivity.kt  # Fullscreen ringing screen
│           │   │   └── BootReceiver.kt   # Reschedules on phone restart
│           └── res/
│               ├── layout/
│               │   ├── activity_main.xml
│               │   └── activity_alarm.xml
│               ├── values/
│               │   ├── colors.xml
│               │   ├── strings.xml
│               │   └── themes.xml
├── build.gradle.kts                   # Root build script
├── settings.gradle.kts                # Project name & repository settings
└── gradle.properties                  # Memory and JVM settings
```

---

## 3. Complete Source Code For Every File

### `settings.gradle.kts` (Root)
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "PasswordKeeperAlarm"
include(":app")
```

---

### `build.gradle.kts` (Root)
```kotlin
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
```

---

### `gradle.properties` (Root)
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
```

---

### `app/build.gradle.kts`
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.passwordkeeper.alarm"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.passwordkeeper.alarm"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

---

### `app/src/main/AndroidManifest.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Permissions for strict alarms and wakeups -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <application
        android:allowBackup="true"
        android:icon="@android:drawable/ic_lock_idle_alarm"
        android:label="PasswordKeeper Alarm"
        android:roundIcon="@android:drawable/ic_lock_idle_alarm"
        android:supportsRtl="true"
        android:theme="@style/Theme.PasswordKeeperAlarm">

        <!-- Main Configuration Activity -->
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Full-Screen Alarm Activity (Shows on top of locked screen) -->
        <activity
            android:name=".alarm.AlarmActivity"
            android:excludeFromRecents="true"
            android:exported="false"
            android:launchMode="singleInstance"
            android:showOnLockScreen="true"
            android:showWhenLocked="true"
            android:turnScreenOn="true" />

        <!-- BroadcastReceiver triggered by AlarmManager -->
        <receiver
            android:name=".alarm.AlarmReceiver"
            android:exported="false" />

        <!-- Reschedule alarms on phone reboot -->
        <receiver
            android:name=".alarm.BootReceiver"
            android:enabled="true"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.QUICKBOOT_POWERON" />
            </intent-filter>
        </receiver>

        <!-- Foreground Service for continuous audio ringtone & vibration -->
        <service
            android:name=".alarm.AlarmService"
            android:exported="false"
            android:foregroundServiceType="mediaPlayback" />

    </application>

</manifest>
```

---

### `app/src/main/java/com/passwordkeeper/alarm/model/Reminder.kt`
```kotlin
package com.passwordkeeper.alarm.model

import com.google.gson.annotations.SerializedName

data class Reminder(
    val id: String,
    val title: String,
    val time: String, // HH:mm in 24h, e.g. "18:30"
    val date: String?, // YYYY-MM-DD
    val recurrence: String, // "once", "daily", "custom", "weekdays"
    @SerializedName("customDays")
    val customDays: List<Int>?, // [0 = Sun, 1 = Mon, ..., 6 = Sat]
    val enabled: Boolean,
    val notes: String?
)
```

---

### `app/src/main/java/com/passwordkeeper/alarm/alarm/AlarmScheduler.kt`
```kotlin
package com.passwordkeeper.alarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.passwordkeeper.alarm.model.Reminder
import java.util.Calendar

object AlarmScheduler {

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
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTimeMs,
            pendingIntent
        )
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
                            set(Calendar.YEAR, dParts[0].toInt())
                            set(Calendar.MONTH, dParts[1].toInt() - 1)
                            set(Calendar.DAY_OF_MONTH, dParts[2].toInt())
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
            else -> return null
        }
    }
}
```

---

### `app/src/main/java/com/passwordkeeper/alarm/alarm/AlarmReceiver.kt`
```kotlin
package com.passwordkeeper.alarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra("REMINDER_ID") ?: "default"
        val title = intent.getStringExtra("REMINDER_TITLE") ?: "Reminder"
        val time = intent.getStringExtra("REMINDER_TIME") ?: ""

        // 1. Start foreground service to play continuous ringtone & vibrate
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("REMINDER_ID", reminderId)
            putExtra("REMINDER_TITLE", title)
            putExtra("REMINDER_TIME", time)
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
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(activityIntent)
    }
}
```

---

### `app/src/main/java/com/passwordkeeper/alarm/alarm/AlarmService.kt`
```kotlin
package com.passwordkeeper.alarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat

class AlarmService : Service() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    companion object {
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
        playContinuousAlarm()

        return START_STICKY
    }

    private fun playContinuousAlarm() {
        try {
            // 1. Native Alarm Ringtone
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

            // 2. Continuous Vibration
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            val pattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0 means repeat
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlarm() {
        try {
            ringtone?.stop()
            vibrator?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
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
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
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
                setSound(null, null) // Sound is handled directly by RingtoneManager
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
```

---

### `app/src/main/java/com/passwordkeeper/alarm/alarm/AlarmActivity.kt`
```kotlin
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
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }
}
```

---

### `app/src/main/java/com/passwordkeeper/alarm/alarm/BootReceiver.kt`
```kotlin
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
                val reminders = ApiClient.fetchReminders(backendUrl)
                reminders.forEach { reminder ->
                    if (reminder.enabled) {
                        AlarmScheduler.scheduleReminder(context, reminder)
                    }
                }
            }
        }
    }
}
```

---

### `app/src/main/java/com/passwordkeeper/alarm/network/ApiClient.kt`
```kotlin
package com.passwordkeeper.alarm.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.passwordkeeper.alarm.model.Reminder
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun fetchReminders(backendBaseUrl: String): List<Reminder> {
        val cleanUrl = backendBaseUrl.trimEnd('/') + "/api/reminders"
        val request = Request.Builder()
            .url(cleanUrl)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val listType = object : TypeToken<List<Reminder>>() {}.type
            return gson.fromJson(body, listType)
        }
    }
}
```

---

### `app/src/main/java/com/passwordkeeper/alarm/MainActivity.kt`
```kotlin
package com.passwordkeeper.alarm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
            startService(serviceIntent)

            val activityIntent = Intent(this, AlarmActivity::class.java).apply {
                putExtra("REMINDER_TITLE", "Test Strict Reminder")
                putExtra("REMINDER_TIME", "Now")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(activityIntent)
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
```

---

### Layouts & Resources

#### `app/src/main/res/layout/activity_main.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#020617"
    android:orientation="vertical"
    android:padding="24dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="20dp"
        android:text="PasswordKeeper Alarm"
        android:textColor="#F8FAFC"
        android:textSize="22sp"
        android:textStyle="bold" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:text="Strict Ringtone and Vibration Companion"
        android:textColor="#94A3B8"
        android:textSize="13sp" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="32dp"
        android:text="PasswordKeeper Backend URL"
        android:textColor="#E2E8F0"
        android:textSize="12sp"
        android:textStyle="bold" />

    <EditText
        android:id="@+id/etBackendUrl"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:background="#0F172A"
        android:hint="https://your-domain.pages.dev"
        android:inputType="textUri"
        android:padding="14dp"
        android:textColor="#FFFFFF"
        android:textColorHint="#475569"
        android:textSize="14sp" />

    <Button
        android:id="@+id/btnSyncReminders"
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:layout_marginTop="16dp"
        android:backgroundTint="#10B981"
        android:text="Sync Reminders Now"
        android:textColor="#020617"
        android:textStyle="bold" />

    <Button
        android:id="@+id/btnTestAlarm"
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:layout_marginTop="12dp"
        android:backgroundTint="#1E293B"
        android:text="Test Ringtone &amp; Vibration Now"
        android:textColor="#38BDF8"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/tvStatus"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="24dp"
        android:lineSpacingExtra="4dp"
        android:text="Status: Ready to sync"
        android:textColor="#A855F7"
        android:textSize="13sp" />

</LinearLayout>
```

#### `app/src/main/res/layout/activity_alarm.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#020617"
    android:padding="32dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_centerInParent="true"
        android:gravity="center"
        android:orientation="vertical">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="⏰ ALARM"
            android:textColor="#10B981"
            android:textSize="18sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/tvAlarmTime"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:text="07:30"
            android:textColor="#FFFFFF"
            android:textSize="64sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/tvAlarmTitle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:gravity="center"
            android:text="Take medicine"
            android:textColor="#E2E8F0"
            android:textSize="24sp"
            android:textStyle="bold" />

    </LinearLayout>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_alignParentBottom="true"
        android:orientation="vertical">

        <Button
            android:id="@+id/btnSnooze"
            android:layout_width="match_parent"
            android:layout_height="60dp"
            android:backgroundTint="#334155"
            android:text="Snooze (10 mins)"
            android:textColor="#FFFFFF"
            android:textSize="16sp"
            android:textStyle="bold" />

        <Button
            android:id="@+id/btnDismiss"
            android:layout_width="match_parent"
            android:layout_height="64dp"
            android:layout_marginTop="12dp"
            android:backgroundTint="#EF4444"
            android:text="DISMISS ALARM"
            android:textColor="#FFFFFF"
            android:textSize="18sp"
            android:textStyle="bold" />

    </LinearLayout>

</RelativeLayout>
```

#### `app/src/main/res/values/themes.xml`
```xml
<resources>
    <style name="Theme.PasswordKeeperAlarm" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <item name="android:statusBarColor">#020617</item>
        <item name="android:navigationBarColor">#020617</item>
    </style>
</resources>
```

---

## 4. Cloud Build Script (`.github/workflows/build-apk.yml`)

Save this file in `.github/workflows/build-apk.yml` in the new repo. Whenever you commit or push, GitHub will automatically compile the APK!

```yaml
name: Build Android APK

on:
  push:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout Code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3

      - name: Grant Execute Permission to Gradle Wrapper
        run: |
          if [ -f gradlew ]; then
            chmod +x gradlew
          else
            gradle wrapper
            chmod +x gradlew
          fi

      - name: Build Debug APK
        run: ./gradlew assembleDebug --stacktrace

      - name: Upload APK Artifact
        uses: actions/upload-artifact@v4
        with:
          name: PasswordKeeper-Alarm-APK
          path: app/build/outputs/apk/debug/app-debug.apk
          retention-days: 14
```

---

## 5. Step-by-Step Setup Guide

1. **Create the New GitHub Repo**:
   - Go to [github.com/new](https://github.com/new).
   - Name it `passwordkeeper-alarm-app`.
   - Make it Public or Private (GitHub Actions is free for both).
2. **Push the Files**:
   - Copy the files from Section 3 and Section 4 into the new repo.
   - Commit and push to `main`.
3. **Get Your APK**:
   - Click the **Actions** tab on your GitHub repository.
   - Click the latest workflow run (takes ~90 seconds).
   - Scroll down to **Artifacts** and download `PasswordKeeper-Alarm-APK`.
4. **Install on Phone**:
   - Transfer `app-debug.apk` to your Android phone (or download it directly from GitHub in your phone's browser).
   - Tap to install (enable "Install unknown apps" if prompted).
5. **Grant Phone Permissions**:
   - Open the app, grant **Notifications** permission.
   - Go to Android Settings > Apps > PasswordKeeper Alarm:
     - Turn on **Alarms & Reminders** (Allow exact alarms).
     - Set Battery to **Unrestricted** (so Android doesn't kill alarms).
6. **Connect & Test**:
   - Paste your PasswordKeeper URL (e.g. `https://your-domain.pages.dev`).
   - Tap **Test Ringtone & Vibration Now** to verify your alarm sound.
   - Tap **Sync Reminders Now** to schedule all your web reminders into your phone!
