package com.preappointment1.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.preappointment1.app.R
import com.preappointment1.app.data.AuthHelper
import com.preappointment1.app.data.SessionManager
import com.preappointment1.app.data.api.ApiClient
import com.preappointment1.app.billing.BillingManager
import com.preappointment1.app.notifications.NotificationDeepLink
import com.preappointment1.app.notifications.NotificationIntents
import com.preappointment1.app.notifications.NotificationHelper
import com.preappointment1.app.notifications.ScheduleReminderManager
import androidx.compose.material.icons.filled.LocalHospital
import com.preappointment1.app.ui.screens.*
import com.preappointment1.app.ui.theme.*
import com.preappointment1.app.data.repository.FollowUpRepository
import com.preappointment1.app.data.repository.TimelineRepository
import com.preappointment1.app.data.sync.SyncManager
import com.preappointment1.app.schedule.ScheduleLogic
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val deepLinkState = mutableStateOf<NotificationDeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionManager.init(this)
        AuthHelper.init(this)
        com.preappointment1.app.data.repository.FollowUpRepository.init(this)
        com.preappointment1.app.data.repository.TimelineRepository.init(this)
        com.preappointment1.app.data.repository.ReportRepository.init(this)
        com.preappointment1.app.data.repository.DocumentsRepository.init(this)
        BillingManager.initialize(this)
        NotificationHelper.createNotificationChannel(this)
        deepLinkState.value = NotificationIntents.from(intent)

        CoroutineScope(Dispatchers.IO).launch {
            val ok = AuthHelper.ensureAuthenticated()
            Log.d("LPM_APP", if (ok) "Session ready" else "Session setup failed")
        }

        setContent {
            val deepLink by deepLinkState
            LivingPatientMemoryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot(
                        deepLink = deepLink,
                        onDeepLinkHandled = { deepLinkState.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkState.value = NotificationIntents.from(intent)
    }
}

private enum class AppScreen {
    Splash, Welcome, Home, NewFollowUp, Journey, Notifications, Profile, Report, Documents
}

@Composable
private fun AppRoot(
    deepLink: NotificationDeepLink? = null,
    onDeepLinkHandled: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var screen by remember { mutableStateOf(AppScreen.Splash) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var selectedFollowUp by remember { mutableStateOf<FollowUpUi?>(null) }
    var hasSeenWelcome by remember { mutableStateOf(SessionManager.getToken() != null) }
    var pendingFollowUpId by remember { mutableStateOf<String?>(null) }
    var openMeasurementFormOnLaunch by remember { mutableStateOf(false) }
    var highlightCheckIn by remember { mutableStateOf(false) }
    var notificationScheduleKey by remember { mutableStateOf<String?>(null) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var followUps by remember { mutableStateOf<List<FollowUpUi>>(emptyList()) }
    var timelineEventsBySubId by remember { mutableStateOf<Map<String, List<com.preappointment1.app.data.model.TimelineEventResponse>>>(emptyMap()) }
    var isOfflineMode by remember { mutableStateOf(false) }
    var pendingSyncCount by remember { mutableIntStateOf(0) }
    var followUpsLoading by remember { mutableStateOf(false) }
    var followUpsLoadComplete by remember { mutableStateOf(false) }
    val now = remember { mutableStateOf(LocalTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now.value = LocalTime.now()
        }
    }

    val hasPendingCheckIn = remember(followUps, timelineEventsBySubId, now.value) {
        followUps.any { followUp ->
            followUp.daysRemaining > 0 && followUp.schedule?.let { schedule ->
                ScheduleLogic.hasDueCheckInNow(
                    schedule,
                    timelineEventsBySubId[followUp.id] ?: emptyList(),
                    now.value
                )
            } == true
        }
    }

    LaunchedEffect(deepLink, followUps, followUpsLoadComplete, hasSeenWelcome) {
        val link = deepLink ?: return@LaunchedEffect
        if (!hasSeenWelcome || !followUpsLoadComplete) return@LaunchedEffect
        val found = followUps.find { it.id == link.subscriptionId }
        if (found == null) {
            onDeepLinkHandled()
            screen = AppScreen.Home
            return@LaunchedEffect
        }
        selectedFollowUp = found
        notificationScheduleKey = link.scheduleKey
        openMeasurementFormOnLaunch = link.openMeasurementForm
        highlightCheckIn = true
        screen = AppScreen.Journey
        onDeepLinkHandled()
    }

    LaunchedEffect(Unit) {
        TimelineRepository.observePendingCount().collect { count ->
            pendingSyncCount = count
        }
    }

    LaunchedEffect(refreshKey) {
        if (!hasSeenWelcome) return@LaunchedEffect
        followUpsLoading = true
        followUpsLoadComplete = false
        try {
            val (loadedFollowUps, synced) = FollowUpRepository.loadFollowUpsWithSync()
            followUps = loadedFollowUps
            isOfflineMode = !synced
            val active = followUps.filter { it.daysRemaining > 0 && it.schedule != null }
            timelineEventsBySubId = active.associate { followUp ->
                followUp.id to TimelineRepository.getEvents(followUp.id)
            }
            if (synced) {
                SyncManager.scheduleSync(context)
            }
            ScheduleReminderManager.rescheduleActiveFollowUps(context, followUps)

            if (pendingFollowUpId != null) {
                val found = followUps.find { it.id == pendingFollowUpId }
                if (found != null) {
                    selectedFollowUp = found
                    screen = AppScreen.Journey
                } else {
                    screen = AppScreen.Home
                }
                pendingFollowUpId = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (deepLink != null) {
                onDeepLinkHandled()
                screen = AppScreen.Home
            }
        } finally {
            followUpsLoading = false
            followUpsLoadComplete = true
        }
    }

    LaunchedEffect(Unit) {
        if (deepLink != null) return@LaunchedEffect
        delay(1500)
        screen = if (hasSeenWelcome) AppScreen.Home else AppScreen.Welcome
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = screen == AppScreen.Home || screen == AppScreen.Journey || screen == AppScreen.Profile,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = White,
                modifier = Modifier.width(300.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    // Profile Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(com.preappointment1.app.ui.theme.Gray200)
                            .clickable {
                                screen = AppScreen.Profile
                                scope.launch { drawerState.close() }
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val drawerName = com.preappointment1.app.data.SessionManager.getUserName()
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                        val drawerInitial = drawerName?.take(1)?.uppercase() ?: "P"
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(drawerInitial, color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                drawerName ?: stringResource(R.string.patient_name_placeholder),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Black
                            )
                            Text(stringResource(R.string.view_profile), fontSize = 12.sp, color = com.preappointment1.app.ui.theme.Gray600)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.my_trackings),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = com.preappointment1.app.ui.theme.Gray400,
                        letterSpacing = 1.sp
                    )
                    
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(followUps) { followUp ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedFollowUp = followUp
                                        screen = AppScreen.Journey
                                        scope.launch { drawerState.close() }
                                    }
                                    .padding(horizontal = 24.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(if (followUp.isActive) Black else Gray200, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = followUp.title,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                    color = Black
                                )
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        screen = AppScreen.NewFollowUp
                                        scope.launch { drawerState.close() }
                                    }
                                    .padding(horizontal = 24.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.AddCircle, contentDescription = "Add", tint = Gray400, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.start_new_tracking),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                    color = Gray400
                                )
                            }
                        }
                    }
                }
            }
        }
    ) {
        when (screen) {
            AppScreen.Splash -> SplashScreen()

            AppScreen.Welcome -> WelcomeScreen(
                onStartTracking = {
                    hasSeenWelcome = true
                    screen = AppScreen.NewFollowUp
                },
                onGoToHome = {
                    hasSeenWelcome = true
                    screen = AppScreen.Home
                }
            )

            AppScreen.Home -> {
                val activeFollowUp = followUps.firstOrNull { it.daysRemaining > 0 } ?: followUps.firstOrNull()
                val currentUserName = SessionManager.getUserName() ?: "Sarah"

                StitchHomeScreen(
                    patientName = currentUserName,
                    activeTab = com.preappointment1.app.ui.components.StitchTab.HOME,
                    onTabSelected = { tab ->
                        when (tab) {
                            com.preappointment1.app.ui.components.StitchTab.HOME -> { /* already on home */ }
                            com.preappointment1.app.ui.components.StitchTab.TIMELINE -> {
                                if (activeFollowUp != null) {
                                    selectedFollowUp = activeFollowUp
                                    screen = AppScreen.Journey
                                } else {
                                    screen = AppScreen.NewFollowUp
                                }
                            }
                            com.preappointment1.app.ui.components.StitchTab.PROGRESS -> {
                                if (activeFollowUp != null) {
                                    selectedFollowUp = activeFollowUp
                                    screen = AppScreen.Report
                                } else {
                                    scope.launch { drawerState.open() }
                                }
                            }
                            com.preappointment1.app.ui.components.StitchTab.PREP -> {
                                screen = AppScreen.Documents
                            }
                        }
                    },
                    onOpenSettings = { screen = AppScreen.Notifications },
                    onOpenAddPhoto = {
                        if (activeFollowUp != null) {
                            selectedFollowUp = activeFollowUp
                            openMeasurementFormOnLaunch = true
                            screen = AppScreen.Journey
                        } else {
                            screen = AppScreen.Documents
                        }
                    },
                    onOpenQuickLog = {
                        if (activeFollowUp != null) {
                            selectedFollowUp = activeFollowUp
                            openMeasurementFormOnLaunch = true
                            screen = AppScreen.Journey
                        } else {
                            screen = AppScreen.NewFollowUp
                        }
                    },
                    onSaveVoiceLog = { transcript, _ ->
                        scope.launch {
                            if (activeFollowUp != null) {
                                TimelineRepository.addEvent(
                                    subscriptionId = activeFollowUp.id,
                                    request = com.preappointment1.app.data.model.TimelineEventRequest(
                                        content = "🎙️ Voice log: $transcript",
                                        date_label = "Voice Check-in",
                                        effective_date = java.time.LocalDate.now().toString()
                                    )
                                )
                                refreshKey++
                            }
                        }
                    },
                    onSentimentSelected = { sentiment ->
                        scope.launch {
                            if (activeFollowUp != null) {
                                TimelineRepository.addEvent(
                                    subscriptionId = activeFollowUp.id,
                                    request = com.preappointment1.app.data.model.TimelineEventRequest(
                                        content = "Mood sentiment: ${sentiment.label}",
                                        date_label = "Daily Mood",
                                        effective_date = java.time.LocalDate.now().toString()
                                    )
                                )
                                refreshKey++
                            }
                        }
                    }
                )
            }

            AppScreen.Profile -> Scaffold(
                topBar = {
                    MainTopBar(
                        title = stringResource(R.string.profile_title),
                        onOpenDrawer = { scope.launch { drawerState.open() } }
                    )
                }
            ) { padding ->
                ProfileScreen(
                    onLogout = {
                        hasSeenWelcome = false
                        screen = AppScreen.Welcome
                    },
                    modifier = Modifier.padding(padding)
                )
            }

            AppScreen.NewFollowUp -> OnboardingScreen(
                onBack = { screen = AppScreen.Home },
                onFollowUpCreated = { newId ->
                    pendingFollowUpId = newId
                    refreshKey++
                }
            )

            AppScreen.Journey -> {
                val followUp = selectedFollowUp
                if (followUp == null) {
                    screen = AppScreen.Home
                } else {
                    JourneyScreen(
                        followUp = followUp,
                        onBack = {
                            refreshKey++
                            openMeasurementFormOnLaunch = false
                            highlightCheckIn = false
                            notificationScheduleKey = null
                            screen = AppScreen.Home
                        },
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onOpenReport = { screen = AppScreen.Report },
                        onOpenDocuments = { screen = AppScreen.Documents },
                        onFollowUpUpdated = { updated ->
                            selectedFollowUp = updated
                            followUps = followUps.map { if (it.id == updated.id) updated else it }
                        },
                        openMeasurementFormOnLaunch = openMeasurementFormOnLaunch,
                        onMeasurementFormLaunchHandled = { openMeasurementFormOnLaunch = false },
                        highlightPendingCheckIn = highlightCheckIn,
                        onHighlightCheckInHandled = { highlightCheckIn = false },
                        notificationScheduleKey = notificationScheduleKey
                    )
                }
            }

            AppScreen.Notifications -> {
                val activeFollowUp = followUps
                    .filter { it.daysRemaining > 0 }
                    .maxByOrNull { it.startsAt }
                NotificationsScreen(
                    activeFollowUp = activeFollowUp,
                    onBack = { screen = AppScreen.Home },
                    onScheduleUpdated = { refreshKey++ }
                )
            }

            AppScreen.Report -> {
                val followUp = selectedFollowUp
                if (followUp == null) {
                    screen = AppScreen.Journey
                } else {
                    ReportScreen(
                        followUp = followUp,
                        onBack = { screen = AppScreen.Journey }
                    )
                }
            }

            AppScreen.Documents -> {
                val followUp = selectedFollowUp
                if (followUp == null) {
                    screen = AppScreen.Journey
                } else {
                    DocumentsScreen(
                        followUp = followUp,
                        onBack = { screen = AppScreen.Journey }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    title: String,
    onOpenDrawer: () -> Unit,
    hasPendingTasks: Boolean = false,
    pendingSyncCount: Int = 0,
    isOfflineMode: Boolean = false,
    onOpenNotifications: (() -> Unit)? = null
) {
    androidx.compose.material3.TopAppBar(
        title = {
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-1).sp)
                if (isOfflineMode || pendingSyncCount > 0) {
                    Text(
                        text = when {
                            pendingSyncCount > 0 && isOfflineMode ->
                                "Offline · $pendingSyncCount waiting to sync"
                            pendingSyncCount > 0 -> "$pendingSyncCount waiting to sync"
                            else -> "Offline — your file works locally"
                        },
                        fontSize = 11.sp,
                        color = com.preappointment1.app.ui.theme.Gray600
                    )
                }
            }
        },
        navigationIcon = {
            androidx.compose.material3.IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Outlined.Menu, contentDescription = "Menu")
            }
        },
        actions = {
            if (onOpenNotifications != null) {
                Box {
                    androidx.compose.material3.IconButton(onClick = onOpenNotifications) {
                        Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", tint = com.preappointment1.app.ui.theme.Black)
                    }
                    if (hasPendingTasks) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .align(Alignment.TopEnd)
                                .padding(top = 12.dp, end = 12.dp)
                                .background(com.preappointment1.app.ui.theme.Black, CircleShape)
                        )
                    }
                }
            }
        },
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
            containerColor = com.preappointment1.app.ui.theme.White,
            titleContentColor = com.preappointment1.app.ui.theme.Black
        )
    )
}

@Composable
private fun SplashScreen() {
    var visible by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val versionName = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(600)),
                exit = fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MintBadge),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalHospital,
                            contentDescription = null,
                            tint = SagePrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "P1 Health",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "YOUR HEALTH COMPANION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = SagePrimary
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            Text(
                text = "Terms of Use & Privacy Policy",
                fontSize = 11.sp,
                color = Gray500,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .clickable { uriHandler.openUri("https://p1-privacy-policy.pages.dev/") }
            )
            Text(
                text = "v$versionName",
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = Gray400
            )
        }
    }
}
