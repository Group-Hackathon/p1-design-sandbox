package com.preappointment1.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import com.preappointment1.app.billing.BillingManager
import com.preappointment1.app.notifications.ScheduleReminderManager
import com.preappointment1.app.schedule.ScheduleDefaults
import com.preappointment1.app.schedule.ScheduleLogic
import com.preappointment1.app.data.AuthHelper
import com.preappointment1.app.data.repository.FollowUpRepository
import com.preappointment1.app.data.api.ApiClient
import com.preappointment1.app.data.model.RecommendRequest
import com.preappointment1.app.data.model.SubscriptionRequest
import com.preappointment1.app.data.model.TrackingRulesDto
import com.preappointment1.app.ui.components.*
import com.preappointment1.app.ui.theme.Black
import com.preappointment1.app.ui.theme.Gray200
import com.preappointment1.app.ui.theme.Gray400
import com.preappointment1.app.ui.theme.Gray50
import com.preappointment1.app.ui.theme.Gray600
import com.preappointment1.app.ui.theme.White
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun OnboardingScreen(
    onBack: () -> Unit,
    onFollowUpCreated: (subscriptionId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableIntStateOf(1) }
    var symptomText by remember { mutableStateOf("") }
    var appointmentDate by remember { mutableStateOf(LocalDate.now().plusDays(14)) }
    
    // Tracking rules state
    var ruleTemperature by remember { mutableStateOf(false) }
    var rulePain by remember { mutableStateOf(true) }
    var rulePhotos by remember { mutableStateOf(true) }
    var ruleSmartwatch by remember { mutableStateOf(false) }
    var ruleBp by remember { mutableStateOf(false) }

    var isAnalyzing by remember { mutableStateOf(false) }
    var isStarting by remember { mutableStateOf(false) }
    
    var generatedTitle by remember { mutableStateOf("") }
    var generatedPlan by remember { mutableStateOf("") }
    var generatedSchedule by remember { mutableStateOf<Map<String, List<String>>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        val message = errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        errorMessage = null
    }
    
    val purchaseSuccess by BillingManager.purchaseSuccessFlow.collectAsState()

    LaunchedEffect(purchaseSuccess) {
        if (purchaseSuccess != null) {
            BillingManager.clearPurchaseSuccess()
            isStarting = true
            scope.launch {
                try {
                    if (!AuthHelper.ensureAuthenticated()) throw Exception("Authentication required")
                    val profileId = AuthHelper.ensureProfile() ?: throw IllegalStateException("Profile not found")
                    val duration = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), appointmentDate).toInt().coerceAtLeast(1)
                    val rulesMap = mapOf(
                        "temperature" to ruleTemperature,
                        "pain" to rulePain,
                        "photos" to rulePhotos,
                        "smartwatch" to ruleSmartwatch,
                        "blood_pressure" to ruleBp
                    )
                    val params = mutableMapOf<String, Any>(
                        "title" to generatedTitle,
                        "symptoms" to symptomText,
                        "next_appointment" to appointmentDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        "rules" to rulesMap,
                        "plan" to generatedPlan
                    )
                    generatedSchedule?.let { params["schedule"] = it }

                    val response = ApiClient.apiService.createSubscription(
                        SubscriptionRequest(
                            profile_id = profileId,
                            agent_id = "dynamic-plan",
                            duration_days = duration,
                            parameters = params
                        )
                    )
                    FollowUpRepository.saveFromRemote(response)
                    onFollowUpCreated(response.id)
                    generatedSchedule?.let { schedule ->
                        ScheduleReminderManager.scheduleForFollowUp(
                            context, response.id, generatedTitle, schedule
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorMessage = "Unable to start tracking. Check your connection and try again."
                } finally {
                    isStarting = false
                }
            }
        }
    }

    Scaffold(
        topBar = { 
            LpmTopBar(
                title = if (step == 6) "Manual Setup" else "New Tracking", 
                onBack = { 
                    if (step > 1 && step != 4 && step != 5 && step != 6) step-- 
                    else if (step == 5 || step == 6) step = 3
                    else onBack() 
                }
            ) 
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = if (step == 2) 12.dp else 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            if (step <= 3) {
                LpmStepIndicator(currentStep = step, totalSteps = 3)
                Spacer(modifier = Modifier.height(28.dp))
            }

            AnimatedContent(
                targetState = step,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                transitionSpec = {
                    (fadeIn() + slideInHorizontally { width -> width }).togetherWith(
                        fadeOut() + slideOutHorizontally { width -> -width }
                    )
                },
                label = "onboarding_steps"
            ) { targetStep ->
                when (targetStep) {
                    1 -> DescribeStep(
                        symptomText = symptomText,
                        onSymptomChange = { symptomText = it },
                        onNext = { step = 2 }
                    )
                    2 -> DateStep(
                        date = appointmentDate,
                        onDateChange = { appointmentDate = it },
                        onNext = { step = 3 }
                    )
                    3 -> RulesStep(
                        ruleTemperature = ruleTemperature, onRuleTemperatureChange = { ruleTemperature = it },
                        rulePain = rulePain, onRulePainChange = { rulePain = it },
                        rulePhotos = rulePhotos, onRulePhotosChange = { rulePhotos = it },
                        ruleSmartwatch = ruleSmartwatch, onRuleSmartwatchChange = { ruleSmartwatch = it },
                        ruleBp = ruleBp, onRuleBpChange = { ruleBp = it },
                        isLoading = isAnalyzing,
                        onGeneratePlan = {
                            isAnalyzing = true
                            scope.launch {
                                try {
                                    if (!AuthHelper.ensureAuthenticated()) throw Exception("Authentication required")
                                    val rulesDto = TrackingRulesDto(
                                        temperature = ruleTemperature,
                                        pain = rulePain,
                                        photos = rulePhotos,
                                        smartwatch = ruleSmartwatch,
                                        blood_pressure = ruleBp
                                    )
                                    val response = ApiClient.apiService.recommendAgent(
                                        RecommendRequest(
                                            symptoms = symptomText,
                                            appointment_date = appointmentDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                                            rules = rulesDto,
                                            local_time = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                                            timezone = java.time.ZoneId.systemDefault().id
                                        )
                                    )
                                    generatedTitle = response.name
                                    generatedPlan = response.description
                                    generatedSchedule = response.schedule?.let { schedule ->
                                        ScheduleLogic.adaptScheduleToNow(schedule, java.time.LocalTime.now())
                                    }
                                    step = 4 // Premium Preview
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    errorMessage = "Plan generation failed. Try again or use manual setup."
                                    step = 5 // Offline / Error Fallback
                                } finally {
                                    isAnalyzing = false
                                }
                            }
                        }
                    )
                    4 -> PremiumPreviewStep(
                        title = generatedTitle,
                        planText = generatedPlan,
                        appointmentDate = appointmentDate,
                        isLoading = isStarting,
                        onConfirm = {
                            // Handled via Purchase Flow and LaunchedEffect above
                        },
                        onManualSetup = { step = 6 }
                    )
                    5 -> OfflineFallbackStep(
                        onRetry = { step = 3 },
                        onManualSetup = { step = 6 }
                    )
                    6 -> ManualEntryStep(
                        appointmentDate = appointmentDate,
                        isLoading = isStarting,
                        onConfirm = { customTitle, customSchedule ->
                            isStarting = true
                            scope.launch {
                                try {
                                    if (!AuthHelper.ensureAuthenticated()) throw Exception("Authentication required")
                                    val profileId = AuthHelper.ensureProfile() ?: throw IllegalStateException("Profile not found")
                                    val duration = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), appointmentDate).toInt().coerceAtLeast(1)
                                    
                                    val params = mutableMapOf<String, Any>(
                                        "title" to customTitle,
                                        "symptoms" to symptomText,
                                        "next_appointment" to appointmentDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                                        "plan" to "Manual custom tracking",
                                        "schedule" to customSchedule,
                                        "rules" to mapOf(
                                            "temperature" to ruleTemperature,
                                            "pain" to rulePain,
                                            "photos" to rulePhotos,
                                            "smartwatch" to ruleSmartwatch,
                                            "blood_pressure" to ruleBp
                                        )
                                    )

                                    val response = ApiClient.apiService.createSubscription(
                                        SubscriptionRequest(
                                            profile_id = profileId,
                                            agent_id = "dynamic-plan",
                                            duration_days = duration,
                                            parameters = params
                                        )
                                    )
                                    FollowUpRepository.saveFromRemote(response)
                                    onFollowUpCreated(response.id)
                                    ScheduleReminderManager.scheduleForFollowUp(
                                        context, response.id, customTitle, customSchedule
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    errorMessage = "Unable to start tracking. Check your connection and try again."
                                } finally {
                                    isStarting = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DescribeStep(
    symptomText: String,
    onSymptomChange: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        LpmSectionTitle("What would you like to track?")
        Spacer(modifier = Modifier.height(8.dp))
        LpmBodyText("Describe your situation in a few sentences.")
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = symptomText,
            onValueChange = onSymptomChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
            placeholder = { Text("e.g. knee wound for 3 days, fever at 39°C...", color = Gray600) },
            shape = RoundedCornerShape(4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Black,
                unfocusedBorderColor = Gray200,
                cursorColor = Black
            )
        )

        Spacer(modifier = Modifier.weight(1f))
        LpmPrimaryButton(
            text = "Continue",
            onClick = onNext,
            enabled = symptomText.isNotBlank()
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateStep(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onNext: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = date
            .atStartOfDay(java.time.ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()
    )
    val scrollState = rememberScrollState()
    val selectedDateLabel = remember(datePickerState.selectedDateMillis) {
        datePickerState.selectedDateMillis?.let { millis ->
            java.time.Instant.ofEpochMilli(millis)
                .atZone(java.time.ZoneId.of("UTC"))
                .toLocalDate()
                .format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH))
        } ?: date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH))
    }

    LaunchedEffect(datePickerState.selectedDateMillis) {
        datePickerState.selectedDateMillis?.let { millis ->
            val selected = java.time.Instant.ofEpochMilli(millis)
                .atZone(java.time.ZoneId.of("UTC"))
                .toLocalDate()
            if (selected != date) onDateChange(selected)
        }
    }

    // Single scroll: DatePicker needs unbounded height (6-row months clip inside weight boxes).
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        LpmSectionTitle("When is your appointment?")
        Spacer(modifier = Modifier.height(8.dp))
        LpmBodyText("Select the exact date of your next medical checkup.")
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = selectedDateLabel,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Black
        )
        Spacer(modifier = Modifier.height(12.dp))

        // No Card — rounded Card clips the grid on the right/bottom edges.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Gray50, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .border(1.dp, Gray200, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .padding(vertical = 4.dp)
        ) {
            DatePicker(
                state = datePickerState,
                showModeToggle = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 420.dp),
                title = null,
                headline = null,
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = Black,
                    selectedDayContentColor = White,
                    todayDateBorderColor = Black,
                    todayContentColor = Black,
                    containerColor = Gray50,
                    headlineContentColor = Black,
                    weekdayContentColor = Gray600,
                    dayContentColor = Black,
                    disabledDayContentColor = Gray200
                )
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        LpmPrimaryButton(text = "Continue", onClick = onNext)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun RulesStep(
    ruleTemperature: Boolean, onRuleTemperatureChange: (Boolean) -> Unit,
    rulePain: Boolean, onRulePainChange: (Boolean) -> Unit,
    rulePhotos: Boolean, onRulePhotosChange: (Boolean) -> Unit,
    ruleSmartwatch: Boolean, onRuleSmartwatchChange: (Boolean) -> Unit,
    ruleBp: Boolean, onRuleBpChange: (Boolean) -> Unit,
    isLoading: Boolean,
    onGeneratePlan: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        LpmSectionTitle("Your tracking rules")
        Spacer(modifier = Modifier.height(8.dp))
        LpmBodyText("Choose the health metrics you want to monitor.")
        Spacer(modifier = Modifier.height(24.dp))

        RuleToggle("Temperature", "Manual entries", ruleTemperature, onRuleTemperatureChange)
        RuleToggle("Pain tracking", "Daily pain level", rulePain, onRulePainChange)
        RuleToggle("Daily photos", "Guided photo capture", rulePhotos, onRulePhotosChange)
        RuleToggleComingSoon("Smartwatch data", "Heart rate, steps — coming soon")
        RuleToggleComingSoon("Blood pressure", "Manual or connected — coming soon")

        Spacer(modifier = Modifier.height(32.dp))
        LpmPrimaryButton(
            text = "Prepare my protocol",
            onClick = onGeneratePlan,
            loading = isLoading
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun RuleToggleComingSoon(title: String, subtitle: String) {
    LpmCard(modifier = Modifier.padding(bottom = 12.dp).alpha(0.55f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, color = Gray400)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Gray400)
            }
            Switch(
                checked = false,
                onCheckedChange = {},
                enabled = false,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = White,
                    checkedTrackColor = Black,
                    uncheckedThumbColor = White,
                    uncheckedTrackColor = Gray200,
                    uncheckedBorderColor = Gray200
                )
            )
        }
    }
}

@Composable
private fun RuleToggle(
    title: String, subtitle: String,
    checked: Boolean, onCheckedChange: (Boolean) -> Unit
) {
    LpmCard(modifier = Modifier.padding(bottom = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, color = Black)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Gray600)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = White,
                    checkedTrackColor = Black,
                    uncheckedThumbColor = White,
                    uncheckedTrackColor = Gray200,
                    uncheckedBorderColor = Gray200
                )
            )
        }
    }
}

