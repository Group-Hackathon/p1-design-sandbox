package com.preappointment1.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.preappointment1.app.billing.BillingManager
import com.preappointment1.app.data.AuthHelper
import com.preappointment1.app.data.SessionManager
import com.preappointment1.app.data.repository.DocumentsRepository
import com.preappointment1.app.data.repository.FollowUpRepository
import com.preappointment1.app.data.repository.ReportRepository
import com.preappointment1.app.data.repository.TimelineRepository
import com.preappointment1.app.notifications.NotificationDeepLink
import com.preappointment1.app.notifications.NotificationHelper
import com.preappointment1.app.notifications.NotificationIntents
import com.preappointment1.app.ui.navigation.AppDestination
import com.preappointment1.app.ui.navigation.AppNavigation
import com.preappointment1.app.ui.theme.LivingPatientMemoryTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val deepLinkState = mutableStateOf<NotificationDeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize core application repositories and singletons
        SessionManager.init(this)
        AuthHelper.init(this)
        FollowUpRepository.init(this)
        TimelineRepository.init(this)
        ReportRepository.init(this)
        DocumentsRepository.init(this)
        BillingManager.initialize(this)
        NotificationHelper.createNotificationChannel(this)

        deepLinkState.value = NotificationIntents.from(intent)

        CoroutineScope(Dispatchers.IO).launch {
            val ok = AuthHelper.ensureAuthenticated()
            Log.d("P1_APP", if (ok) "User session initialized" else "Session authentication failed")
        }

        setContent {
            val deepLink by deepLinkState
            val hasSeenWelcome = SessionManager.getToken() != null
            val initialDestination = if (hasSeenWelcome) AppDestination.Home else AppDestination.Welcome

            LivingPatientMemoryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        initialScreen = initialDestination,
                        openMeasurementFormOnLaunch = deepLink?.openMeasurementForm == true,
                        highlightCheckIn = deepLink != null,
                        notificationScheduleKey = deepLink?.scheduleKey
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
