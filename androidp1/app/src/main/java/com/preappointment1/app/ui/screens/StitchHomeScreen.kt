package com.preappointment1.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preappointment1.app.R
import com.preappointment1.app.data.model.TimelineEventResponse
import com.preappointment1.app.ui.components.StitchBottomNavBar
import com.preappointment1.app.ui.components.StitchTab
import com.preappointment1.app.ui.components.VoiceLogSheet
import com.preappointment1.app.ui.components.WellBeingTrendCard
import com.preappointment1.app.ui.support.FileStats
import com.preappointment1.app.ui.theme.*

enum class FeelingSentiment {
    BETTER,
    SAME,
    WORSE
}

@Composable
fun StitchHomeScreen(
    patientName: String = "Patient 1",
    followUps: List<FollowUpUi> = emptyList(),
    onOpenFollowUp: (FollowUpUi) -> Unit = {},
    timelineEvents: List<TimelineEventResponse> = emptyList(),
    activeTab: StitchTab = StitchTab.HOME,
    onTabSelected: (StitchTab) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenAddPhoto: () -> Unit = {},
    onOpenQuickLog: () -> Unit = {},
    onOpenTimeline: () -> Unit = {},
    onStartNewTracking: () -> Unit = {},
    onVoiceNoteCreated: () -> Unit = {},
    onPhotoAdded: () -> Unit = {},
    onSaveVoiceLog: (transcript: String, aiInsight: String?) -> Unit = { _, _ -> },
    onSentimentSelected: (FeelingSentiment) -> Unit = {}
) {
    var selectedSentiment by remember { mutableStateOf<FeelingSentiment?>(FeelingSentiment.SAME) }
    var showVoiceSheet by remember { mutableStateOf(false) }

    val activeFollowUp = remember(followUps) {
        followUps.firstOrNull { it.daysRemaining > 0 } ?: followUps.firstOrNull()
    }

    val fileStats = remember(activeFollowUp, timelineEvents) {
        if (activeFollowUp != null) {
            FileStats.compute(activeFollowUp, timelineEvents)
        } else null
    }

    Scaffold(
        containerColor = CanvasBackground,
        bottomBar = {
            StitchBottomNavBar(
                currentTab = activeTab,
                onTabSelected = onTabSelected
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 16.dp)
        ) {
            // ── Top Bar ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar (Tapping opens the full side navigation drawer)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MintBadge)
                        .border(1.5.dp, Color.White, CircleShape)
                        .clickable { onOpenDrawer() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "P1",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = SagePrimary
                    )
                }

                // Center App Logo & Brand Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardBackground)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MintBadge),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocalHospital,
                            contentDescription = null,
                            tint = SagePrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.brand_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = SagePrimary
                    )
                }

                // Settings Gear Button
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = TextPrimary.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // ── Greeting Section ──
            val hour = java.time.LocalTime.now().hour
            val greetingRes = when {
                hour < 12 -> R.string.home_greeting_morning
                hour < 18 -> R.string.home_greeting_afternoon
                else -> R.string.home_greeting_evening
            }
            Text(
                text = stringResource(greetingRes, patientName),
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                lineHeight = 38.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.home_feeling_question),
                fontSize = 15.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Sentiment Selector Row (Better / Same / Worse) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FeelingSentiment.values().forEach { sentiment ->
                    val isSelected = selectedSentiment == sentiment
                    val label = when (sentiment) {
                        FeelingSentiment.BETTER -> stringResource(R.string.sentiment_better)
                        FeelingSentiment.SAME -> stringResource(R.string.sentiment_same)
                        FeelingSentiment.WORSE -> stringResource(R.string.sentiment_worse)
                    }
                    val interactionSource = remember { MutableInteractionSource() }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .shadow(
                                elevation = if (isSelected) 3.dp else 1.dp,
                                shape = RoundedCornerShape(16.dp),
                                spotColor = SagePrimary.copy(alpha = 0.12f)
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) CardBackground else CardBackground.copy(alpha = 0.85f))
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) SagePrimary.copy(alpha = 0.7f) else CardBorderSoft,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                selectedSentiment = sentiment
                                onSentimentSelected(sentiment)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) TextPrimary else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Primary Hero Button (Voice Action) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(32.dp),
                        spotColor = SagePrimary.copy(alpha = 0.35f)
                    )
                    .clip(RoundedCornerShape(32.dp))
                    .background(SagePrimary)
                    .clickable { showVoiceSheet = true }
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Microphone",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.home_hero_voice_btn),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Quick Actions Grid (2 Cards) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Card 1: Add photo
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp)
                        .shadow(2.dp, RoundedCornerShape(22.dp), spotColor = Color.Black.copy(alpha = 0.04f))
                        .clip(RoundedCornerShape(22.dp))
                        .background(CardBackground)
                        .clickable { onOpenAddPhoto() }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CameraAlt,
                            contentDescription = "Add photo",
                            tint = SagePrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.home_action_add_photo),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }

                // Card 2: Quick log
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp)
                        .shadow(2.dp, RoundedCornerShape(22.dp), spotColor = Color.Black.copy(alpha = 0.04f))
                        .clip(RoundedCornerShape(22.dp))
                        .background(CardBackground)
                        .clickable { onOpenQuickLog() }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.EditNote,
                            contentDescription = "Quick log",
                            tint = SagePrimary,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.home_action_quick_log),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Active Appointment File / Tracking Card (Symbiosis with Timeline) ──
            if (activeFollowUp != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(3.dp, RoundedCornerShape(24.dp), spotColor = SagePrimary.copy(alpha = 0.08f))
                        .clip(RoundedCornerShape(24.dp))
                        .background(CardBackground)
                        .clickable {
                            onOpenFollowUp(activeFollowUp)
                            onOpenTimeline()
                        }
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeFollowUp.title,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (activeFollowUp.daysRemaining > 0)
                                        stringResource(R.string.next_appt_in_days, activeFollowUp.daysRemaining)
                                    else stringResource(R.string.summary_appt_date),
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MintBadge)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.home_active_file_ready, fileStats?.readinessPercent ?: 50),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MintBadgeText
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Progress Bar
                        LinearProgressIndicator(
                            progress = { ((fileStats?.readinessPercent ?: 50) / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = SagePrimary,
                            trackColor = MintBadge
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.home_active_file_entries,
                                    fileStats?.measurementCount ?: timelineEvents.size,
                                    fileStats?.photoCount ?: 0
                                ),
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.home_open_timeline),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SagePrimary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = SagePrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // Empty state card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.04f))
                        .clip(RoundedCornerShape(24.dp))
                        .background(CardBackground)
                        .clickable { onStartNewTracking() }
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MintBadge),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Assignment,
                                contentDescription = null,
                                tint = SagePrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.home_empty_prep_title),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.home_empty_prep_subtitle),
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = SagePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Insights Card: Your last 7 days (Computed from Real Patient Data) ──
            WellBeingTrendCard(
                timelineEvents = timelineEvents
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Voice Sheet Dialog ──
        if (showVoiceSheet) {
            VoiceLogSheet(
                onDismiss = { showVoiceSheet = false },
                onSaveVoiceLog = { transcript, insight ->
                    onSaveVoiceLog(transcript, insight)
                    onVoiceNoteCreated()
                }
            )
        }
    }
}
