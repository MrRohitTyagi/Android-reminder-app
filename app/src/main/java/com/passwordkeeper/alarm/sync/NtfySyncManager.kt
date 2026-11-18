package com.passwordkeeper.alarm.sync

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.passwordkeeper.alarm.alarm.AlarmScheduler
import com.passwordkeeper.alarm.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

object NtfySyncManager {
    private const val TAG = "NtfySyncManager"
    private var webSocket: WebSocket? = null
    private var isRunning = false
    private var currentTopic: String? = null
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    var onSyncReceived: (() -> Unit)? = null

    fun start(context: Context, topic: String) {
        if (isRunning && currentTopic == topic && webSocket != null) return

        stop()
        isRunning = true
        currentTopic = topic

        connect(context.applicationContext, topic)
    }

    private fun connect(context: Context, topic: String) {
        if (!isRunning) return

        val wsUrl = "wss://ntfy.sh/${topic}/ws"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Connected to real-time sync topic: $topic")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Real-time sync event received: $text")
                scope.launch {
                    performSync(context)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Real-time sync socket disconnected: ${t.message}. Reconnecting in 10s...")
                scheduleReconnect(context, topic)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Real-time sync socket closed: $reason. Reconnecting in 10s...")
                scheduleReconnect(context, topic)
            }
        })
    }

    private fun scheduleReconnect(context: Context, topic: String) {
        if (!isRunning) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(10000)
            if (isRunning) {
                connect(context, topic)
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
        reconnectJob?.cancel()
        reconnectJob = null
        try {
            webSocket?.close(1000, "Stop requested")
        } catch (_: Exception) {}
        webSocket = null
    }
}
