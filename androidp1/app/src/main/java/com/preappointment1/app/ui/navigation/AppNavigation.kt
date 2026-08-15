package com.preappointment1.app.ui.navigation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.preappointment1.app.R
import com.preappointment1.app.data.SessionManager
import com.preappointment1.app.data.model.TimelineEventRequest
import com.preappointment1.app.data.model.TimelineEventResponse
import com.preappointment1.app.data.repository.FollowUpRepository
import com.preappointment1.app.data.repository.TimelineRepository
import com.preappointment1.app.notifications.ScheduleReminderManager
import com.preappointment1.app.ui.components.*
import com.preappointment1.app.ui.screens.*
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class AppDestination {
    Welcome,
    Home,
    Onboarding,
    Journey,
    Notifications,
    Report,
    Documents,
    Profile
}

@Composable
fun AppNavigation(
    initialScreen: AppDestination,
    openMeasurementFormOnLaunch: Boolean = false,
    highlightCheckIn: Boolean = false,
    notificationScheduleKey: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val backStack = remember { mutableStateListOf(initialScreen) }
    var currentDestination by remember { mutableStateOf(initialScreen) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var selectedFollowUp by remember { mutableStateOf<FollowUpUi?>(null) }
    var followUps by remember { mutableStateOf<List<FollowUpUi>>(emptyList()) }
    var activeTimelineEvents by remember { mutableStateOf<List<TimelineEventResponse>>(emptyList()) }

    var formLaunchPending by remember { mutableStateOf(openMeasurementFormOnLaunch) }
    var photoModePending by remember { mutableStateOf(false) }
    var checkInHighlightPending by remember { mutableStateOf(highlightCheckIn) }
    var scheduleKeyPending by remember { mutableStateOf(notificationScheduleKey) }

    fun clearPendingFlags() {
        formLaunchPending = false
        photoModePending = false
        checkInHighlightPending = false
        scheduleKeyPending = null
    }

    // Root-level navigation: replace the whole stack (used for bottom tabs,
    // drawer selection, welcome/onboarding transitions).
    fun switchTab(destination: AppDestination) {
        backStack.clear()
        if (destination != AppDestination.Home) {
            backStack.add(AppDestination.Home)
        }
        backStack.add(destination)
        if (destination != AppDestination.Journey) {
            clearPendingFlags()
        }
        currentDestination = destination
    }

    // Forward navigation: push onto the back stack.
    fun navigateTo(destination: AppDestination) {
        backStack.add(destination)
        currentDestination = destination
    }

    fun popBack() {
        if (backStack.size > 1) {
            val leaving = backStack.removeAt(backStack.lastIndex)
            if (leaving == AppDestination.Journey) {
                clearPendingFlags()
            }
            currentDestination = backStack.last()
        }
    }

    // System back button walks the stack; at the root the system default applies.
    BackHandler(enabled = backStack.size > 1) {
        popBack()
    }

    LaunchedEffect(refreshKey) {
        val (loadedFollowUps, _) = FollowUpRepository.loadFollowUpsWithSync()
        followUps = loadedFollowUps
        val active = selectedFollowUp?.let { sel -> followUps.find { it.id == sel.id } }
            ?: followUps.firstOrNull { it.daysRemaining > 0 }
            ?: followUps.firstOrNull()
        selectedFollowUp = active

        if (active != null) {
            try {
                activeTimelineEvents = TimelineRepository.getEvents(active.id)
            } catch (_: Exception) {}
        }
        ScheduleReminderManager.rescheduleActiveFollowUps(context, followUps)
    }

    // Screens that require a selected file redirect to Home when none is available.
    LaunchedEffect(currentDestination, selectedFollowUp) {
        if (selectedFollowUp == null &&
            (currentDestination == AppDestination.Journey ||
                currentDestination == AppDestination.Report ||
                currentDestination == AppDestination.Documents)
        ) {
            switchTab(AppDestination.Home)
        }
    }

    val onTabSelected: (StitchTab) -> Unit = { tab ->
        when (tab) {
            StitchTab.HOME -> switchTab(AppDestination.Home)
            StitchTab.TIMELINE -> {
                if (selectedFollowUp == null) {
                    selectedFollowUp = followUps.firstOrNull { it.daysRemaining > 0 } ?: followUps.firstOrNull()
                }
                switchTab(AppDestination.Journey)
            }
            StitchTab.PROGRESS -> {
                if (selectedFollowUp == null) {
                    selectedFollowUp = followUps.firstOrNull { it.daysRemaining > 0 } ?: followUps.firstOrNull()
                }
                switchTab(AppDestination.Report)
            }
            StitchTab.PREP -> {
                if (selectedFollowUp == null) {
                    selectedFollowUp = followUps.firstOrNull { it.daysRemaining > 0 } ?: followUps.firstOrNull()
                }
                switchTab(AppDestination.Documents)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            LpmAppDrawer(
                followUps = followUps,
                selectedFollowUpId = selectedFollowUp?.id,
                onSelectFollowUp = { followUp ->
                    selectedFollowUp = followUp
                    switchTab(AppDestination.Journey)
                    scope.launch { drawerState.close() }
                },
                onStartNewTracking = {
                    navigateTo(AppDestination.Onboarding)
                    scope.launch { drawerState.close() }
                },
                onOpenNotifications = {
                    navigateTo(AppDestination.Notifications)
                    scope.launch { drawerState.close() }
                },
                onClose = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Crossfade(
            targetState = currentDestination,
            label = "ScreenTransition",
            modifier = Modifier.fillMaxSize()
        ) { destination ->
            when (destination) {
                AppDestination.Welcome -> {
                    WelcomeScreen(
                        onStartTracking = { navigateTo(AppDestination.Onboarding) },
                        onGoToHome = { switchTab(AppDestination.Home) }
                    )
                }

                AppDestination.Home -> {
                    StitchHomeScreen(
                        patientName = SessionManager.getUserName() ?: "Patient 1",
                        followUps = followUps,
                        timelineEvents = activeTimelineEvents,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onOpenFollowUp = { followUp ->
                            selectedFollowUp = followUp
                        },
                        onStartNewTracking = { navigateTo(AppDestination.Onboarding) },
                        onOpenNotifications = { navigateTo(AppDestination.Notifications) },
                        onOpenSettings = { navigateTo(AppDestination.Profile) },
                        onOpenProfile = { scope.launch { drawerState.open() } },
                        onOpenAddPhoto = {
                            scope.launch {
                                selectedFollowUp = selectedFollowUp ?: FollowUpRepository.getOrCreateActiveFollowUp()
                                formLaunchPending = true
                                photoModePending = true
                                navigateTo(AppDestination.Journey)
                            }
                        },
                        onOpenQuickLog = {
                            scope.launch {
                                selectedFollowUp = selectedFollowUp ?: FollowUpRepository.getOrCreateActiveFollowUp()
                                formLaunchPending = true
                                photoModePending = false
                                navigateTo(AppDestination.Journey)
                            }
                        },
                        onOpenTimeline = {
                            scope.launch {
                                selectedFollowUp = selectedFollowUp ?: FollowUpRepository.getOrCreateActiveFollowUp()
                                navigateTo(AppDestination.Journey)
                            }
                        },
                        onSaveVoiceLog = { transcript, insight ->
                            scope.launch {
                                try {
                                    val active = selectedFollowUp ?: FollowUpRepository.getOrCreateActiveFollowUp()
                                    val formattedContent = if (!insight.isNullOrBlank()) {
                                        context.getString(R.string.voice_event_with_ai, transcript.trim(), insight)
                                    } else {
                                        context.getString(R.string.voice_event_plain, transcript.trim())
                                    }

                                    TimelineRepository.addEvent(
                                        subscriptionId = active.id,
                                        request = TimelineEventRequest(
                                            content = formattedContent,
                                            date_label = "Voice Check-in",
                                            effective_date = LocalDate.now().toString()
                                        )
                                    )
                                    refreshKey++
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.voice_saved_toast),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        },
                        onSentimentSelected = { sentiment ->
                            scope.launch {
                                try {
                                    val active = selectedFollowUp ?: FollowUpRepository.getOrCreateActiveFollowUp()
                                    val label = when (sentiment) {
                                        FeelingSentiment.BETTER -> "Better"
                                        FeelingSentiment.SAME -> "Stable"
                                        FeelingSentiment.WORSE -> "Unwell / Worse"
                                    }
                                    TimelineRepository.addEvent(
                                        subscriptionId = active.id,
                                        request = TimelineEventRequest(
                                            content = "Daily status: $label",
                                            date_label = "Well-being",
                                            effective_date = LocalDate.now().toString()
                                        )
                                    )
                                    refreshKey++
                                } catch (_: Exception) {}
                            }
                        },
                        activeTab = StitchTab.HOME,
                        onTabSelected = onTabSelected
                    )
                }

                AppDestination.Onboarding -> {
                    OnboardingScreen(
                        onBack = { switchTab(AppDestination.Home) },
                        onFollowUpCreated = { newFollowUpId ->
                            refreshKey++
                            scope.launch {
                                val (loaded, _) = FollowUpRepository.loadFollowUpsWithSync()
                                followUps = loaded
                                selectedFollowUp = followUps.find { it.id == newFollowUpId }
                                switchTab(AppDestination.Journey)
                            }
                        }
                    )
                }

                AppDestination.Journey -> {
                    val followUp = selectedFollowUp
                    if (followUp == null) {
                        // Redirect handled by the LaunchedEffect guard above.
                        Spacer(modifier = Modifier.fillMaxSize())
                    } else {
                        JourneyScreen(
                            followUp = followUp,
                            onBack = { popBack() },
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onOpenReport = { navigateTo(AppDestination.Report) },
                            onOpenDocuments = { navigateTo(AppDestination.Documents) },
                            onFollowUpUpdated = { updated ->
                                selectedFollowUp = updated
                                followUps = followUps.map { if (it.id == updated.id) updated else it }
                                refreshKey++
                            },
                            openMeasurementFormOnLaunch = formLaunchPending,
                            onMeasurementFormLaunchHandled = { formLaunchPending = false },
                            openPhotoModeOnLaunch = photoModePending,
                            onPhotoModeLaunchHandled = { photoModePending = false },
                            highlightPendingCheckIn = checkInHighlightPending,
                            onHighlightCheckInHandled = { checkInHighlightPending = false },
                            notificationScheduleKey = scheduleKeyPending,
                            activeTab = StitchTab.TIMELINE,
                            onTabSelected = onTabSelected
                        )
                    }
                }

                AppDestination.Notifications -> {
                    val activeFollowUp = followUps
                        .filter { it.daysRemaining > 0 }
                        .maxByOrNull { it.startsAt }
                    NotificationsScreen(
                        activeFollowUp = activeFollowUp,
                        onBack = { popBack() },
                        onScheduleUpdated = { refreshKey++ }
                    )
                }

                AppDestination.Report -> {
                    val followUp = selectedFollowUp
                    if (followUp == null) {
                        // Redirect handled by the LaunchedEffect guard above.
                        Spacer(modifier = Modifier.fillMaxSize())
                    } else {
                        ReportScreen(
                            followUp = followUp,
                            onBack = { popBack() },
                            activeTab = StitchTab.PROGRESS,
                            onTabSelected = onTabSelected
                        )
                    }
                }

                AppDestination.Documents -> {
                    val followUp = selectedFollowUp
                    if (followUp == null) {
                        // Redirect handled by the LaunchedEffect guard above.
                        Spacer(modifier = Modifier.fillMaxSize())
                    } else {
                        DocumentsScreen(
                            followUp = followUp,
                            onBack = { popBack() },
                            activeTab = StitchTab.PREP,
                            onTabSelected = onTabSelected
                        )
                    }
                }

                AppDestination.Profile -> {
                    ProfileScreen(
                        onBack = { popBack() },
                        onLogout = {
                            refreshKey++
                            switchTab(AppDestination.Welcome)
                        }
                    )
                }
            }
        }
    }
}
