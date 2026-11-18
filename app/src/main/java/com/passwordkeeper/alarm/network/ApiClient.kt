package com.passwordkeeper.alarm.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.passwordkeeper.alarm.model.Reminder
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

object ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun fetchReminders(backendBaseUrl: String): List<Reminder> {
        val now = Calendar.getInstance()
        val currentDate = String.format(
            Locale.US,
            "%04d-%02d-%02d",
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH) + 1,
            now.get(Calendar.DAY_OF_MONTH)
        )
        val currentTime = String.format(
            Locale.US,
            "%02d:%02d",
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE)
        )

        val cleanUrl = backendBaseUrl.trimEnd('/') + "/api/reminders?currentDate=$currentDate&currentTime=$currentTime"
        val request = Request.Builder()
            .url(cleanUrl)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val listType = object : TypeToken<List<Reminder>>() {}.type
                gson.fromJson(body, listType) ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun deleteReminder(backendBaseUrl: String, reminderId: String): Boolean {
        val cleanUrl = backendBaseUrl.trimEnd('/') + "/api/reminders/" + reminderId
        val request = Request.Builder()
            .url(cleanUrl)
            .delete()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    fun fetchSyncTopic(backendBaseUrl: String): String? {
        val cleanUrl = backendBaseUrl.trimEnd('/') + "/api/reminders/sync-channel"
        val request = Request.Builder()
            .url(cleanUrl)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val map = gson.fromJson<Map<String, Any>>(body, object : TypeToken<Map<String, Any>>() {}.type)
                map["topic"] as? String
            }
        } catch (e: Exception) {
            null
        }
    }
}
