package com.passwordkeeper.alarm.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.passwordkeeper.alarm.R
import com.passwordkeeper.alarm.model.Reminder

class ReminderAdapter : RecyclerView.Adapter<ReminderAdapter.ReminderViewHolder>() {

    private val reminders = mutableListOf<Reminder>()

    fun submitList(newReminders: List<Reminder>) {
        reminders.clear()
        reminders.addAll(newReminders)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_reminder, parent, false)
        return ReminderViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        holder.bind(reminders[position])
    }

    override fun getItemCount(): Int = reminders.size

    class ReminderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTime: TextView = itemView.findViewById(R.id.tvItemTime)
        private val tvRecurrence: TextView = itemView.findViewById(R.id.tvItemRecurrence)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvItemTitle)
        private val tvNotes: TextView = itemView.findViewById(R.id.tvItemNotes)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvItemStatus)

        fun bind(reminder: Reminder) {
            tvTime.text = reminder.time
            tvTitle.text = reminder.title

            // Format recurrence label
            tvRecurrence.text = when (reminder.recurrence?.lowercase()) {
                "daily" -> "Daily"
                "weekdays" -> "Mon - Fri"
                "once" -> if (!reminder.date.isNullOrEmpty()) reminder.date else "Once"
                "custom" -> formatCustomDays(reminder.customDays)
                else -> reminder.recurrence?.replaceFirstChar { it.uppercase() } ?: "Alarm"
            }

            // Notes
            if (!reminder.notes.isNullOrEmpty()) {
                tvNotes.text = reminder.notes
                tvNotes.visibility = View.VISIBLE
            } else {
                tvNotes.visibility = View.GONE
            }

            // Status badge
            if (reminder.enabled) {
                tvStatus.text = "Active"
                tvStatus.setBackgroundResource(R.drawable.bg_badge_active)
                tvStatus.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.holo_green_light))
            } else {
                tvStatus.text = "Off"
                tvStatus.setBackgroundResource(R.drawable.bg_badge_disabled)
                tvStatus.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.darker_gray))
            }
        }

        private fun formatCustomDays(days: List<Int>?): String {
            if (days.isNullOrEmpty()) return "Custom"
            val names = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            return days.sorted().mapNotNull { if (it in names.indices) names[it] else null }.joinToString(", ")
        }
    }
}
