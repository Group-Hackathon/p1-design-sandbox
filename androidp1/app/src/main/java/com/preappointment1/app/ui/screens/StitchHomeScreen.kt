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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preappointment1.app.ui.components.StitchBottomNavBar
import com.preappointment1.app.ui.components.StitchTab
import com.preappointment1.app.ui.components.VoiceLogSheet
import com.preappointment1.app.ui.components.WellBeingTrendCard
import com.preappointment1.app.ui.theme.*

enum class FeelingSentiment(val label: String) {
    BETTER("Better"),
    SAME("Same"),
    WORSE("Worse")
}

@Composable
fun StitchHomeScreen(
    patientName: String = "Sarah",
    activeTab: StitchTab = StitchTab.HOME,
    onTabSelected: (StitchTab) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenAddPhoto: () -> Unit = {},
    onOpenQuickLog: () -> Unit = {},
    onSaveVoiceLog: (transcript: String, aiInsight: String?) -> Unit = { _, _ -> },
    onSentimentSelected: (FeelingSentiment) -> Unit = {}
) {
    var selectedSentiment by remember { mutableStateOf<FeelingSentiment?>(FeelingSentiment.SAME) }
    var showVoiceSheet by remember { mutableStateOf(false) }

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
                // Avatar
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MintBadge)
                        .border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = patientName.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
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
                        text = "P1 Health",
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

            Spacer(modifier = Modifier.height(28.dp))

            // ── Greeting Section ──
            Text(
                text = "Good morning,\n$patientName.",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                lineHeight = 38.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "How are you feeling today?",
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
                            text = sentiment.label,
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
                        text = "Tell P1 how you’re feeling",
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
                        .height(115.dp)
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
                            text = "Add photo",
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
                        .height(115.dp)
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
                            text = "Quick log",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Insights Card: Your last 7 days ──
            WellBeingTrendCard(
                statusText = "Stable",
                dataPoints = listOf(0.35f, 0.45f, 0.40f, 0.65f, 0.50f, 0.70f, 0.68f)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Voice Sheet Dialog ──
        if (showVoiceSheet) {
            VoiceLogSheet(
                onDismiss = { showVoiceSheet = false },
                onSaveVoiceLog = { transcript, insight ->
                    onSaveVoiceLog(transcript, insight)
                }
            )
        }
    }
}
