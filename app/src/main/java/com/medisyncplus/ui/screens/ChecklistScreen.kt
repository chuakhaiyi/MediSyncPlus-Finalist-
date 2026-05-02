package com.medisyncplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.medisyncplus.data.models.ChecklistTaskEntity
import com.medisyncplus.ui.components.*
import com.medisyncplus.ui.theme.*
import com.medisyncplus.viewmodel.ChecklistUiState
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

// ── Vital input dialog state ──────────────────────────────────────────────────
data class VitalInputRequest(val taskId: String, val vitalType: String, val taskDesc: String)

// ── Vital Input Dialog ────────────────────────────────────────────────────────
@Composable
fun VitalInputDialog(
    vitalType: String,
    taskDesc: String,
    onSave: (weight: Float?, systolic: Int?, diastolic: Int?, bloodSugar: Float?, pulse: Float?, spo2: Float?, temperature: Float?) -> Unit,
    onDismiss: () -> Unit
) {
    var weightInput by remember { mutableStateOf("") }
    var systolicInput by remember { mutableStateOf("") }
    var diastolicInput by remember { mutableStateOf("") }
    var bloodSugarInput by remember { mutableStateOf("") }
    var pulseInput by remember { mutableStateOf("") }
    var spo2Input by remember { mutableStateOf("") }
    var temperatureInput by remember { mutableStateOf("") }

    val title = when (vitalType) {
        "weight"      -> "Record Weight"
        "bp"          -> "Record Blood Pressure"
        "blood_sugar" -> "Record Blood Sugar"
        "pulse"       -> "Record Pulse"
        "spo2"        -> "Record SpO2"
        "temperature" -> "Record Temperature"
        else          -> "Record Vital"
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(taskDesc, fontSize = 13.sp, color = SlateGrey)

                when (vitalType) {
                    "weight" -> OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        label = { Text("Weight (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    "bp" -> {
                        OutlinedTextField(
                            value = systolicInput,
                            onValueChange = { systolicInput = it },
                            label = { Text("Systolic (mmHg)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = diastolicInput,
                            onValueChange = { diastolicInput = it },
                            label = { Text("Diastolic (mmHg)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    "blood_sugar" -> OutlinedTextField(
                        value = bloodSugarInput,
                        onValueChange = { bloodSugarInput = it },
                        label = { Text("Blood Sugar (mmol/L)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    "pulse" -> OutlinedTextField(
                        value = pulseInput,
                        onValueChange = { pulseInput = it },
                        label = { Text("Pulse (bpm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    "spo2" -> OutlinedTextField(
                        value = spo2Input,
                        onValueChange = { spo2Input = it },
                        label = { Text("SpO2 (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    "temperature" -> OutlinedTextField(
                        value = temperatureInput,
                        onValueChange = { temperatureInput = it },
                        label = { Text("Temperature (°C)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            onSave(
                                weightInput.toFloatOrNull(),
                                systolicInput.toIntOrNull(),
                                diastolicInput.toIntOrNull(),
                                bloodSugarInput.toFloatOrNull(),
                                pulseInput.toFloatOrNull(),
                                spo2Input.toFloatOrNull(),
                                temperatureInput.toFloatOrNull()
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Teal500)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun ChecklistScreen(
    state: ChecklistUiState,
    onToggleTask: (String, Boolean) -> Unit,
    onRecordWeight: (Float) -> Unit,
    onRecordBp: (Int, Int) -> Unit,
    onRecordBloodSugar: (Float) -> Unit,
    onRecordPulse: (Float) -> Unit = {},        // Fixed: was (Int), ViewModel expects Float
    onRecordSpo2: (Float) -> Unit = {},
    onRecordTemperature: (Float) -> Unit = {},
    onCriticalAlert: (String, String) -> Unit = { _, _ -> },
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val tasks = state.tasks
    val done  = state.completedCount
    val total = tasks.size
    val pct   = if (total > 0) done.toFloat() / total else 0f

    var vitalRequest by remember { mutableStateOf<VitalInputRequest?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp).background(MaterialTheme.colorScheme.background)) {

        ScreenHeader(
            title = "Checklist",
            onSettingsClick = { onNavigate("settings") }
        )

        Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {

            // ── Progress bar ──────────────────────────────────────────────────
            LinearProgressIndicator(
                progress = { pct },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                color = if (pct >= 1f) Green500 else Teal500,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
            Text("$done of $total tasks completed today",
                fontSize = 13.sp, color = SlateGrey, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))

            if (pct >= 1f) {
                AlertBanner("🎉 All tasks completed today! Great job.",
                    AlertType.SUCCESS, modifier = Modifier.padding(bottom = 12.dp))
            }

            // ── Tasks by time of day ──────────────────────────────────────────
            listOf("MORNING" to "🌅 Morning", "AFTERNOON" to "☀️ Afternoon", "EVENING" to "🌙 Evening").forEach { (period, label) ->
                val periodTasks = tasks.filter { it.timeOfDay == period }
                if (periodTasks.isEmpty()) return@forEach

                Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Teal500,
                    letterSpacing = 0.5.sp, modifier = Modifier.padding(bottom = 8.dp))

                Card(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.padding(4.dp)) {
                        periodTasks.forEachIndexed { idx, task ->
                            ChecklistTaskRow(
                                task = task,
                                onToggle = {
                                    if (task.isDone) {
                                        onToggleTask(task.id, false)
                                    } else {
                                        val now = LocalTime.now()
                                        val scheduledTime = try {
                                            LocalTime.parse(task.scheduledTime, DateTimeFormatter.ofPattern("HH:mm"))
                                        } catch (e: Exception) { LocalTime.MIN }

                                        if (now.isBefore(scheduledTime)) {
                                            Toast.makeText(context, "It's too early for this task. Scheduled for ${task.scheduledTime}", Toast.LENGTH_SHORT).show()
                                        } else {
                                            if (task.requiresVitalInput && task.vitalType != null) {
                                                vitalRequest = VitalInputRequest(task.id, task.vitalType, task.description)
                                            } else {
                                                onToggleTask(task.id, true)
                                            }
                                        }
                                    }
                                }
                            )
                            if (idx < periodTasks.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Vital Input Dialog ────────────────────────────────────────────────────
    vitalRequest?.let { req ->
        VitalInputDialog(
            vitalType = req.vitalType,
            taskDesc = req.taskDesc,
            onSave = { w, sys, dia, sugar, pulse, spo2, temp ->
                when (req.vitalType) {
                    "weight" -> w?.let { onRecordWeight(it) }
                    "bp" -> if (sys != null && dia != null) {
                        onRecordBp(sys, dia)
                        if (sys >= 160 || dia >= 100) onCriticalAlert("High Blood Pressure", "BP reading $sys/$dia is dangerously high. Care team notified.")
                    }
                    "blood_sugar" -> sugar?.let {
                        onRecordBloodSugar(it)
                        if (it >= 11f) onCriticalAlert("Hyperglycemia", "Blood sugar is critically high (%.1f mmol/L).".format(it))
                        else if (it < 3.9f) onCriticalAlert("Hypoglycemia", "Blood sugar is dangerously low (%.1f mmol/L).".format(it))
                    }
                    "pulse" -> pulse?.let {
                        onRecordPulse(it)                          // now Float, matches ViewModel
                        if (it >= 120f || it <= 50f) onCriticalAlert("Pulse Alert", "Pulse rate ${it.toInt()} bpm is outside normal range.")
                    }
                    "spo2" -> spo2?.let {
                        onRecordSpo2(it)
                        if (it < 90f) onCriticalAlert("Low Oxygen", "SpO2 reading %.0f%% is critically low.".format(it))
                    }
                    "temperature" -> temp?.let {
                        onRecordTemperature(it)
                        if (it >= 39.0f || it <= 35.0f) onCriticalAlert("Temperature Alert", "Temperature %.1f°C is abnormal.".format(it))
                    }
                }
                onToggleTask(req.taskId, true)
                vitalRequest = null
            },
            onDismiss = { vitalRequest = null }
        )
    }
}

@Composable
fun ChecklistTaskRow(task: ChecklistTaskEntity, onToggle: () -> Unit) {
    val now = LocalTime.now()
    val scheduled = try {
        LocalTime.parse(task.scheduledTime, DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) { LocalTime.MIN }

    val isTooEarly = !task.isDone && now.isBefore(scheduled)
    val isOverdue  = !task.isDone && now.isAfter(scheduled)
    val isSoon     = !task.isDone && !now.isAfter(scheduled) && now.isAfter(scheduled.minusHours(1))

    val isDark = isSystemInDarkTheme()

    val bgColor = when {
        task.isDone -> GreenLight.copy(alpha = if (isDark) 0.15f else 1f)
        isTooEarly  -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        isOverdue   -> RedLight.copy(alpha = if (isDark) 0.1f else 0.15f)
        isSoon      -> AmberLight.copy(alpha = if (isDark) 0.15f else 0.4f)
        else        -> Color.Transparent
    }

    val borderColor = when {
        task.isDone -> Green500
        isTooEarly  -> Color.Transparent
        isOverdue   -> Red500.copy(alpha = 0.3f)
        isSoon      -> Amber500.copy(alpha = 0.5f)
        else        -> Color.Transparent
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(enabled = !isTooEarly || task.isDone) { onToggle() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (task.isDone) Teal500 else Color.Transparent)
                .border(
                    2.dp,
                    if (task.isDone) Teal500
                    else if (isTooEarly) SlateGrey.copy(alpha = 0.3f)
                    else if (isOverdue) Red500.copy(alpha = 0.4f)
                    else if (isSoon) Amber500.copy(alpha = 0.5f)
                    else BorderGrey,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (task.isDone) {
                Icon(Icons.Default.Check, contentDescription = null,
                    tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }

        Text(
            when (task.iconName) {
                "scale"      -> "⚖️"
                "medication" -> "💊"
                "walk"       -> "🚶"
                "bp"         -> "🩺"
                "check"      -> "👀"
                "report"     -> "📋"
                else         -> "✅"
            },
            fontSize = 20.sp,
            modifier = Modifier.alpha(if (isTooEarly && !task.isDone) 0.5f else 1f)
        )

        Column(Modifier.weight(1f)) {
            Text(
                task.description,
                fontSize = 14.sp,
                fontWeight = if (task.isDone) FontWeight.Normal else FontWeight.Medium,
                color = when {
                    task.isDone -> SlateGrey
                    isTooEarly  -> SlateGrey.copy(alpha = 0.6f)
                    isOverdue   -> Red500
                    else        -> MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None
            )
            if (!task.isDone) {
                Text(
                    when {
                        isTooEarly -> "Available at ${task.scheduledTime}"
                        isOverdue  -> "Overdue (Scheduled: ${task.scheduledTime})"
                        else       -> "Scheduled: ${task.scheduledTime}"
                    },
                    fontSize = 11.sp,
                    color = if (isOverdue) Red500 else SlateGrey
                )
            }
        }

        if (task.isDone) {
            Text("✅", fontSize = 18.sp)
        } else if (isTooEarly) {
            Icon(Icons.Default.Lock, contentDescription = null,
                tint = SlateGrey.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
        }
    }
}