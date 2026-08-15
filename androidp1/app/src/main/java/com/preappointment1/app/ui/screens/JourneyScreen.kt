package com.preappointment1.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.ui.res.painterResource
import com.preappointment1.app.ui.components.StitchBottomNavBar
import com.preappointment1.app.ui.components.StitchTab
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.preappointment1.app.R
import com.preappointment1.app.data.repository.FollowUpRepository
import com.preappointment1.app.data.repository.TimelineRepository
import com.preappointment1.app.data.sync.SyncManager
import com.preappointment1.app.data.model.TimelineEventRequest
import com.preappointment1.app.data.model.TimelineEventResponse
import com.preappointment1.app.data.model.UpdateSubscriptionRequest
import com.preappointment1.app.data.api.ApiClient
import com.preappointment1.app.data.updateFollowUpSchedule
import com.preappointment1.app.notifications.ScheduleReminderManager
import com.preappointment1.app.schedule.MeasurementStep
import com.preappointment1.app.schedule.ScheduleLogic
import com.preappointment1.app.schedule.ScheduleSlot
import com.preappointment1.app.ui.components.*
import com.preappointment1.app.ui.support.FileStats
import com.preappointment1.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private fun isSlotCompletedToday(
    events: List<TimelineEventResponse>,
    slotKey: String,
    isFirstSlot: Boolean
): Boolean = ScheduleLogic.isSlotCompletedToday(events, slotKey, isFirstSlot)

private fun measurementStepsFromSchedule(schedule: Map<String, List<String>>): List<MeasurementStep> {
    val types = schedule.values.flatten().map { it.lowercase() }.toSet()
    val ordered = listOf(
        MeasurementStep.Pain to "pain",
        MeasurementStep.Temperature to "temperature",
        MeasurementStep.Photo to "photo"
    )
    return ordered.filter { (_, key) -> key in types }.map { it.first }
        .ifEmpty { listOf(MeasurementStep.Pain) }
}

private fun measurementStepLabel(step: MeasurementStep): Int = when (step) {
    MeasurementStep.Pain -> R.string.measurement_type_pain
    MeasurementStep.Temperature -> R.string.measurement_type_temperature
    MeasurementStep.Photo -> R.string.measurement_type_photo
}

