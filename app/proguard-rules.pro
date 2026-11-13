# Proguard rules for PasswordKeeper Alarm
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.passwordkeeper.alarm.model.** { *; }
