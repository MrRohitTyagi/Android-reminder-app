# PasswordKeeper Alarm Companion App

Native Android Companion Alarm app for PasswordKeeper.

- **Continuous Ringtone & Vibration**: Uses Android's native `RingtoneManager.TYPE_ALARM` audio stream and continuous vibration until dismissed or snoozed.
- **Wakes Locked Screens**: Utilizes `showWhenLocked`, `turnScreenOn`, and full-screen intents.
- **Exact Timing**: Schedules system-level alarms with `AlarmManager.setExactAndAllowWhileIdle`.
- **Offline & Reboot Persistent**: Alarms ring even without internet once synced and reschedule automatically on boot (`RECEIVE_BOOT_COMPLETED`).
- **Automated Cloud Builds**: Builds via GitHub Actions workflow (`.github/workflows/build-apk.yml`).

## Project Structure

```
├── .github/workflows/build-apk.yml
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/passwordkeeper/alarm/
│       │   ├── MainActivity.kt
│       │   ├── alarm/
│       │   │   ├── AlarmActivity.kt
│       │   │   ├── AlarmReceiver.kt
│       │   │   ├── AlarmScheduler.kt
│       │   │   ├── AlarmService.kt
│       │   │   └── BootReceiver.kt
│       │   ├── model/
│       │   │   └── Reminder.kt
│       │   └── network/
│       │       └── ApiClient.kt
│       └── res/
│           ├── layout/
│           │   ├── activity_alarm.xml
│           │   └── activity_main.xml
│           └── values/
│               ├── colors.xml
│               ├── strings.xml
│               └── themes.xml
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── settings.gradle.kts
```

## How to Build the APK

1. Push this repository to GitHub (e.g. `main` or `master` branch).
2. Go to the **Actions** tab in your repository.
3. Once the build completes (~1-2 minutes), download the **PasswordKeeper-Alarm-APK** artifact.
4. Install `app-debug.apk` on your phone.
