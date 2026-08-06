package com.preappointment1.app.ui.components.checkin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.preappointment1.app.ui.theme.*

/**
 * EVA 0-10 pain intensity gauge — black & white design only.
 */
@Composable
fun PainIntensityGauge(
    level: Int,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val painDesc = when (level) {
        0 -> "No pain"
        in 1..3 -> "Mild"
        in 4..6 -> "Moderate"
        in 7..8 -> "Severe"
        else -> "Extreme"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Pain intensity",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Black
                )
                Box(
                    modifier = Modifier
                        .background(Gray100, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        "$level / 10  •  $painDesc",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                for (i in 0..10) {
                    val isFilled = i <= level
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isFilled) Black else Gray200)
                            .clickable { onLevelChange(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$i",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isFilled) White else Gray500
                        )
                    }
                }
            }
        }
    }
}
