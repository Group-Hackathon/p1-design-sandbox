package com.preappointment1.app.ui.navigation

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    Documents
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

    var currentDestination by remember { mutableStateOf(initialScreen) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var selectedFollowUp by remember { mutableStateOf<FollowUpUi?>(null) }
    var followUps by remember { mutableStateOf<List<FollowUpUi>>(emptyList()) }
    var activeTimelineEvents by remember { mutableStateOf<List<TimelineEventResponse>>(emptyList()) }

    var formLaunchPending by remember { mutableStateOf(openMeasurementFormOnLaunch) }
    var checkInHighlightPending by remember { mutableStateOf(highlightCheckIn) }
    var scheduleKeyPending by remember { mutableStateOf(notificationScheduleKey) }

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

    val onTabSelected: (StitchTab) -> Unit = { tab ->
        when (tab) {
            StitchTab.HOME -> currentDestination = AppDestination.Home
            StitchTab.TIMELINE -> {
                if (selectedFollowUp == null) {
                    selectedFollowUp = followUps.firstOrNull { it.daysRemaining > 0 } ?: followUps.firstOrNull()
                }
                currentDestination = AppDestination.Journey
            }
            StitchTab.PROGRESS -> {
                if (selectedFollowUp == null) {
                    selectedFollowUp = followUps.firstOrNull { it.daysRemaining > 0 } ?: followUps.firstOrNull()
                }
                currentDestination = AppDestination.Report
            }
            StitchTab.PREP -> {
                if (selectedFollowUp == null) {
                    selectedFollowUp = followUps.firstOrNull { it.daysRemaining > 0 } ?: followUps.firstOrNull()
                }
                currentDestination = AppDestination.Documents
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
                    currentDestination = AppDestination.Journey
                    scope.launch { drawerState.close() }
                },
                onStartNewTracking = {
                    currentDestination = AppDestination.Onboarding
                    scope.launch { drawerState.close() }
                },
                onOpenNotifications = {
                    currentDestination = AppDestination.Notifications
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
                        onStartTracking = { currentDestination = AppDestination.Onboarding },
                        onGoToHome = { currentDestination = AppDestination.Home }
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
                            currentDestination = AppDestination.Journey
                        },
                        onStartNewTracking = { currentDestination = AppDestination.Onboarding },
                        onOpenNotifications = { currentDestination = AppDestination.Notifications },
                        onOpenSettings = { currentDestination = AppDestination.Notifications },
                        onOpenProfile = { scope.launch { drawerState.open() } },
                        onOpenAddPhoto = {
                            scope.launch {
                                selectedFollowUp = selectedFollowUp ?: FollowUpRepository.getOrCreateActiveFollowUp()
                                currentDestination = AppDestination.Documents
                            }
                        },
                        onOpenQuickLog = {
                            scope.launch {
                                selectedFollowUp = selectedFollowUp ?: FollowUpRepository.getOrCreateActiveFollowUp()
                                formLaunchPending = true
                                currentDestination = AppDestination.Journey
                            }
                        },
                        onOpenTimeline = {
                            scope.launch {
                                selectedFollowUp = selectedFollowUp ?: FollowUpRepository.getOrCreateActiveFollowUp()
                                currentDestination = AppDestination.Journey
                            }
                        },
                        onSaveVoiceLog = { transcript, insight ->
                            scope.launch {
                                try {
                                    val active = selectedFollowUp ?: FollowUpRepository.getOrCreateActiveFollowUp()
                                    val formattedContent = if (!insight.isNullOrBlank()) {
                                        "🎙️ Voice Check-in: ${transcript.trim()}\n\n💡 AI Assessment: $insight"
                                    } else {
                                        "🎙️ Voice Check-in: ${transcript.trim()}"
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
                                        "Voice check-in saved to your timeline",
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
                        onBack = { currentDestination = AppDestination.Home },
                        onFollowUpCreated = { newFollowUpId ->
                            refreshKey++
                            scope.launch {
                                val (loaded, _) = FollowUpRepository.loadFollowUpsWithSync()
                                followUps = loaded
                                selectedFollowUp = followUps.find { it.id == newFollowUpId }
                                currentDestination = AppDestination.Journey
                            }
                        }
                    )
                }

                AppDestination.Journey -> {
                    val followUp = selectedFollowUp
                    if (followUp == null) {
                        currentDestination = AppDestination.Home
                    } else {
                        JourneyScreen(
                            followUp = followUp,
                            onBack = {
                                refreshKey++
                                formLaunchPending = false
                                checkInHighlightPending = false
                                scheduleKeyPending = null
                                currentDestination = AppDestination.Home
                            },
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onOpenReport = { currentDestination = AppDestination.Report },
                            onOpenDocuments = { currentDestination = AppDestination.Documents },
                            onFollowUpUpdated = { updated ->
                                selectedFollowUp = updated
                                followUps = followUps.map { if (it.id == updated.id) updated else it }
                                refreshKey++
                            },
                            openMeasurementFormOnLaunch = formLaunchPending,
                            onMeasurementFormLaunchHandled = { formLaunchPending = false },
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
                        onBack = { currentDestination = AppDestination.Home },
                        onScheduleUpdated = { refreshKey++ }
                    )
                }

                AppDestination.Report -> {
                    val followUp = selectedFollowUp
                    if (followUp == null) {
                        currentDestination = AppDestination.Home
                    } else {
                        ReportScreen(
                            followUp = followUp,
                            onBack = { currentDestination = AppDestination.Home },
                            activeTab = StitchTab.PROGRESS,
                            onTabSelected = onTabSelected
                        )
                    }
                }

                AppDestination.Documents -> {
                    val followUp = selectedFollowUp
                    if (followUp == null) {
                        currentDestination = AppDestination.Home
                    } else {
                        DocumentsScreen(
                            followUp = followUp,
                            onBack = { currentDestination = AppDestination.Home },
                            activeTab = StitchTab.PREP,
                            onTabSelected = onTabSelected
                        )
                    }
                }
            }
        }
    }
}