@Composable
private fun PremiumPreviewStep(
    title: String,
    planText: String,
    appointmentDate: LocalDate,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onManualSetup: () -> Unit
) {
    val durationDays = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), appointmentDate).toInt().coerceAtLeast(1)
    
    val productId = when {
        durationDays <= 5 -> "p1_pack_short"
        durationDays <= 14 -> "p1_pack_medium"
        else -> "p1_pack_long"
    }

    val prices by BillingManager.pricesFlow.collectAsState()
    val displayPrice = prices[productId] ?: "Loading..."
    
    val context = LocalContext.current
    val activity = context as? Activity

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Highlighting the premium/proprietary aspect
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Check, contentDescription = "Analyzed", tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Analyzed from thousands of medical records", style = MaterialTheme.typography.labelMedium, color = Gray600, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Black)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Here is the optimal $durationDays-day tracking program until your appointment.", style = MaterialTheme.typography.bodyMedium, color = Gray600)
        
        Spacer(modifier = Modifier.height(24.dp))

        // Schedule Preview Box (No blur)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Gray50, RoundedCornerShape(12.dp))
                .padding(2.dp) // border space
                .background(White, RoundedCornerShape(10.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                val lines = planText.split("\n").filter { it.isNotBlank() }
                lines.forEach { line ->
                    val cleanLine = line.removePrefix("- ").removePrefix("* ")
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text("•", color = Black, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(cleanLine, color = Gray600, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Google Play Payment Button
        Button(
            onClick = {
                if (activity != null) BillingManager.launchPurchaseFlow(activity, productId)
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Black)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = White, strokeWidth = 2.dp)
            } else {
                Text(
                    "Pay $displayPrice via Google Play",
                    color = White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Manual Opt-out
        Text(
            text = "Or create my planning manually for free",
            style = MaterialTheme.typography.labelLarge,
            color = Gray400,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable { onManualSetup() }.padding(8.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun OfflineFallbackStep(
    onRetry: () -> Unit,
    onManualSetup: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Warning, contentDescription = "Offline", tint = Color(0xFFFFA726), modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Connection Unavailable",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Black,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "We cannot analyze your symptoms at the moment to propose the optimal tracking protocol.",
            style = MaterialTheme.typography.bodyMedium,
            color = Gray600,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        
        Spacer(modifier = Modifier.height(40.dp))
        
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Try again", color = Black, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onManualSetup,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Black)
        ) {
            Text("Proceed with manual setup", color = White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ManualEntryStep(
    appointmentDate: LocalDate,
    isLoading: Boolean,
    onConfirm: (title: String, schedule: Map<String, List<String>>) -> Unit
) {
    val context = LocalContext.current
    val defaults = remember { ScheduleDefaults.load(context) }
    val durationDays = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), appointmentDate).toInt().coerceAtLeast(1)
    var trackingTitle by remember { mutableStateOf("") }
    var mornPain by remember { mutableStateOf(defaults["08:00"]?.contains("pain") == true) }
    var mornTemp by remember { mutableStateOf(defaults["08:00"]?.contains("temperature") == true) }
    var mornPhoto by remember { mutableStateOf(defaults["08:00"]?.contains("photo") == true) }
    var noonPain by remember { mutableStateOf(defaults["12:00"]?.contains("pain") == true) }
    var noonTemp by remember { mutableStateOf(defaults["12:00"]?.contains("temperature") == true) }
    var noonPhoto by remember { mutableStateOf(defaults["12:00"]?.contains("photo") == true) }
    var evePain by remember { mutableStateOf(defaults["20:00"]?.contains("pain") == true) }
    var eveTemp by remember { mutableStateOf(defaults["20:00"]?.contains("temperature") == true) }
    var evePhoto by remember { mutableStateOf(defaults["20:00"]?.contains("photo") == true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text("Manual Tracking Setup", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Black)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Set your daily check-in times for the next $durationDays days until your appointment.",
            style = MaterialTheme.typography.bodyMedium,
            color = Gray600
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = trackingTitle,
            onValueChange = { trackingTitle = it },
            label = { Text("Tracking name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        LpmCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Morning — 08:00", fontWeight = FontWeight.SemiBold, color = Black)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ManualCheckbox("Pain", mornPain) { mornPain = it }
                    ManualCheckbox("Temp", mornTemp) { mornTemp = it }
                    ManualCheckbox("Photo", mornPhoto) { mornPhoto = it }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LpmCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Noon — 12:00", fontWeight = FontWeight.SemiBold, color = Black)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ManualCheckbox("Pain", noonPain) { noonPain = it }
                    ManualCheckbox("Temp", noonTemp) { noonTemp = it }
                    ManualCheckbox("Photo", noonPhoto) { noonPhoto = it }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LpmCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Evening — 20:00", fontWeight = FontWeight.SemiBold, color = Black)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ManualCheckbox("Pain", evePain) { evePain = it }
                    ManualCheckbox("Temp", eveTemp) { eveTemp = it }
                    ManualCheckbox("Photo", evePhoto) { evePhoto = it }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        LpmPrimaryButton(
            text = "Create free tracking",
            loading = isLoading,
            enabled = trackingTitle.isNotBlank(),
            onClick = {
                val schedule = mutableMapOf<String, List<String>>()

                fun slotSet(pain: Boolean, temp: Boolean, photo: Boolean): List<String> {
                    val set = mutableListOf<String>()
                    if (pain) set.add("pain")
                    if (temp) set.add("temperature")
                    if (photo) set.add("photo")
                    return set
                }

                val morn = slotSet(mornPain, mornTemp, mornPhoto)
                val noon = slotSet(noonPain, noonTemp, noonPhoto)
                val eve = slotSet(evePain, eveTemp, evePhoto)

                if (morn.isNotEmpty()) schedule["08:00"] = morn
                if (noon.isNotEmpty()) schedule["12:00"] = noon
                if (eve.isNotEmpty()) schedule["20:00"] = eve

                if (schedule.isEmpty()) return@LpmPrimaryButton
                onConfirm(trackingTitle, schedule)
            }
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ManualCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = Black)
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = Gray600)
    }
}
