package com.preappointment1.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import kotlinx.coroutines.launch
import com.preappointment1.app.ui.components.LpmCard
import com.preappointment1.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var watchConnected by remember { mutableStateOf(false) }
    var thermometerConnected by remember { mutableStateOf(true) }
    var bpConnected by remember { mutableStateOf(false) }
    var scaleConnected by remember { mutableStateOf(false) }
    var userName by remember { mutableStateOf(com.preappointment1.app.data.SessionManager.getUserName() ?: "") }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = White,
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                // Header Profile
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Gray50)
                        .padding(top = 48.dp, bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (userName.isNotBlank()) userName.take(1).uppercase() else "?",
                            color = White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = userName,
                        onValueChange = { userName = it },
                        placeholder = { Text("Enter your full name", color = Gray400) },
                        modifier = Modifier.fillMaxWidth(0.8f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            com.preappointment1.app.data.SessionManager.saveUserName(userName)
                            focusManager.clearFocus()
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Black,
                            unfocusedBorderColor = Gray200,
                            cursorColor = Black
                        )
                    )
                }
            }

            item {
                SectionTitle("CONNECTED DEVICES")
                DeviceRow(
                    name = "Smartwatch",
                    desc = "Steps, heart rate, sleep",
                    checked = watchConnected,
                    onCheckedChange = { watchConnected = it }
                )
                DeviceRow(
                    name = "Digital thermometer",
                    desc = "Manual temperature entries",
                    checked = thermometerConnected,
                    onCheckedChange = { thermometerConnected = it }
                )
                DeviceRow(
                    name = "Blood pressure monitor",
                    desc = "Bluetooth BP device",
                    checked = bpConnected,
                    onCheckedChange = { bpConnected = it }
                )
                DeviceRow(
                    name = "Connected scale",
                    desc = "Weight tracking",
                    checked = scaleConnected,
                    onCheckedChange = { scaleConnected = it }
                )
            }

            item {
                SectionTitle("PREFERENCES")
                MenuRow(label = "Temperature unit", value = "°C")
                MenuRow(label = "Reminder times", value = "9am, 7pm")
            }

            item {
                SectionTitle("ACCOUNT")
                val uriHandler = LocalUriHandler.current
                MenuRow(label = "Terms of Use & Privacy Policy", onClick = {
                    uriHandler.openUri("https://p1-privacy-policy.pages.dev/")
                })
                MenuRow(label = "Export my data")
                MenuRow(label = "Sign out", isDestructive = false, onClick = {
                    com.preappointment1.app.data.SessionManager.clearSession()
                    onLogout()
                })
                MenuRow(label = "Delete account and all data", isDestructive = true, onClick = {
                    scope.launch {
                        try {
                            com.preappointment1.app.data.api.ApiClient.apiService.deleteAccount()
                        } catch (e: Exception) {}
                        com.preappointment1.app.data.SessionManager.clearSession()
                        onLogout()
                    }
                })
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = Gray400,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun DeviceRow(
    name: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(text = desc, color = Gray500, fontSize = 13.sp)
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

@Composable
private fun MenuRow(
    label: String,
    value: String? = null,
    isDestructive: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            color = if (isDestructive) Color(0xFFEF4444) else Black,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Gray200)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = value,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Gray600
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "›", fontSize = 20.sp, color = Gray400)
    }
}
