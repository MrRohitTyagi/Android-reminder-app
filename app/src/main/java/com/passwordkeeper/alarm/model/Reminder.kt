package com.passwordkeeper.alarm.model

import com.google.gson.annotations.SerializedName

data class Reminder(
    val id: String,
    val title: String,
    val time: String, // HH:mm in 24h, e.g. "18:30"
    val date: String? = null, // YYYY-MM-DD
    val recurrence: String? = "once", // "once", "daily", "custom", "weekdays"
    @SerializedName("customDays")
    val customDays: List<Int>? = null, // [0 = Sun, 1 = Mon, ..., 6 = Sat]
    val enabled: Boolean = true,
    val notes: String? = null
)
