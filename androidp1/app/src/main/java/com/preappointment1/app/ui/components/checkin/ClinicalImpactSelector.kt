package com.preappointment1.app.ui.components.checkin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preappointment1.app.ui.theme.*

/**
 * Clinical impact: mobility + temporal pattern — black & white.
 */
@Composable
fun ClinicalImpactSelector(
    mobilityImpact: String,
    onMobilityChange: (String) -> Unit,
    temporalPattern: String,
    onPatternChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val mobilityOptions = listOf("Normal", "Restricted", "Impaired")
    val patternOptions = listOf("Constant", "Intermittent", "Morning", "Evening")

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Mobility impact",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Black
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                mobilityOptions.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (mobilityImpact == key) Black else Gray100)
                            .border(1.dp, if (mobilityImpact == key) Black else CardBorder, RoundedCornerShape(10.dp))
                            .clickable { onMobilityChange(key) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            key,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (mobilityImpact == key) White else Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Temporal pattern",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Black
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                patternOptions.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (temporalPattern == key) Black else Gray100)
                            .border(1.dp, if (temporalPattern == key) Black else CardBorder, RoundedCornerShape(10.dp))
                            .clickable { onPatternChange(key) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            key,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (temporalPattern == key) White else Black,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
