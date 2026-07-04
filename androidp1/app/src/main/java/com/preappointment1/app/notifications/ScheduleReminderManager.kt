package com.preappointment1.app.notifications

import android.content.Context
import com.preappointment1.app.data.SessionManager
import com.preappointment1.app.ui.screens.FollowUpUi
import java.time.LocalTime

object ScheduleReminderManager {
    private const val PREFS = "schedule_reminders"
    private const val KEY_ACTIVE_SUB = "active_subscription_id"
    private const val KEY_SCHEDULED_SUBS = "scheduled_subscription_ids"
    private const val MAX_SLOTS_PER_SUB = 12

    fun requestCode(subscriptionId: String, slotIndex: Int): Int {
        return (subscriptionId.hashCode() and 0x7FFF) + slotIndex + 1
    }

    fun scheduleForFollowUp(
        context: Context,
        subscriptionId: String,
        title: String,
        schedule: Map<String, List<String>>
    ) {
        cancelForFollowUp(context, subscriptionId)
        NotificationHelper.createNotificationChannel(context)
        scheduleAlarms(context, subscriptionId, title, schedule)
        registerScheduledFollowUp(context, subscriptionId)
    }

    fun cancelForFollowUp(context: Context, subscriptionId: String) {
        for (index in 0 until MAX_SLOTS_PER_SUB) {
            NotificationHelper.cancelReminder(context, requestCode(subscriptionId, index))
        }
        unregisterScheduledFollowUp(context, subscriptionId)
    }

    fun getActiveSubscriptionId(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE_SUB, null)
    }

    fun rescheduleActiveFollowUps(context: Context, followUps: List<FollowUpUi>) {
        val active = followUps.filter { it.daysRemaining > 0 && !it.schedule.isNullOrEmpty() }
        val activeIds = active.map { it.id }.toSet()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previouslyScheduled = prefs.getStringSet(KEY_SCHEDULED_SUBS, emptySet()) ?: emptySet()

        previouslyScheduled.filter { it !in activeIds }.forEach { cancelForFollowUp(context, it) }

        active.forEach { followUp ->
            scheduleForFollowUp(context, followUp.id, followUp.title, followUp.schedule!!)
        }

        prefs.edit()
            .putStringSet(KEY_SCHEDULED_SUBS, activeIds)
            .putString(KEY_ACTIVE_SUB, active.maxByOrNull { it.startsAt }?.id)
            .apply()
    }

    fun remindersFromSchedule(schedule: Map<String, List<String>>): List<ScheduleReminderUi> {
        return schedule.mapNotNull { (timeKey, actions) ->
            runCatching { LocalTime.parse(timeKey) }.getOrNull()?.let { time ->
                ScheduleReminderUi(
                    timeKey = timeKey,
                    displayTime = formatDisplayTime(time),
                    measures = formatActions(actions)
                )
            }
        }.sortedBy { it.timeKey }
    }

    fun firstScheduleKey(schedule: Map<String, List<String>>): String? {
        return remindersFromSchedule(schedule).firstOrNull()?.timeKey
    }

    private fun scheduleAlarms(
        context: Context,
        subscriptionId: String,
        title: String,
        schedule: Map<String, List<String>>
    ) {
        val slots = schedule.mapNotNull { (timeKey, actions) ->
            runCatching {
                val time = LocalTime.parse(timeKey)
                Triple(timeKey, time.hour, time.minute) to actions
            }.getOrNull()
        }.sortedBy { it.first.third }.sortedBy { it.first.second }

        slots.forEachIndexed { index, (timeInfo, actions) ->
            val (timeKey, hour, minute) = timeInfo
            val measures = formatActions(actions)
            val firstName = SessionManager.getUserName()?.trim()?.split(" ")?.firstOrNull()
            val notifTitle = if (firstName != null) {
                "Hi $firstName — check-in time"
            } else {
                "Time for your check-in"
            }
            val message = buildString {
                append("About 90 seconds to add today")
                if (measures.isNotBlank()) {
                    append(" ($measures)")
                }
                append(" to your file for ")
                append(title)
                append('.')
            }
            NotificationHelper.scheduleDailyReminder(
                context = context,
                hour = hour,
                minute = minute,
                requestCode = requestCode(subscriptionId, index),
                title = notifTitle,
                message = message,
                subscriptionId = subscriptionId,
                scheduleKey = timeKey
            )
        }
    }

    private fun registerScheduledFollowUp(context: Context, subscriptionId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(KEY_SCHEDULED_SUBS, emptySet())?.toMutableSet() ?: mutableSetOf()
        ids.add(subscriptionId)
        prefs.edit()
            .putStringSet(KEY_SCHEDULED_SUBS, ids)
            .putString(KEY_ACTIVE_SUB, subscriptionId)
            .apply()
    }

    private fun unregisterScheduledFollowUp(context: Context, subscriptionId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(KEY_SCHEDULED_SUBS, emptySet())?.toMutableSet() ?: mutableSetOf()
        ids.remove(subscriptionId)
        prefs.edit().putStringSet(KEY_SCHEDULED_SUBS, ids).apply()
    }

    private fun formatActions(actions: List<String>): String {
        return actions.map { action ->
            when (action.lowercase()) {
                "pain" -> "pain level"
                "temperature" -> "temperature"
                "photo" -> "photo"
                else -> action
            }
        }.joinToString(", ")
    }

    private fun formatDisplayTime(time: LocalTime): String {
        val hour = time.hour
        val minute = time.minute
        val amPm = if (hour < 12) "AM" else "PM"
        val h12 = when (val h = hour % 12) {
            0 -> 12
            else -> h
        }
        return if (minute == 0) "$h12:00 $amPm" else String.format("%d:%02d %s", h12, minute, amPm)
    }
}

data class ScheduleReminderUi(
    val timeKey: String,
    val displayTime: String,
    val measures: String
)
