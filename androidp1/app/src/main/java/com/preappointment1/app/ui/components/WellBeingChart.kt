package com.preappointment1.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preappointment1.app.ui.theme.*

@Composable
fun WellBeingTrendCard(
    statusText: String = "Stable",
    dataPoints: List<Float> = listOf(0.35f, 0.45f, 0.40f, 0.65f, 0.50f, 0.70f, 0.68f),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CardBackground)
            .padding(20.dp)
    ) {
        Column {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Your last 7 days",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Overall well-being",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }

                // Pill Tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MintBadge)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MintBadgeText
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Smooth Spline Chart Canvas
            SplineWaveChart(
                points = dataPoints,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            )
        }
    }
}

@Composable
private fun SplineWaveChart(
    points: List<Float>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (points.isEmpty()) return@Canvas

        val width = size.width
        val height = size.height
        val stepX = width / (points.size - 1).coerceAtLeast(1)

        val coords = points.mapIndexed { index, normY ->
            val x = index * stepX
            // Invert Y since 0 is top
            val y = height - (normY * height * 0.75f) - (height * 0.12f)
            Offset(x, y)
        }

        // Build smooth Cubic Bezier Path
        val strokePath = Path().apply {
            moveTo(coords.first().x, coords.first().y)
            for (i in 0 until coords.size - 1) {
                val p0 = coords[i]
                val p1 = coords[i + 1]
                val controlX = (p0.x + p1.x) / 2f
                cubicTo(
                    controlX, p0.y,
                    controlX, p1.y,
                    p1.x, p1.y
                )
            }
        }

        // Build filled gradient path
        val fillPath = Path().apply {
            addPath(strokePath)
            lineTo(coords.last().x, height)
            lineTo(coords.first().x, height)
            close()
        }

        // Draw Fill Gradient
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    SagePrimary.copy(alpha = 0.20f),
                    MintBadge.copy(alpha = 0.05f)
                ),
                startY = 0f,
                endY = height
            )
        )

        // Draw Smooth Stroke Line
        drawPath(
            path = strokePath,
            color = SagePrimary,
            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw Dots on points
        coords.forEachIndexed { idx, point ->
            // Highlight specific days or all points cleanly
            if (idx == 0 || idx == 2 || idx == 4 || idx == coords.size - 1) {
                drawCircle(
                    color = SagePrimary,
                    radius = 4.dp.toPx(),
                    center = point
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.dp.toPx(),
                    center = point
                )
            }
        }
    }
}
