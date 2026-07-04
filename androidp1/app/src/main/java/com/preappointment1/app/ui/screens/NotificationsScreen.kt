package com.preappointment1.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.preappointment1.app.R
import com.preappointment1.app.data.updateFollowUpSchedule
import com.preappointment1.app.notifications.NotificationHelper
import com.preappointment1.app.notifications.ScheduleReminderManager
import com.preappointment1.app.schedule.ScheduleDefaults
import com.preappointment1.app.ui.components.*
import com.preappointment1.app.ui.theme.*
import com.preappointment1.app.worker.NotificationScheduler
import kotlinx.coroutines.launch

@Composable
fun NotificationsScreen(
    activeFollowUp: FollowUpUi?,
    onBack: () -> Unit,
    onScheduleUpdated: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showEditor by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val displaySchedule = remember(activeFollowUp?.schedule) {
        activeFollowUp?.schedule ?: ScheduleDefaults.load(context)
    }

    val scheduleReminders = remember(displaySchedule) {
        ScheduleReminderManager.remindersFromSchedule(displaySchedule)
    }

    var testSent by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                sendTestNotification(context) { testSent = true }
            }
        }
    )

    fun requestTestNotification() {
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED

        if (needsPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            sendTestNotification(context) { testSent = true }
        }
    }

    if (showEditor) {
        ScheduleEditorSheet(
            title = if (activeFollowUp != null) {
                stringResource(R.string.notifications_edit_protocol)
            } else {
                stringResource(R.string.notifications_edit_defaults)
            },
            schedule = displaySchedule,
            isSaving = isSaving,
            onDismiss = { showEditor = false },
            onSave = { newSchedule ->
                isSaving = true
                scope.launch {
                    try {
                        if (activeFollowUp != null) {
                            updateFollowUpSchedule(activeFollowUp.id, newSchedule)
                            ScheduleReminderManager.scheduleForFollowUp(
                                context,
                                activeFollowUp.id,
                                activeFollowUp.title,
                                newSchedule
                            )
                            onScheduleUpdated()
                        } else {
                            ScheduleDefaults.save(context, newSchedule)
                        }
                        showEditor = false
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isSaving = false
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = { LpmTopBar(title = "Notifications", onBack = onBack) },
        containerColor = White,
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                LpmBodyText(
                    if (activeFollowUp != null) {
                        "Gentle reminders to keep building your file for ${activeFollowUp.title}. " +
                            "Edit times here or from the file menu."
                    } else {
                        "Default times apply when you prepare a new appointment file."
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PROTOCOL REMINDERS",
                        style = MaterialTheme.typography.labelMedium,
                        color = Gray400,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = { showEditor = true }) {
                        Text(stringResource(R.string.menu_edit_schedule), color = Black)
                    }
                }
            }

            if (scheduleReminders.isEmpty()) {
                item {
                    LpmCard {
                        Text(
                            text = "No schedule configured.",
                            modifier = Modifier.padding(20.dp),
                            color = Gray600
                        )
                    }
                }
            } else {
                items(scheduleReminders.size) { index ->
                    val reminder = scheduleReminders[index]
                    ReminderRow(reminder = reminder)
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                LpmSecondaryButton(
                    text = if (testSent) "Test notification sent" else "Send a test notification",
                    onClick = { requestTestNotification() },
                    enabled = !testSent
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

private fun sendTestNotification(context: android.content.Context, onSent: () -> Unit) {
    NotificationHelper.createNotificationChannel(context)
    NotificationScheduler.scheduleRoutineReminder(
        context = context,
        title = "P1 — Time for your check-in",
        message = "About 90 seconds to add today's entries to your file.",
        delayMinutes = 0
    )
    onSent()
}

@Composable
private fun ReminderRow(reminder: com.preappointment1.app.notifications.ScheduleReminderUi) {
    LpmCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Check-in at ${reminder.timeKey}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = reminder.displayTime,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Gray600
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = reminder.measures,
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray400
                )
            }
            Switch(
                checked = true,
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
