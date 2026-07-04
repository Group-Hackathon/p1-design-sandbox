package com.preappointment1.app.ui.support

import com.preappointment1.app.data.model.TimelineEventResponse
import com.preappointment1.app.ui.screens.FollowUpUi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class FileReadiness(
    val readinessPercent: Int,
    val measurementCount: Int,
    val photoCount: Int,
    val noteCount: Int,
    val expectedMeasurements: Int,
    val dayStreak: Int,
    val missingHint: String?,
    val tagline: String
)

object FileStats {

    fun compute(followUp: FollowUpUi, events: List<TimelineEventResponse>): FileReadiness {
        val userEvents = events.filter { it.type == "user" }
        val measurements = userEvents.filter { !isNoteEvent(it) }
        val measurementCount = measurements.size
        val photoCount = measurements.count { it.content.contains("Photo:", ignoreCase = true) }
        val noteCount = userEvents.count { isNoteEvent(it) }

        val slotsPerDay = followUp.schedule?.size?.coerceAtLeast(1) ?: 1
        val expectedMeasurements = (followUp.totalDays * slotsPerDay).coerceAtLeast(1)
        val readinessPercent = ((measurementCount.toFloat() / expectedMeasurements) * 100)
            .toInt()
            .coerceIn(0, 100)

        val dayStreak = computeStreak(measurements)
        val missingHint = buildMissingHint(readinessPercent, followUp.daysRemaining, measurementCount, expectedMeasurements)
        val tagline = dashboardTagline(followUp.daysRemaining, readinessPercent)

        return FileReadiness(
            readinessPercent = readinessPercent,
            measurementCount = measurementCount,
            photoCount = photoCount,
            noteCount = noteCount,
            expectedMeasurements = expectedMeasurements,
            dayStreak = dayStreak,
            missingHint = missingHint,
            tagline = tagline
        )
    }

    fun checkInSuccessMessage(daysRemaining: Int, readinessPercent: Int): String = when {
        daysRemaining == 0 -> "Added to your file. Good luck at your appointment today."
        daysRemaining <= 3 && readinessPercent >= 80 ->
            "Noted — thank you. Your briefing is nearly complete ($readinessPercent% ready)."
        daysRemaining <= 3 ->
            "Noted — thank you. $daysRemaining days until your appointment."
        readinessPercent >= 50 ->
            "Saved to your file — $readinessPercent% ready for your doctor."
        else -> "Noted — thank you. We'll take care of the rest."
    }

    fun noteSuccessMessage(): String =
        "Your note was added to the file for your doctor."

    fun greetingPrefix(): String {
        val hour = java.time.LocalTime.now().hour
        return when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    fun dashboardTagline(daysRemaining: Int, readiness: Int): String = when {
        daysRemaining == 0 -> "Appointment day — your file is ready to share with your doctor."
        daysRemaining <= 3 && readiness >= 70 -> "Almost there. Your doctor will have a clear picture."
        daysRemaining <= 3 -> "Every entry counts — $daysRemaining days until your appointment."
        readiness == 0 -> "Start today — your future self will thank you at the appointment."
        readiness < 40 -> "You're building something useful. Keep adding a little each day."
        readiness < 80 -> "Your file is taking shape. Steady progress."
        else -> "Strong file so far. Stay consistent until appointment day."
    }

    private fun isNoteEvent(event: TimelineEventResponse): Boolean =
        event.date_label.equals("Question", ignoreCase = true)

    private fun buildMissingHint(
        readiness: Int,
        daysRemaining: Int,
        actual: Int,
        expected: Int
    ): String? {
        val remaining = expected - actual
        return when {
            daysRemaining == 0 && readiness >= 80 -> null
            daysRemaining == 0 -> "A few more entries will strengthen your file for today."
            remaining <= 0 -> null
            readiness >= 90 -> "Almost complete — keep going."
            remaining == 1 -> "One more check-in fills a gap in your file."
            else -> "$remaining check-ins still to capture before your appointment."
        }
    }

    private fun computeStreak(measurements: List<TimelineEventResponse>): Int {
        if (measurements.isEmpty()) return 0
        val zone = ZoneId.systemDefault()
        val datesWithActivity = measurements.mapNotNull { event ->
            parseEventDate(event)?.atZone(zone)?.toLocalDate()
        }.toSet()

        var streak = 0
        var day = LocalDate.now()
        while (day in datesWithActivity) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    private fun parseEventDate(event: TimelineEventResponse): Instant? =
        runCatching {
            Instant.parse(event.effective_at ?: event.created_at)
        }.getOrNull()
}
