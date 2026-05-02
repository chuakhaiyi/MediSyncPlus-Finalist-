package com.medisyncplus.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.medisyncplus.data.models.UserSettingsEntity
import com.medisyncplus.data.models.HospitalInfoEntity
import com.medisyncplus.data.models.PatientEntity
import com.medisyncplus.viewmodel.HospitalInfoUiState
import com.medisyncplus.ui.components.ScreenHeader
import com.medisyncplus.ui.theme.*
import com.medisyncplus.viewmodel.SettingsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    patient: PatientEntity?,
    hospitalInfoState: HospitalInfoUiState = HospitalInfoUiState(),
    onUpdateSettings: (UserSettingsEntity) -> Unit,
    onUpdateHospitalInfo: (HospitalInfoEntity) -> Unit = {},
    onUpdateEmergencyContact: (String, String) -> Unit = { _, _ -> },
    onLogout: () -> Unit,
    onTestAi: () -> Unit = {}
) {
    val patientId = patient?.id ?: ""
    val currentSettings = state.settings ?: UserSettingsEntity(patientId)

    val info = hospitalInfoState.hospitalInfo
    var editWardName    by remember(info) { androidx.compose.runtime.mutableStateOf(info?.wardName ?: "") }
    var editWardPhone   by remember(info) { androidx.compose.runtime.mutableStateOf(info?.wardPhone ?: "") }
    var editHospitalName by remember(info) { androidx.compose.runtime.mutableStateOf(info?.hospitalName ?: "") }

    var editEmergencyName by remember(patient) { mutableStateOf(patient?.emergencyContactName ?: "David Chen (Son)") }
    var editEmergencyPhone by remember(patient) { mutableStateOf(patient?.emergencyContact ?: "012-987-6543") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        ScreenHeader(title = "Settings", subtitle = "Customise your experience")

        Column(Modifier.padding(16.dp)) {
            
            Text("Notifications", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Teal500)
            Spacer(Modifier.height(8.dp))
            
            Text("Notify me before events:", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = currentSettings.notificationMinutesBefore.toFloat(),
                    onValueChange = { onUpdateSettings(currentSettings.copy(notificationMinutesBefore = it.toInt())) },
                    valueRange = 5f..60f,
                    steps = 10,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = Teal500, activeTrackColor = Teal500)
                )
                Text("${currentSettings.notificationMinutesBefore} min", modifier = Modifier.padding(start = 12.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            }
            Text("Setting a default reminder time (5-60 min) helps you stay on track with medications and appointments.", fontSize = 12.sp, color = SlateGrey)

            Spacer(Modifier.height(24.dp))
            Text("Unit Systems", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Teal500)
            Spacer(Modifier.height(8.dp))
            
            SettingsToggleRow(
                label = "Temperature Unit",
                value = currentSettings.tempUnit,
                options = listOf("CELSIUS", "FAHRENHEIT"),
                onSelect = { onUpdateSettings(currentSettings.copy(tempUnit = it)) }
            )

            Spacer(Modifier.height(12.dp))

            SettingsToggleRow(
                label = "Weight & Height",
                value = currentSettings.unitSystem,
                options = listOf("METRIC", "IMPERIAL"),
                onSelect = { onUpdateSettings(currentSettings.copy(unitSystem = it)) }
            )

            Spacer(Modifier.height(24.dp))
            Text("Theme", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Teal500)
            Spacer(Modifier.height(8.dp))

            SettingsToggleRow(
                label = "Visual Style",
                value = currentSettings.theme,
                options = listOf("LIGHT", "DARK"),
                onSelect = { onUpdateSettings(currentSettings.copy(theme = it)) }
            )

            Spacer(Modifier.height(32.dp))
            
            // ── Hospital Info Section ──────────────────
            Text("Hospital Information", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Teal500)
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Assigned Ward & Contact", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = SlateGrey)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editHospitalName,
                        onValueChange = { editHospitalName = it },
                        label = { Text("Hospital Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editWardName,
                        onValueChange = { editWardName = it },
                        label = { Text("Ward Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editWardPhone,
                        onValueChange = { editWardPhone = it },
                        label = { Text("Ward Phone Number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val current = info ?: HospitalInfoEntity("H001", editHospitalName, editWardName, editWardPhone)
                            onUpdateHospitalInfo(
                                current.copy(
                                    hospitalName = editHospitalName,
                                    wardName     = editWardName,
                                    wardPhone    = editWardPhone
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Teal500),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save Hospital Info", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Emergency Contact Section
            Text("Emergency Contact", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Teal500)
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Primary Emergency Contact", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = SlateGrey)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editEmergencyName,
                        onValueChange = { editEmergencyName = it },
                        label = { Text("Contact Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editEmergencyPhone,
                        onValueChange = { editEmergencyPhone = it },
                        label = { Text("Phone Number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            onUpdateEmergencyContact(editEmergencyName, editEmergencyPhone)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Teal500),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Update Emergency Contact", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("This contact is alerted automatically by the AI agent if an SOS is triggered or critical symptoms are detected.", fontSize = 11.sp, color = SlateGrey)
                }
            }

            Spacer(Modifier.height(32.dp))
            Text("AI Diagnostic", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Teal500)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onTestAi,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Teal500))
            ) {
                Icon(Icons.Default.CloudSync, contentDescription = null, tint = Teal500)
                Spacer(Modifier.width(8.dp))
                Text("Test AI Connection", color = Teal500)
            }

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedLight, contentColor = Red500),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Logout", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "App Version 2.0.0",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                color = SlateGrey
            )
        }
    }
}

@Composable
fun SettingsToggleRow(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
        Row {
            options.forEach { option ->
                val isSelected = value == option
                Surface(
                    onClick = { onSelect(option) },
                    modifier = Modifier.padding(start = 4.dp),
                    color = if (isSelected) Teal500 else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        option.lowercase().replaceFirstChar { it.uppercase() },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
