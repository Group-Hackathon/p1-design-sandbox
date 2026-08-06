package com.preappointment1.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.preappointment1.app.ui.components.checkin.CheckInScreen

/** Structured pain check-in result. */
data class PainEntry(
    val level: Int,
    val zoneIds: List<String>,
    val zoneLabels: List<String>,
    val qualities: List<String>,
    val temperature: Float = 36.6f,
    val mobilityImpact: String = "Normal",
    val temporalPattern: String = "Constant"
)

val PAIN_QUALITIES = listOf(
    "Burning", "Stabbing", "Throbbing", "Dull ache",
    "Pressing", "Tingling", "Cramping", "Shooting"
)

/**
 * PainDiaryStep wrapper redirecting to modular CheckInScreen.
 */
@Composable
fun PainDiaryStep(
    submitLabel: String,
    onSubmit: (PainEntry) -> Unit
) {
    CheckInScreen(
        submitLabel = submitLabel,
        onSubmit = { level, temp, zoneIds, zoneLabels, qualities, mobility, pattern ->
            val formattedContent = buildString {
                append("Pain Level: $level/10")
                if (zoneLabels.isNotEmpty()) append(" (${zoneLabels.joinToString(", ")})")
                append(" • Temp: ${String.format(java.util.Locale.US, "%.1f", temp)}°C")
                append(" • Mobility: $mobility")
                append(" • Pattern: $pattern")
                if (qualities.isNotEmpty()) append("\nCharacteristics: ${qualities.joinToString(", ")}")
            }

            onSubmit(
                PainEntry(
                    level = level,
                    zoneIds = zoneIds,
                    zoneLabels = zoneLabels,
                    qualities = qualities,
                    temperature = temp,
                    mobilityImpact = mobility,
                    temporalPattern = pattern
                )
            )
        }
    )
}