private sealed class TimelineItem {
    data class PastEvent(val userEvent: TimelineEventResponse, val aiEvent: TimelineEventResponse?) : TimelineItem()
    data class FutureDay(val dayNumber: Int, val label: String, val date: LocalDate) : TimelineItem()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyScreen(
    followUp: FollowUpUi,
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenReport: (() -> Unit)? = null,
    onOpenDocuments: (() -> Unit)? = null,
    onFollowUpUpdated: ((FollowUpUi) -> Unit)? = null,
    openMeasurementFormOnLaunch: Boolean = false,
    onMeasurementFormLaunchHandled: () -> Unit = {},
    highlightPendingCheckIn: Boolean = false,
    onHighlightCheckInHandled: () -> Unit = {},
    notificationScheduleKey: String? = null,
    activeTab: StitchTab = StitchTab.TIMELINE,
    onTabSelected: ((StitchTab) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val appContext = LocalContext.current

    var currentFollowUp by remember { mutableStateOf(followUp) }
    LaunchedEffect(followUp) {
        currentFollowUp = followUp
    }

    val events by TimelineRepository.observeEvents(currentFollowUp.id).collectAsState(initial = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }

    var isFormMode by remember { mutableStateOf(false) }
    var formEffectiveDate by remember { mutableStateOf<LocalDate?>(null) }
    var formLabelOverride by remember { mutableStateOf<String?>(null) }
    var formStepsOverride by remember { mutableStateOf<List<MeasurementStep>?>(null) }
    var formScheduleKeyOverride by remember { mutableStateOf<String?>(null) }
    var showNoteSheet by remember { mutableStateOf(false) }
    var showExtraPicker by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(currentFollowUp.id) {
        TimelineRepository.refreshFromRemote(currentFollowUp.id)
        SyncManager.scheduleSync(appContext)
    }

    var showMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showScheduleEditor by remember { mutableStateOf(false) }
    var isSavingSchedule by remember { mutableStateOf(false) }

    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while(true) {
            delay(1000)
            now = LocalTime.now()
        }
    }
    val schedule = currentFollowUp.schedule ?: mapOf(
        "08:00" to listOf("pain", "temperature"),
        "20:00" to listOf("pain", "temperature", "photo")
    )

    val scheduleSlots = remember(schedule) { ScheduleLogic.parseScheduleSlots(schedule) }

    val isInitial = !isLoading && events.none {
        it.type == "user" && !it.date_label.contains("Question")
    }

    val isSlotPending: (ScheduleSlot) -> Boolean = { slot ->
        !isSlotCompletedToday(
            events,
            slot.timeKey,
            slot == scheduleSlots.firstOrNull()
        )
    }

    val measurementContext = remember(schedule, scheduleSlots, events, now, isInitial) {
        ScheduleLogic.resolveMeasurementContext(
            schedule = schedule,
            slots = scheduleSlots,
            now = now,
            isInitial = isInitial,
            isSlotPending = isSlotPending
        )
    }

    val dueSlot = measurementContext.dueSlot
    val nextSlot = measurementContext.nextSlot
    val showStarterCheckIn = measurementContext.showStarterCheckIn
    val showMeasurementButton = dueSlot != null || showStarterCheckIn

    val nextWindowTime = nextSlot?.time
    val nextWindowName = nextSlot?.timeKey?.let { stringResource(R.string.check_in_at, it) }
        ?: stringResource(R.string.next_check_in)

    LaunchedEffect(openMeasurementFormOnLaunch, isLoading, showMeasurementButton, showStarterCheckIn, notificationScheduleKey) {
        if (!openMeasurementFormOnLaunch || isLoading) return@LaunchedEffect

        val notifiedKey = notificationScheduleKey?.takeIf { schedule.containsKey(it) }
        val openFromNotification = notifiedKey != null &&
            ScheduleLogic.isNotificationSlotActionable(schedule, events, notifiedKey, now)

        when {
            openFromNotification -> {
                formEffectiveDate = null
                formLabelOverride = null
                formStepsOverride = null
                formScheduleKeyOverride = notifiedKey
                isFormMode = true
                onHighlightCheckInHandled()
            }
            showMeasurementButton -> {
                formEffectiveDate = null
                formLabelOverride = if (showStarterCheckIn) measurementContext.formLabelOverride else null
                formStepsOverride = if (showStarterCheckIn) measurementContext.formStepsOverride else null
                formScheduleKeyOverride = null
                isFormMode = true
                onHighlightCheckInHandled()
            }
            else -> {
                snackbarHostState.showSnackbar(
                    message = appContext.getString(R.string.notification_checkin_already_done)
                )
                onHighlightCheckInHandled()
            }
        }
        onMeasurementFormLaunchHandled()
    }

    // Compute appointment date from expiresAt
    val appointmentDate = remember(currentFollowUp.expiresAt) {
        runCatching {
            Instant.parse(currentFollowUp.expiresAt).atZone(ZoneId.systemDefault()).toLocalDate()
        }.getOrDefault(LocalDate.now().plusDays(currentFollowUp.daysRemaining.toLong()))
    }

    val startDate = remember(currentFollowUp.startsAt) {
        runCatching {
            Instant.parse(currentFollowUp.startsAt).atZone(ZoneId.systemDefault()).toLocalDate()
        }.getOrDefault(LocalDate.now())
    }

    val timelineItems = remember(events, currentFollowUp) {
        val items = mutableListOf<TimelineItem>()
        var i = 0
        while (i < events.size) {
            val event = events[i]
            if (event.type == "user") {
                var aiEvent: TimelineEventResponse? = null
                if (i + 1 < events.size && events[i+1].type == "ai") {
                    aiEvent = events[i+1]
                    i++
                }
                items.add(TimelineItem.PastEvent(event, aiEvent))
            } else if (event.type == "ai") {
                items.add(TimelineItem.PastEvent(event, null))
            }
            i++
        }

        val daysDone = currentFollowUp.totalDays - currentFollowUp.daysRemaining
        val startFutureDay = if (daysDone < 1) 1 else daysDone + 1

        for (d in startFutureDay..currentFollowUp.totalDays) {
            val futureDate = startDate.plusDays(d.toLong() - 1)
            items.add(TimelineItem.FutureDay(dayNumber = d, label = "Day $d - Scheduled tracking", date = futureDate))
        }
        items
    }

    // Date picker dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = appointmentDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val newDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        val newExpiresAt = newDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        coroutineScope.launch {
                            try {
                                val updated = ApiClient.apiService.patchSubscription(
                                    currentFollowUp.id,
                                    UpdateSubscriptionRequest(expires_at = newExpiresAt)
                                )
                                FollowUpRepository.saveFromRemote(updated)
                                val refreshed = updated.toFollowUpUi(FollowUpRepository.getAgentsOrEmpty())
                                currentFollowUp = refreshed
                                onFollowUpUpdated?.invoke(refreshed)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_confirm), color = Black) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Delete tracking confirmation
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.dialog_delete_tracking_title)) },
            text = { Text(stringResource(R.string.dialog_delete_tracking_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmDialog = false
                    coroutineScope.launch {
                        try {
                            ApiClient.apiService.deleteSubscription(currentFollowUp.id)
                        } catch (_: Exception) {
                            // Still remove local copy when offline
                        }
                        FollowUpRepository.deleteLocal(currentFollowUp.id)
                        onBack()
                    }
                }) { Text(stringResource(R.string.action_delete), color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize().background(CanvasBackground)) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(currentFollowUp.title, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SagePrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { onOpenDocuments?.invoke() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_folder),
                                contentDescription = stringResource(R.string.action_open_folder),
                                tint = SagePrimary
                            )
                        }
                        // 3-dot overflow menu
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Options", tint = SagePrimary)
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_edit_schedule)) },
                                    onClick = {
                                        showMenu = false
                                        showScheduleEditor = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_change_appt_date)) },
                                    onClick = {
                                        showMenu = false
                                        showDatePicker = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_delete_tracking), color = Color.Red) },
                                    onClick = {
                                        showMenu = false
                                        showDeleteConfirmDialog = true
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = CanvasBackground,
                        titleContentColor = TextPrimary
                    )
                )
            },
            bottomBar = {
                if (onTabSelected != null) {
                    StitchBottomNavBar(
                        currentTab = activeTab,
                        onTabSelected = onTabSelected
                    )
                }
            },
            containerColor = CanvasBackground,
            modifier = Modifier.fillMaxSize()
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // Vertical central line
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(MintBadge)
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        item {
                            JourneySummary(followUp = currentFollowUp, events = events, appointmentDate = appointmentDate)
                            TopInfoCard(
                                followUp = currentFollowUp,
                                dueSlotKey = dueSlot?.timeKey,
                                nextWindowName = nextWindowName,
                                nextWindowTime = nextWindowTime
                            )
                        }

                        if (events.isEmpty()) {
                            item {
                                EmptyStateWelcome()
                            }
                        }

                        items(timelineItems) { item ->
                            when (item) {
                                is TimelineItem.PastEvent -> {
                                    CentralTimelineEvent(
                                        userEvent = item.userEvent,
                                        aiEvent = item.aiEvent,
                                        onDelete = { eventId ->
                                            coroutineScope.launch {
                                                try {
                                                    TimelineRepository.deleteEvent(currentFollowUp.id, eventId)
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        }
                                    )
                                }
                                is TimelineItem.FutureDay -> {
                                    val isPastDay = item.date.isBefore(LocalDate.now())
                                    FutureTimelineEvent(
                                        day = item.dayNumber,
                                        label = item.label,
                                        isPast = isPastDay,
                                        onAddMissed = if (isPastDay) {
                                            {
                                                formEffectiveDate = item.date
                                                formLabelOverride = null
                                                formStepsOverride = null
                                                isFormMode = true
                                            }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                }

                BottomMeasurementBar(
                    dueSlot = dueSlot,
                    nextSlot = nextSlot,
                    nextWindowTime = nextWindowTime,
                    showMeasurementButton = showMeasurementButton,
                    showStarterCheckIn = showStarterCheckIn,
                    highlightPendingCheckIn = highlightPendingCheckIn && showMeasurementButton,
                    previewActionsOverride = if (showStarterCheckIn) {
                        ScheduleLogic.starterActions(schedule, now, null, nextSlot)
                    } else null,
                    onStartRoutine = {
                        formEffectiveDate = null
                        formLabelOverride = if (showStarterCheckIn) measurementContext.formLabelOverride else null
                        formStepsOverride = if (showStarterCheckIn) measurementContext.formStepsOverride else null
                        onHighlightCheckInHandled()
                        isFormMode = true
                    },
                    onAddNote = { showNoteSheet = true },
                    onExtraMeasurement = {
                        formLabelOverride = "Extra - ${java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}"
                        formStepsOverride = listOf(MeasurementStep.Pain, MeasurementStep.Temperature, MeasurementStep.Photo)
                        formEffectiveDate = null
                        isFormMode = true
                    },
                    onOpenReport = { onOpenReport?.invoke() }
                )
            }
        }

        if (showExtraPicker) {
            ExtraMeasurementPickerSheet(
                availableSteps = remember(schedule) { measurementStepsFromSchedule(schedule) },
                onDismiss = { showExtraPicker = false },
                onSelect = { step ->
                    showExtraPicker = false
                    formEffectiveDate = null
                    formLabelOverride = "Extra - ${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))}"
                    formStepsOverride = listOf(step)
                    isFormMode = true
                }
            )
        }

        if (showNoteSheet) {
            NoteBottomSheet(
                followUpId = currentFollowUp.id,
                onDismiss = { showNoteSheet = false },
                onSent = {
                    showNoteSheet = false
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            "Added to your file. Assistant replies when you're back online."
                        )
                        SyncManager.scheduleSync(appContext)
                    }
                }
            )
        }

        if (isFormMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Black.copy(alpha = 0.5f))
        ) {
            FocusModeForm(
                followUp = currentFollowUp,
                scheduleKey = formScheduleKeyOverride ?: measurementContext.formScheduleKey,
                effectiveDate = formEffectiveDate,
                labelOverride = formLabelOverride,
                stepsOverride = formStepsOverride,
                onClose = {
                    isFormMode = false
                    formLabelOverride = null
                    formStepsOverride = null
                        formScheduleKeyOverride = null
                    },
                    onSubmitted = {
                        isFormMode = false
                        formLabelOverride = null
                        formStepsOverride = null
                        formScheduleKeyOverride = null
                        coroutineScope.launch {
                            val localEvents = TimelineRepository.getEvents(currentFollowUp.id)
                            val readiness = FileStats.compute(currentFollowUp, localEvents)
                            snackbarHostState.showSnackbar(
                                FileStats.checkInSuccessMessage(
                                    currentFollowUp.daysRemaining,
                                    readiness.readinessPercent
                                )
                            )
                        }
                    }
                )
            }
        }

        if (showScheduleEditor) {
            ScheduleEditorSheet(
                title = stringResource(R.string.schedule_editor_title),
                schedule = schedule,
                isSaving = isSavingSchedule,
                onDismiss = { showScheduleEditor = false },
                onSave = { newSchedule ->
                    isSavingSchedule = true
                    coroutineScope.launch {
                        try {
                            val refreshed = updateFollowUpSchedule(currentFollowUp.id, newSchedule)
                            if (refreshed != null) {
                                currentFollowUp = refreshed
                                onFollowUpUpdated?.invoke(refreshed)
                                ScheduleReminderManager.scheduleForFollowUp(
                                    appContext,
                                    refreshed.id,
                                    refreshed.title,
                                    newSchedule
                                )
                            }
                            showScheduleEditor = false
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            isSavingSchedule = false
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun EmptyStateWelcome() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(White, RoundedCornerShape(12.dp))
                .border(1.dp, Gray200, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text(
                stringResource(R.string.empty_state_welcome),
                style = MaterialTheme.typography.bodyMedium,
                color = Gray600,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun JourneySummary(followUp: FollowUpUi, events: List<TimelineEventResponse>, appointmentDate: LocalDate) {
    val readiness = FileStats.compute(followUp, events)
    val progressFloat = readiness.readinessPercent / 100f
    val formattedApptDate = appointmentDate.format(DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH))

    LpmCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = readiness.tagline,
                style = MaterialTheme.typography.bodySmall,
                color = Gray600,
                lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryItem(value = formattedApptDate, label = stringResource(R.string.summary_appt_date))
                SummaryItem(value = "${followUp.daysRemaining}", label = stringResource(R.string.summary_days_left))
                SummaryItem(value = "${readiness.readinessPercent}%", label = stringResource(R.string.summary_complete))
            }
            Spacer(modifier = Modifier.height(12.dp))
            LpmProgressBar(progress = progressFloat)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(
                    R.string.summary_file_contents,
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
                    color = Black,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Black)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Gray400)
    }
}

@Composable
private fun TopInfoCard(
    followUp: FollowUpUi,
    dueSlotKey: String?,
    nextWindowName: String,
    nextWindowTime: LocalTime?
) {
    var countdownText by remember { mutableStateOf("") }

    LaunchedEffect(nextWindowTime, dueSlotKey) {
        if (nextWindowTime == null || dueSlotKey != null) {
            countdownText = ""
            return@LaunchedEffect
        }
        while (true) {
            val now = LocalTime.now()
            var durationSeconds = ChronoUnit.SECONDS.between(now, nextWindowTime)
            if (durationSeconds < 0) {
                durationSeconds += 24 * 3600
            }
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            val seconds = durationSeconds % 60
            countdownText = String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
            delay(1000)
        }
    }

    LpmCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Status", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                Box(
                    modifier = Modifier
                        .background(MintBadge, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (followUp.isActive) stringResource(R.string.status_ongoing) else stringResource(R.string.status_completed),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MintBadgeText
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(stringResource(R.string.next_appt_in_days, followUp.daysRemaining), style = MaterialTheme.typography.bodySmall, color = TextSecondary)

            val actionText = when {
                dueSlotKey != null -> stringResource(R.string.next_action_now, dueSlotKey)
                nextWindowTime != null -> stringResource(R.string.next_action_in, nextWindowName, countdownText)
                else -> stringResource(R.string.next_action_done_today)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = actionText,
                style = MaterialTheme.typography.bodySmall,
                color = SagePrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CentralTimelineEvent(userEvent: TimelineEventResponse, aiEvent: TimelineEventResponse?, onDelete: (String) -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.dialog_delete_record_title)) },
            text = { Text(stringResource(R.string.dialog_delete_record_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete(userEvent.id)
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    val formatTime = { timeStr: String ->
        runCatching {
            val zdt = java.time.ZonedDateTime.parse(timeStr)
            zdt.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        }.getOrDefault("")
    }

    // Wrap the entire interaction block
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        // --- USER EVENT ---
        val userDateLabel = userEvent.date_label.ifEmpty { "USER" }.uppercase()
        val displayTime = userEvent.effective_at ?: userEvent.created_at
        val userTime = formatTime(displayTime)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(0.45f))
            Box(modifier = Modifier.weight(0.1f), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(SagePrimary, CircleShape)
                        .border(2.dp, CanvasBackground, CircleShape)
                )
            }
            Column(
                modifier = Modifier.weight(0.45f).padding(start = 12.dp, end = 20.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = if (userTime.isNotEmpty()) "$userDateLabel • $userTime" else userDateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .background(CardBackground, RoundedCornerShape(20.dp))
                        .border(1.dp, CardBorderSoft, RoundedCornerShape(20.dp))
                        .padding(14.dp)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { if (userEvent.type == "user") showDeleteConfirm = true }
                        )
                ) {
                    TimelineContentWithPhoto(content = userEvent.content)
                }
            }
        }

        // --- AI ASSISTANT / ROBOT EVENT ---
        if (aiEvent != null) {
            val aiDateLabel = aiEvent.date_label.ifEmpty { "P1 ASSISTANT" }.uppercase()
            val aiTime = formatTime(aiEvent.created_at)

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(0.45f).padding(end = 12.dp, start = 20.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (aiTime.isNotEmpty()) "$aiDateLabel • $aiTime" else aiDateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MintBadgeText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .background(MintBadge.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .border(1.dp, MintBadge, RoundedCornerShape(20.dp))
                            .padding(14.dp)
                    ) {
                        Text(aiEvent.content, style = MaterialTheme.typography.bodySmall, color = TextPrimary, lineHeight = 18.sp)
                    }
                }
                Box(modifier = Modifier.weight(0.1f), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(MintBadgeText, CircleShape)
                            .border(2.dp, CanvasBackground, CircleShape)
                    )
                }
                Spacer(modifier = Modifier.weight(0.45f))
            }
        }
    }
}

