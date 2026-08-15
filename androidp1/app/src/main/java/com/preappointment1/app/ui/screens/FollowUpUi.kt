package com.preappointment1.app.ui.screens

import com.preappointment1.app.data.model.AgentResponse
import com.preappointment1.app.data.model.FollowUpRules
import com.preappointment1.app.data.model.SubscriptionResponse
import java.time.Instant
import java.time.temporal.ChronoUnit

data class FollowUpUi(
    val id: String,
    val title: String,
    val daysRemaining: Int,
    val totalDays: Int,
    val progress: Float,
    val isActive: Boolean,
    val rules: FollowUpRules,
    val schedule: Map<String, List<String>>?,
    val startsAt: String = "",
    val expiresAt: String = ""
)

fun SubscriptionResponse.toFollowUpUi(agents: Map<String, AgentResponse>): FollowUpUi {
    val startInstant = runCatching { Instant.parse(starts_at) }.getOrNull() ?: Instant.now()
    val endInstant = runCatching { Instant.parse(expires_at) }.getOrNull()
        ?: startInstant.plus(14, ChronoUnit.DAYS)
    val now = Instant.now()

    val totalDays = ChronoUnit.DAYS.between(startInstant, endInstant).coerceAtLeast(1).toInt()
    val elapsedDays = ChronoUnit.DAYS.between(startInstant, now).coerceAtLeast(0)
    val daysRemaining = ChronoUnit.DAYS.between(now, endInstant).coerceAtLeast(0).toInt()
    val progress = (elapsedDays.toFloat() / totalDays.toFloat()).coerceIn(0f, 1f)

    val agent = agents[agent_id]
    val title = parameters?.get("title")?.toString()
        ?: agent?.name
        ?: "Tracking from ${startInstant.toString().take(10)}"

    val rulesMap = parameters?.get("rules") as? Map<*, *>
    val rules = if (rulesMap != null) {
        FollowUpRules(
            temperature = rulesMap["temperature"] as? Boolean ?: false,
            pain = rulesMap["pain"] as? Boolean ?: false,
            photos = rulesMap["photos"] as? Boolean ?: false,
            smartwatch = rulesMap["smartwatch"] as? Boolean ?: false,
            bloodPressure = rulesMap["blood_pressure"] as? Boolean ?: false
        )
    } else {
        FollowUpRules(
            temperature = false,
            pain = false,
            photos = false,
            smartwatch = false,
            bloodPressure = false
        )
    }

    val schedule = (parameters?.get("schedule") as? Map<*, *>)?.mapNotNull { (key, value) ->
        val slotKey = key as? String ?: return@mapNotNull null
        val actions = (value as? List<*>)?.mapNotNull { it as? String } ?: return@mapNotNull null
        slotKey to actions
    }?.toMap()

    return FollowUpUi(
        id = id,
        title = title,
        daysRemaining = daysRemaining,
        totalDays = totalDays,
        progress = progress,
        isActive = now.isBefore(endInstant),
        rules = rules,
        schedule = schedule,
        startsAt = starts_at,
        expiresAt = expires_at
    )
}
