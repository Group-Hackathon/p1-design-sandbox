package com.preappointment1.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preappointment1.app.R
import com.preappointment1.app.data.model.AgentResponse
import com.preappointment1.app.data.model.SubscriptionResponse
import com.preappointment1.app.data.model.TimelineEventResponse
import com.preappointment1.app.ui.components.*
import com.preappointment1.app.ui.support.FileReadiness
import com.preappointment1.app.ui.support.FileStats
import com.preappointment1.app.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class FollowUpUi(
    val id: String,
    val title: String,
    val daysRemaining: Int,
    val totalDays: Int,
    val progress: Float,
    val isActive: Boolean,
    val rules: com.preappointment1.app.data.model.FollowUpRules?,
    val schedule: Map<String, List<String>>?,
    val startsAt: String = "",
    val expiresAt: String = ""
)

@Composable
fun DashboardScreen(
    followUps: List<FollowUpUi>,
    timelineByFollowUpId: Map<String, List<TimelineEventResponse>>,
    patientName: String?,
    isLoading: Boolean,
    onNewFollowUp: () -> Unit,
    onOpenJourney: (FollowUpUi) -> Unit,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showComingSoon by remember { mutableStateOf(false) }

    if (showComingSoon) {
        ComingSoonDialog(onDismiss = { showComingSoon = false })
    }

    val activeFollowUps = followUps.filter { it.isActive }
    val greeting = FileStats.greetingPrefix()
    val firstName = patientName?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() }

    Scaffold(
        containerColor = White,
        modifier = modifier
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Black)
            }
        } else if (activeFollowUps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_empty_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = Black,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.dashboard_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Gray600,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    LpmPrimaryButton(
                        text = stringResource(R.string.dashboard_empty_action),
                        onClick = onNewFollowUp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
            ) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                        Text(
                            text = if (firstName != null) {
                                stringResource(R.string.dashboard_greeting_named, greeting, firstName)
                            } else {
                                stringResource(R.string.dashboard_greeting, greeting)
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = Black
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.dashboard_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Gray600
                        )
                    }
                }

                items(activeFollowUps) { followUp ->
                    val events = timelineByFollowUpId[followUp.id] ?: emptyList()
                    val readiness = FileStats.compute(followUp, events)
                    FollowUpFileCard(
                        followUp = followUp,
                        readiness = readiness,
                        onClick = { onOpenJourney(followUp) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FollowUpFileCard(
    followUp: FollowUpUi,
    readiness: FileReadiness,
    onClick: () -> Unit
) {
    LpmCard(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = followUp.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Black
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (followUp.daysRemaining == 0) {
                            stringResource(R.string.file_appt_today)
                        } else {
                            stringResource(R.string.file_appt_countdown, followUp.daysRemaining)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Gray600
                    )
                }
                Text(
                    text = stringResource(R.string.file_ready_percent, readiness.readinessPercent),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = Black
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            LpmProgressBar(progress = readiness.readinessPercent / 100f)

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(
                    R.string.file_contents,
                    readiness.measurementCount,
                    readiness.photoCount,
                    readiness.noteCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Gray600
            )

            if (readiness.dayStreak >= 2) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.file_streak, readiness.dayStreak),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Black
                )
            }

            readiness.missingHint?.let { hint ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray600,
                    lineHeight = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = readiness.tagline,
                style = MaterialTheme.typography.bodySmall,
                color = Black,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.file_continue),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Black
            )
        }
    }
}

@Composable
fun ComingSoonDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        shape = RoundedCornerShape(4.dp),
        title = {
            Text(
                "Coming soon",
                fontWeight = FontWeight.Bold,
                color = Black
            )
        },
        text = {
            Text(
                "Scanning prescriptions and doctor protocols will be available in a future update.",
                color = Gray600
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", color = Black, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

fun SubscriptionResponse.toFollowUpUi(agents: Map<String, AgentResponse>): FollowUpUi {
    val start = runCatching { Instant.parse(starts_at) }.getOrNull() ?: Instant.now()
    val end = runCatching { Instant.parse(expires_at) }.getOrNull() ?: start.plus(14, ChronoUnit.DAYS)
    val now = Instant.now()
    val totalDays = ChronoUnit.DAYS.between(start, end).coerceAtLeast(1).toInt()
    val elapsedDays = ChronoUnit.DAYS.between(start, now).coerceAtLeast(0)
    val daysRemaining = ChronoUnit.DAYS.between(now, end).coerceAtLeast(0).toInt()
    val progress = (elapsedDays.toFloat() / totalDays.toFloat()).coerceIn(0f, 1f)
    val startDateStr = start.toString().take(10)
    val agent = agents[agent_id]
    val title = parameters?.get("title")?.toString() ?: agent?.name ?: "Tracking from $startDateStr"

    var parsedRules: com.preappointment1.app.data.model.FollowUpRules? = null
    val rulesMap = parameters?.get("rules") as? Map<*, *>
    if (rulesMap != null) {
        parsedRules = com.preappointment1.app.data.model.FollowUpRules(
            temperature = rulesMap["temperature"] as? Boolean ?: false,
            pain = rulesMap["pain"] as? Boolean ?: false,
            photos = rulesMap["photos"] as? Boolean ?: false,
            smartwatch = rulesMap["smartwatch"] as? Boolean ?: false,
            bloodPressure = rulesMap["blood_pressure"] as? Boolean ?: false
        )
    }

    var parsedSchedule: Map<String, List<String>>? = null
    val scheduleMap = parameters?.get("schedule") as? Map<*, *>
    if (scheduleMap != null) {
        parsedSchedule = scheduleMap.mapNotNull { (k, v) ->
            val key = k as? String
            val valueList = (v as? List<*>)?.mapNotNull { it as? String }
            if (key != null && valueList != null) key to valueList else null
        }.toMap()
    }

    return FollowUpUi(
        id = id,
        title = title,
        daysRemaining = daysRemaining,
        totalDays = totalDays,
        progress = progress,
        isActive = now.isBefore(end),
        rules = parsedRules,
        schedule = parsedSchedule,
        startsAt = starts_at,
        expiresAt = expires_at
    )
}
