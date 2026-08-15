package com.preappointment1.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.preappointment1.app.R
import com.preappointment1.app.schedule.ScheduleLogic
import com.preappointment1.app.schedule.ScheduleSlot
import com.preappointment1.app.ui.theme.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorSheet(
    title: String,
    schedule: Map<String, List<String>>,
    onDismiss: () -> Unit,
    onSave: (Map<String, List<String>>) -> Unit,
    isSaving: Boolean = false
) {
    var editedSchedule by remember(schedule) { mutableStateOf(schedule) }
    var editingSlot by remember { mutableStateOf<ScheduleSlot?>(null) }

    val slots = remember(editedSchedule) { ScheduleLogic.parseScheduleSlots(editedSchedule) }

    if (editingSlot != null) {
        val slot = editingSlot!!
        val pickerState = rememberTimePickerState(
            initialHour = slot.time.hour,
            initialMinute = slot.time.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { editingSlot = null },
            title = { Text(stringResource(R.string.schedule_editor_change_time, slot.timeKey), fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newTime = LocalTime.of(pickerState.hour, pickerState.minute)
                        editedSchedule = ScheduleLogic.replaceSlotTime(editedSchedule, slot.timeKey, newTime)
                        editingSlot = null
                    }
                ) { Text(stringResource(R.string.action_ok), color = SagePrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { editingSlot = null }) { Text(stringResource(R.string.action_cancel), color = TextSecondary) }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            LpmBodyText(stringResource(R.string.schedule_editor_subtitle))
            Spacer(modifier = Modifier.height(18.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 360.dp)
            ) {
                items(slots, key = { it.timeKey }) { slot ->
                    LpmCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    slot.timeKey,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    slot.actions.joinToString(", "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            TextButton(onClick = { editingSlot = slot }) {
                                Text(stringResource(R.string.schedule_editor_edit_time), color = SagePrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            LpmPrimaryButton(
                text = stringResource(R.string.schedule_editor_save),
                loading = isSaving,
                onClick = { onSave(editedSchedule) }
            )
        }
    }
}

fun formatSlotMeasures(actions: List<String>): String {
    return actions.joinToString(", ") { action ->
        when (action.lowercase()) {
            "pain" -> "pain level"
            "temperature" -> "temperature"
            "photo" -> "photo"
            else -> action
        }
    }
}

fun formatDisplayTime(timeKey: String): String {
    return runCatching {
        val time = LocalTime.parse(timeKey)
        time.format(DateTimeFormatter.ofPattern("h:mm a"))
    }.getOrDefault(timeKey)
}
