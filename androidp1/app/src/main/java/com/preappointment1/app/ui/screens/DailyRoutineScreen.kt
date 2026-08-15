package com.preappointment1.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preappointment1.app.data.model.FollowUpRules
import com.preappointment1.app.ui.components.*
import com.preappointment1.app.ui.theme.*

private enum class RoutineStepType {
    Photo, Pain, Vitals, Done
}

@Composable
fun DailyRoutineScreen(
    followUpTitle: String = "Daily Routine",
    rules: FollowUpRules?,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val steps = remember(rules) {
        val list = mutableListOf<RoutineStepType>()
        if (rules?.photos == true) list.add(RoutineStepType.Photo)
        if (rules?.pain != false) list.add(RoutineStepType.Pain)
        if (rules?.temperature == true || rules?.bloodPressure == true || rules?.smartwatch == true) {
            list.add(RoutineStepType.Vitals)
        }
        list.add(RoutineStepType.Done)
        list
    }

    var stepIndex by remember { mutableIntStateOf(0) }
    var photoFilename by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { LpmTopBar(title = followUpTitle, onBack = onBack) },
        containerColor = CanvasBackground,
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 22.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            LpmStepIndicator(currentStep = stepIndex + 1, totalSteps = steps.size)
            Spacer(modifier = Modifier.height(20.dp))

            when (steps[stepIndex]) {
                RoutineStepType.Photo -> PhotoStep(onPhotoTaken = { filename ->
                    photoFilename = filename
                    stepIndex++
                })
                RoutineStepType.Pain -> PainStep(onContinue = { stepIndex++ })
                RoutineStepType.Vitals -> VitalsStep(rules = rules, onContinue = { stepIndex++ })
                RoutineStepType.Done -> DoneStep(onFinish = onComplete)
            }
        }
    }
}

@Composable
private fun PhotoStep(onPhotoTaken: (String?) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        var photoSaved by remember { mutableStateOf<String?>(null) }

        if (photoSaved == null) {
            MeasurementPhotoCapture(
                onPhotoCaptured = { fileName ->
                    photoSaved = fileName
                },
                previewHeight = 280
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MintBadge),
                contentAlignment = Alignment.Center
            ) {
                Text("✓ Photo saved", color = MintBadgeText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(20.dp))
            LpmPrimaryButton(text = "Continue", onClick = { onPhotoTaken(photoSaved) })
        }
    }
}

@Composable
private fun PainStep(onContinue: () -> Unit) {
    var painLevel by remember { mutableFloatStateOf(0f) }

    Column(modifier = Modifier.fillMaxSize()) {
        LpmSectionTitle("Pain Assessment")
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Pain level today",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("0 (None)", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Text("10 (Severe)", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = painLevel,
            onValueChange = { painLevel = it },
            valueRange = 0f..10f,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = SagePrimary,
                activeTrackColor = SagePrimary,
                inactiveTrackColor = MintBadge
            )
        )
        Text(
            text = "${painLevel.toInt()} / 10",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = SagePrimary
        )

        Spacer(modifier = Modifier.weight(1f))
        LpmPrimaryButton(text = "Save & Continue", onClick = onContinue)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun VitalsStep(rules: FollowUpRules?, onContinue: () -> Unit) {
    var tempValue by remember { mutableStateOf("") }
    var bpValue by remember { mutableStateOf("") }
    var hrValue by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            LpmSectionTitle("Vitals Check-in")
            Spacer(modifier = Modifier.height(24.dp))

            if (rules?.temperature == true) {
                Text("Quick Select Temperature", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("36.5", "37.0", "37.5", "38.0").forEach { temp ->
                        FilterChip(
                            selected = tempValue == temp,
                            onClick = { tempValue = temp },
                            label = { Text("$temp°") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SagePrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tempValue,
                    onValueChange = { tempValue = it },
                    label = { Text("Or enter specific (°C)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            if (rules?.bloodPressure == true) {
                OutlinedTextField(
                    value = bpValue,
                    onValueChange = { bpValue = it },
                    label = { Text("Blood Pressure (mmHg)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            if (rules?.smartwatch == true) {
                OutlinedTextField(
                    value = hrValue,
                    onValueChange = { hrValue = it },
                    label = { Text("Heart Rate (bpm)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }
        
        LpmPrimaryButton(text = "Save & Continue", onClick = onContinue)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DoneStep(onFinish: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MintBadge),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = SagePrimary,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        LpmSectionTitle("Routine Completed")
        Spacer(modifier = Modifier.height(12.dp))
        LpmBodyText(
            "Today's data has been securely saved to your timeline file.",
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(36.dp))
        LpmPrimaryButton(
            text = "Back to Home",
            onClick = onFinish,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}
