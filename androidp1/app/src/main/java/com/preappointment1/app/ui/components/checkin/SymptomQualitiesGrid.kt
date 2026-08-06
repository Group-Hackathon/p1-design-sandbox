package com.preappointment1.app.ui.components.checkin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.preappointment1.app.ui.theme.*

val PAIN_QUALITIES = listOf(
    "Burning", "Stabbing", "Throbbing", "Dull ache",
    "Pressing", "Tingling", "Cramping", "Shooting"
)

/**
 * Symptom descriptor chips — black & white.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SymptomQualitiesGrid(
    selectedQualities: Set<String>,
    onToggleQuality: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "What does it feel like?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Optional — pick all that apply",
                style = MaterialTheme.typography.bodySmall,
                color = Gray500
            )
            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PAIN_QUALITIES.forEach { quality ->
                    val isSelected = selectedQualities.contains(quality)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggleQuality(quality) },
                        label = { Text(quality, fontWeight = FontWeight.Medium) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Black,
                            selectedLabelColor = White,
                            containerColor = Gray100,
                            labelColor = Black
                        )
                    )
                }
            }
        }
    }
}
