package com.passwordkeeper.alarm.sync

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.passwordkeeper.alarm.alarm.AlarmScheduler
import com.passwordkeeper.alarm.network.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object NtfySyncManager {
    private const val TAG = "NtfySyncManager"
    private var streamJob: Job? = null
    private var currentCall: Call? = null
    private var isRunning = false
    private var currentTopic: String? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // No read timeout for continuous stream
        .retryOnConnectionFailure(true)
        .build()

    var onSyncReceived: (() -> Unit)? = null
    var onConnectionStateChanged: ((Boolean) -> Unit)? = null

    fun start(context: Context, topic: String) {
        if (isRunning && currentTopic == topic && streamJob?.isActive == true) return

        stop()
        isRunning = true
        currentTopic = topic

        startStreaming(context.applicationContext, topic)
    }

    private fun startStreaming(context: Context, topic: String) {
        streamJob = scope.launch {
            var retryDelay = 2000L
            while (isActive && isRunning) {
                try {
                    val streamUrl = "https://ntfy.sh/${topic}/json"
                    val request = Request.Builder()
                        .url(streamUrl)
                        .header("Accept", "application/x-ndjson, text/event-stream")
                        .build()

                    val call = client.newCall(request)
                    currentCall = call

                    call.execute().use { response ->
                        if (!response.isSuccessful) {
                            Log.w(TAG, "Ntfy stream returned HTTP ${response.code}. Retrying...")
                            throw Exception("HTTP ${response.code}")
                        }

                        // Connected successfully to the stream
                        Log.d(TAG, "Connected to real-time sync stream for topic: $topic")
                        retryDelay = 2000L
                        scope.launch(Dispatchers.Main) {
                            onConnectionStateChanged?.invoke(true)
                        }

                        val body = response.body ?: throw Exception("Empty response body")
                        val reader = BufferedReader(InputStreamReader(body.byteStream()))

                        while (isActive && isRunning) {
                            val line = reader.readLine() ?: break
                            if (line.isBlank()) continue

                            Log.d(TAG, "Ntfy event received: $line")

                            // Any message event triggers sync
                            if (line.contains("\"event\":\"message\"")) {
                                performSync(context)
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) break
                    Log.w(TAG, "Stream disconnected (${e.message}). Reconnecting in ${retryDelay / 1000}s...")
                    scope.launch(Dispatchers.Main) {
                        onConnectionStateChanged?.invoke(false)
                    }
                } finally {
                    currentCall = null
                }

                if (isActive && isRunning) {
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(30000L)
                }
            }
        }
    }

    suspend fun performSync(context: Context) {
        val prefs = context.getSharedPreferences("pk_alarm_prefs", Context.MODE_PRIVATE)
        val backendUrl = prefs.getString("backend_url", null) ?: return

        try {
            val reminders = ApiClient.fetchReminders(backendUrl)

            // Cache reminders locally
            val json = Gson().toJson(reminders)
            prefs.edit()
                .putString("cached_reminders_json", json)
                .putLong("last_sync_timestamp", System.currentTimeMillis())
                .apply()

            // Schedule into Android OS system clock
            reminders.forEach { reminder ->
                if (reminder.enabled) {
                    AlarmScheduler.scheduleReminder(context, reminder)
                } else {
                    AlarmScheduler.cancelReminder(context, reminder.id)
                }
            }

            Log.d(TAG, "Instant real-time sync completed: ${reminders.size} alarms active")
            scope.launch(Dispatchers.Main) {
                onSyncReceived?.invoke()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Real-time sync fetch failed: ${e.message}")
        }
    }

    fun stop() {
        isRunning = false
        try {
            currentCall?.cancel()
        } catch (_: Exception) {}
        currentCall = null

        streamJob?.cancel()
        streamJob = null
        scope.launch(Dispatchers.Main) {
            onConnectionStateChanged?.invoke(false)
        }
    }
}
