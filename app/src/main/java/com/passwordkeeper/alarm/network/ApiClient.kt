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
            return gson.fromJson(body, listType) ?: emptyList()
        }
    }
}
