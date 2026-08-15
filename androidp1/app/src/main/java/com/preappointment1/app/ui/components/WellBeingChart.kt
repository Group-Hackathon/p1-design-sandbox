package com.preappointment1.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preappointment1.app.R
import com.preappointment1.app.data.model.TimelineEventResponse
import com.preappointment1.app.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun WellBeingTrendCard(
    timelineEvents: List<TimelineEventResponse> = emptyList(),
    modifier: Modifier = Modifier
) {
    val (status, points) = remember(timelineEvents) {
        computeTrendFromEvents(timelineEvents)
    }

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
                        text = stringResource(R.string.home_insights_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.home_insights_wellness),
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
                        text = status,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MintBadgeText
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Smooth Spline Chart Canvas
            SplineWaveChart(
                points = points,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            )
        }
    }
}

private fun computeTrendFromEvents(events: List<TimelineEventResponse>): Pair<String, List<Float>> {
    if (events.isEmpty()) {
        return "Getting Started" to listOf(0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f)
    }

    val today = LocalDate.now()
    val dayPoints = mutableListOf<Float>()

    for (i in 6 downTo 0) {
        val targetDate = today.minusDays(i.toLong())
        val datePrefix = targetDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

        val dayEvents = events.filter { event ->
            event.created_at.startsWith(datePrefix) || event.effective_at?.startsWith(datePrefix) == true
        }

        if (dayEvents.isEmpty()) {
            // Default baseline if no entries on this day
            val prev = dayPoints.lastOrNull() ?: 0.5f
            dayPoints.add(prev)
        } else {
            // Calculate wellness score (1.0 = best, 0.1 = severe pain/unwell)
            var scoreSum = 0f
            var count = 0
            for (event in dayEvents) {
                val content = event.content.lowercase()
                val painMatch = Regex("""pain[:\s]+(\d+)""").find(content)
                if (painMatch != null) {
                    val painVal = painMatch.groupValues[1].toFloatOrNull() ?: 0f
                    // Lower pain = higher wellness (10 pain -> 0.1 score, 0 pain -> 0.95 score)
                    scoreSum += (10f - painVal.coerceIn(0f, 10f)) / 10f * 0.85f + 0.15f
                    count++
                } else if (content.contains("better")) {
                    scoreSum += 0.85f
                    count++
                } else if (content.contains("worse") || content.contains("unwell")) {
                    scoreSum += 0.25f
                    count++
                } else {
                    scoreSum += 0.65f
                    count++
                }
            }
            val avg = if (count > 0) (scoreSum / count).coerceIn(0.1f, 1.0f) else 0.5f
            dayPoints.add(avg)
        }
    }

    // Determine status text
    val first = dayPoints.take(3).average()
    val last = dayPoints.takeLast(3).average()
    val status = when {
        last > first + 0.1 -> "Improving"
        last < first - 0.1 -> "Needs Attention"
        else -> "Stable"
    }

    return status to dayPoints
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
            val y = height - (normY * height * 0.75f) - (height * 0.12f)
            Offset(x, y)
        }

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

        val fillPath = Path().apply {
            addPath(strokePath)
            lineTo(coords.last().x, height)
            lineTo(coords.first().x, height)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    SagePrimary.copy(alpha = 0.22f),
                    MintBadge.copy(alpha = 0.04f)
                ),
                startY = 0f,
                endY = height
            )
        )

        drawPath(
            path = strokePath,
            color = SagePrimary,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        )
    }
}
