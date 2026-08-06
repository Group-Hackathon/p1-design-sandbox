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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preappointment1.app.ui.theme.*
import java.util.Locale

/**
 * Medical temperature gauge — black & white design only.
 */
@Composable
fun TemperatureGauge(
    temperature: Float,
    onTemperatureChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val tempStatusLabel = when {
        temperature < 37.5f -> "Normal"
        temperature < 38.1f -> "Low-grade fever"
        temperature < 39.1f -> "Moderate fever"
        else -> "High fever"
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
                    "Temperature",
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
                        String.format(Locale.US, "%.1f °C  •  %s", temperature, tempStatusLabel),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Slider(
                value = temperature,
                onValueChange = onTemperatureChange,
                valueRange = 35.5f..41.0f,
                steps = 54,
                colors = SliderDefaults.colors(
                    thumbColor = Black,
                    activeTrackColor = Black,
                    inactiveTrackColor = Gray200
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("35.5°C", style = MaterialTheme.typography.labelSmall, color = Gray400)
                Text("37.0°C", style = MaterialTheme.typography.labelSmall, color = Gray500)
                Text("38.5°C", style = MaterialTheme.typography.labelSmall, color = Gray500)
                Text("41.0°C", style = MaterialTheme.typography.labelSmall, color = Gray400)
            }
        }
    }
}