@Composable
private fun TimelineContentWithPhoto(content: String) {
    val context = LocalContext.current
    val photoRegex = remember { Regex("(?i)Photo:\\s*([\\w\\d_-]+\\.jpg)") }
    val allPhotos = photoRegex.findAll(content).map { it.groupValues[1] }.toList()
    var fullscreenPhotoFile by remember { mutableStateOf<java.io.File?>(null) }

    // Detect recorded body regions for 3D thumbnail preview
    val allRegions = listOf("Head", "Neck", "Chest", "Back", "Abdomen", "Pelvis", "Shoulder", "Arm", "Hand", "Thigh", "Calf", "Foot")
    val recordedRegions = remember(content) {
        allRegions.filter { region ->
            content.contains(region, ignoreCase = true)
        }
    }
    
    Column {
        // Display text without the raw photo filename lines
        val displayText = if (allPhotos.isNotEmpty()) {
            content.lines().filter { line ->
                !photoRegex.containsMatchIn(line)
            }.joinToString("\n").trim()
        } else {
            content
        }
        
        if (displayText.isNotBlank()) {
            Text(displayText, style = MaterialTheme.typography.bodySmall, color = Black)
        }

        // Display 3D Mannequin Thumbnail Card if pain regions recorded
        if (recordedRegions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceLight,
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth().height(150.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val engine = io.github.sceneview.rememberEngine()
                    val modelLoader = io.github.sceneview.rememberModelLoader(engine)
                    val cameraNode = io.github.sceneview.rememberCameraNode(engine).apply {
                        position = io.github.sceneview.math.Position(0.0f, 1.4f, 22.0f)
                    }
                    val modelNode = io.github.sceneview.rememberNode {
                        io.github.sceneview.node.ModelNode(
                            modelInstance = modelLoader.createModelInstance("mannequin_pbr.glb"),
                            scaleToUnits = 0.075f,
                            centerOrigin = io.github.sceneview.math.Position(0.0f, 0.0f, 0.0f)
                        )
                    }
                    LaunchedEffect(Unit) {
                        var animTime = 0f
                        while (true) {
                            kotlinx.coroutines.delay(16)
                            animTime += 0.0015f
                            modelNode.modelInstance?.animator?.apply {
                                if (animationCount > 0) {
                                    applyAnimation(0, animTime % getAnimationDuration(0))
                                }
                            }
                        }
                    }
                    io.github.sceneview.Scene(
                        modifier = Modifier.fillMaxSize(),
                        engine = engine,
                        modelLoader = modelLoader,
                        cameraNode = cameraNode,
                        childNodes = listOf(modelNode)
                    )
                    
                    // Semi-transparent overlay to prevent interaction
                    Box(modifier = Modifier.fillMaxSize().background(Color.Transparent))
                    
                    Surface(
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Black.copy(alpha = 0.8f)
                    ) {
                        Text(
                            "3D Snapshot",
                            color = White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
        
        // Display all photo thumbnails
        if (allPhotos.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
            ) {
                items(allPhotos.size) { index ->
                    val filename = allPhotos[index]
                    val imgFile = java.io.File(context.filesDir, filename)
                    if (imgFile.exists()) {
                        val bitmap = remember(filename) {
                            android.graphics.BitmapFactory.decodeFile(imgFile.absolutePath)
                        }
                        if (bitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Photo",
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { fullscreenPhotoFile = imgFile },
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }

        // Fullscreen photo viewer (expanded in-place)
        if (fullscreenPhotoFile != null) {
            val file = fullscreenPhotoFile!!
            val bitmap = remember(file) {
                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { fullscreenPhotoFile = null }
            ) {
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                    )
                }
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .clickable { fullscreenPhotoFile = null }
                )
            }
        }
    }
}

@Composable
private fun FutureTimelineEvent(day: Int, label: String, isPast: Boolean = false, onAddMissed: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).alpha(if (isPast) 0.7f else 0.5f),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.weight(0.45f))
        Box(modifier = Modifier.weight(0.1f), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(10.dp).background(if (isPast) Color(0xFFFFA726) else Gray200, CircleShape))
        }
        Column(
            modifier = Modifier.weight(0.45f).padding(start = 12.dp, end = 20.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                if (isPast) stringResource(R.string.timeline_missed) else stringResource(R.string.timeline_upcoming),
                style = MaterialTheme.typography.labelSmall,
                color = if (isPast) Color(0xFFFFA726) else Gray400
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .background(if (isPast) Color(0xFFFFF3E0) else Gray50, RoundedCornerShape(12.dp))
                    .border(1.dp, if (isPast) Color(0xFFFFA726).copy(alpha = 0.3f) else Gray200, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = if (isPast) Black else Gray600)
                    if (isPast && onAddMissed != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.timeline_add_missed),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Black,
                            modifier = Modifier.clickable { onAddMissed() }
                        )
                    }
                }
            }
        }
    }
}

