package com.preappointment1.app.ui.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.preappointment1.app.ui.theme.*
import com.preappointment1.app.voice.VoiceManager
import com.preappointment1.app.voice.VoiceSessionState
import com.preappointment1.app.voice.VoiceState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceLogSheet(
    onDismiss: () -> Unit,
    onSaveVoiceLog: (transcript: String, aiInsight: String?) -> Unit
) {
    val context = LocalContext.current
    val voiceManager = remember { VoiceManager(context) }
    val sessionState by voiceManager.sessionState.collectAsState()
    var editableText by remember { mutableStateOf("") }

    LaunchedEffect(sessionState.text) {
        if (sessionState.text.isNotBlank()) {
            editableText = sessionState.text
        }
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Universal Fallback System Dialog (works on 100% of Android phones)
    val systemSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                voiceManager.setManualText(spoken)
                editableText = spoken
            }
        }
    }

    fun startRecordingFlow() {
        val started = voiceManager.startListening()
        if (!started) {
            try {
                systemSpeechLauncher.launch(voiceManager.createSystemSpeechIntent())
            } catch (e: Exception) {
                // Device has no voice IME, user can type
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            startRecordingFlow()
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            startRecordingFlow()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // If in-app recognition reports service unavailable, launch universal intent dialog
    LaunchedEffect(sessionState.requiresSystemDialog) {
        if (sessionState.requiresSystemDialog) {
            try {
                systemSpeechLauncher.launch(voiceManager.createSystemSpeechIntent())
            } catch (_: Exception) {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceManager.stopListening()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CardBackground,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = CardBorderSoft) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MintBadge),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = SagePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "Voice Check-in",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Subtitle status
            Text(
                text = when {
                    sessionState.state == VoiceState.LISTENING -> "Listening… speak naturally about how you feel"
                    sessionState.state == VoiceState.PROCESSING -> "Processing your speech…"
                    editableText.isNotBlank() -> "Review or edit what P1 heard below"
                    sessionState.state == VoiceState.ERROR -> sessionState.errorMessage ?: "Tap mic or speech button to start"
                    else -> "Tap the microphone to speak"
                },
                fontSize = 14.sp,
                color = if (sessionState.state == VoiceState.ERROR && editableText.isBlank()) PainMedium else TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Pulsing / Waveform Voice Orb
            VoiceOrbVisualization(
                isListening = sessionState.state == VoiceState.LISTENING,
                rmsLevel = sessionState.rmsLevel,
                onClick = {
                    if (sessionState.state == VoiceState.LISTENING) {
                        voiceManager.stopListening()
                    } else {
                        if (hasPermission) {
                            startRecordingFlow()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Quick button to open native system voice popup
            TextButton(
                onClick = {
                    try {
                        systemSpeechLauncher.launch(voiceManager.createSystemSpeechIntent())
                    } catch (e: Exception) {
                        // Fallback
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Outlined.RecordVoiceOver,
                    contentDescription = null,
                    tint = SagePrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Open system voice recognizer",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SagePrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Editable Transcript Card
            OutlinedTextField(
                value = editableText,
                onValueChange = {
                    editableText = it
                    voiceManager.setManualText(it)
                },
                placeholder = {
                    Text(
                        "Your words will appear here in real time as you speak (or type here directly)…",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 110.dp),
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SagePrimary,
                    unfocusedBorderColor = CardBorderSoft,
                    cursorColor = SagePrimary,
                    focusedContainerColor = CanvasBackground,
                    unfocusedContainerColor = CanvasBackground
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        editableText = ""
                        voiceManager.reset()
                        if (hasPermission) startRecordingFlow()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(CardBorderSoft)
                    )
                ) {
                    Text("Restart", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        val transcript = editableText.trim()
                        if (transcript.isNotBlank()) {
                            onSaveVoiceLog(transcript, null)
                            onDismiss()
                        }
                    },
                    enabled = editableText.isNotBlank(),
                    modifier = Modifier
                        .weight(1.5f)
                        .height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SagePrimary,
                        disabledContainerColor = Gray200,
                        contentColor = Color.White,
                        disabledContentColor = Gray400
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save to File", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun VoiceOrbVisualization(
    isListening: Boolean,
    rmsLevel: Float,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voicePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val dynamicRmsScale = 1f + (rmsLevel * 0.35f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(110.dp)
            .clickable(onClick = onClick)
    ) {
        // Outer glow ripple 2
        AnimatedVisibility(visible = isListening) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(pulseScale * dynamicRmsScale)
                    .clip(CircleShape)
                    .background(MintBadge.copy(alpha = 0.45f))
            )
        }

        // Outer glow ripple 1
        Box(
            modifier = Modifier
                .size(82.dp)
                .scale(if (isListening) pulseScale else 1f)
                .clip(CircleShape)
                .background(MintBadge)
        )

        // Core Solid Mic Orb
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (isListening) SagePrimary else SageDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isListening) "Stop" else "Record",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