private fun formatMeasurementActions(actions: List<String>): String {
    return actions.map { action ->
        when (action.lowercase()) {
            "pain" -> "pain level"
            "temperature" -> "temperature"
            "photo" -> "a photo"
            "smartwatch" -> "smartwatch data"
            "blood_pressure" -> "blood pressure"
            else -> action
        }
    }.joinToString(", ")
}

@Composable
private fun BottomMeasurementBar(
    dueSlot: ScheduleSlot?,
    nextSlot: ScheduleSlot?,
    nextWindowTime: LocalTime?,
    showMeasurementButton: Boolean,
    showStarterCheckIn: Boolean = false,
    highlightPendingCheckIn: Boolean = false,
    previewActionsOverride: List<String>? = null,
    onStartRoutine: () -> Unit,
    onAddNote: () -> Unit,
    onExtraMeasurement: () -> Unit,
    onOpenReport: () -> Unit
) {
    val previewSlot = dueSlot ?: nextSlot
    val previewActions = previewActionsOverride ?: previewSlot?.actions ?: emptyList()
    val previewText = formatMeasurementActions(previewActions)
    var countdownText by remember { mutableStateOf("") }

    LaunchedEffect(nextWindowTime, dueSlot) {
        if (nextWindowTime == null || dueSlot != null) {
            countdownText = ""
            return@LaunchedEffect
        }
        while (true) {
            val now = LocalTime.now()
            var durationSeconds = ChronoUnit.SECONDS.between(now, nextWindowTime)
            if (durationSeconds < 0) durationSeconds += 24 * 3600
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            val seconds = durationSeconds % 60
            countdownText = String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
            delay(1000)
        }
    }

    Surface(
        color = CardBackground,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = if (highlightPendingCheckIn) 12.dp else 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSoft),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            if (highlightPendingCheckIn) {
                Text(
                    text = stringResource(R.string.notification_checkin_pending),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = SagePrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            when {
                showMeasurementButton && showStarterCheckIn -> {
                    Text(
                        stringResource(R.string.bottom_starter_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (previewText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.bottom_checkin_will_ask, previewText),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onStartRoutine,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SagePrimary)
                    ) {
                        Text(
                            stringResource(R.string.btn_start_baseline),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                showMeasurementButton && dueSlot != null -> {
                    Text(
                        stringResource(R.string.bottom_checkin_now_title, dueSlot.timeKey),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (previewText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.bottom_checkin_will_ask, previewText),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onStartRoutine,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SagePrimary)
                    ) {
                        Text(
                            stringResource(R.string.btn_fill_measurements, dueSlot.timeKey),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                nextSlot != null -> {
                    Text(
                        stringResource(R.string.bottom_next_measurement_at, nextSlot.timeKey),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (previewText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.bottom_next_measurement_prepare, previewText),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    if (countdownText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.bottom_countdown, countdownText),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = SagePrimary
                        )
                    }
                }
                else -> {
                    Text(
                        stringResource(R.string.next_action_done_today),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = CardBorderSoft)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                JourneyQuickAction(
                    icon = { Icon(Icons.Outlined.Edit, contentDescription = null, tint = SagePrimary, modifier = Modifier.size(22.dp)) },
                    label = stringResource(R.string.quick_action_note),
                    onClick = onAddNote
                )
                JourneyQuickAction(
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_body_mannequin),
                            contentDescription = null,
                            tint = SagePrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = stringResource(R.string.quick_action_extra),
                    onClick = onExtraMeasurement,
                    enabled = !showMeasurementButton
                )
            }
        }
    }
}

@Composable
private fun JourneyQuickAction(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Gray50, CircleShape)
                .border(1.dp, Gray200, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Gray600, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteBottomSheet(
    followUpId: String,
    onDismiss: () -> Unit,
    onSent: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = White
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.note_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Black
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.note_sheet_desc),
                style = MaterialTheme.typography.bodySmall,
                color = Gray600
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.note_sheet_placeholder), color = Gray400) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Gray200,
                    unfocusedBorderColor = Gray200,
                    cursorColor = Black
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            LpmPrimaryButton(
                text = if (isSending) "..." else stringResource(R.string.note_sheet_send),
                onClick = {
                    if (isSending || text.isBlank()) return@LpmPrimaryButton
                    isSending = true
                    coroutineScope.launch {
                        try {
                            TimelineRepository.addEvent(
                                followUpId,
                                TimelineEventRequest(content = text.trim(), date_label = "Question")
                            )
                            onSent()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            isSending = false
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtraMeasurementPickerSheet(
    availableSteps: List<MeasurementStep>,
    onDismiss: () -> Unit,
    onSelect: (MeasurementStep) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = White
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.extra_picker_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Black
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.extra_picker_desc),
                style = MaterialTheme.typography.bodySmall,
                color = Gray600
            )
            Spacer(modifier = Modifier.height(16.dp))
            availableSteps.forEach { step ->
                OutlinedButton(
                    onClick = { onSelect(step) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Black)
                ) {
                    Text(
                        stringResource(measurementStepLabel(step)),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusModeForm(
    followUp: FollowUpUi,
    scheduleKey: String,
    effectiveDate: LocalDate? = null,
    labelOverride: String? = null,
    stepsOverride: List<MeasurementStep>? = null,
    onClose: () -> Unit,
    onSubmitted: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isSending by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }

    val steps = remember(followUp.schedule, scheduleKey, stepsOverride) {
        if (stepsOverride != null) return@remember stepsOverride

        val list = mutableListOf<MeasurementStep>()
        val actions = followUp.schedule?.get(scheduleKey) ?: listOf("pain", "temperature")

        actions.forEach { action ->
            when (action.lowercase()) {
                "pain" -> list.add(MeasurementStep.Pain)
                "temperature" -> list.add(MeasurementStep.Temperature)
                "photo" -> list.add(MeasurementStep.Photo)
            }
        }
        if (list.isEmpty()) {
            list.add(MeasurementStep.Pain)
        }
        list
    }

    if (steps.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val hasPain = steps.contains(MeasurementStep.Pain)
    val hasTemp = steps.contains(MeasurementStep.Temperature)
    val hasPhoto = steps.contains(MeasurementStep.Photo)

    // Flat state — all fields on one screen, no step-by-step.
    var painLevel by remember { mutableFloatStateOf(0f) }
    var zoneIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var zoneLabels by remember { mutableStateOf<List<String>>(emptyList()) }
    var painQualities by remember { mutableStateOf(setOf<String>()) }
    var viewSide by remember { mutableStateOf("front") }
    var tempValue by remember { mutableStateOf("") }
    var photoFilename by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = White
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isSending) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Black)
                }
            } else {
                com.preappointment1.app.ui.components.checkin.CheckInScreen(
                    submitLabel = stringResource(R.string.action_save),
                    onClose = onClose,
                    onSubmit = { level, temp, zoneIds, zoneLabels, qualities, mobility, pattern ->
                        isSending = true
                        coroutineScope.launch {
                            val dateLabel = labelOverride ?: if (effectiveDate != null) {
                                "Retroactive - ${effectiveDate.format(DateTimeFormatter.ofPattern("MMM d"))}"
                            } else {
                                scheduleKey
                            }
                            val content = buildString {
                                appendLine("Routine Check-in ($dateLabel):")
                                appendLine("• Pain Level: $level/10")
                                if (zoneLabels.isNotEmpty()) {
                                    appendLine("• Pain Areas: ${zoneLabels.joinToString(", ")}")
                                }
                                appendLine("• Body Temp: ${String.format(java.util.Locale.US, "%.1f", temp)}°C")
                                appendLine("• Mobility Impact: $mobility")
                                appendLine("• Temporal Pattern: $pattern")
                                if (qualities.isNotEmpty()) {
                                    appendLine("• Characteristics: ${qualities.joinToString(", ")}")
                                }
                            }
                            try {
                                TimelineRepository.addEvent(
                                    followUp.id,
                                    TimelineEventRequest(
                                        content = content.trim(),
                                        date_label = dateLabel,
                                        effective_date = effectiveDate?.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                    )
                                )
                                isSending = false
                                onSubmitted()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                isSending = false
                                submitError = "Could not save locally."
                            }
                        }
                    }
                )
            }
        }
    }
}
