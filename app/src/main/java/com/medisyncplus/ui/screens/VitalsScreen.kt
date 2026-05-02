package com.medisyncplus.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medisyncplus.data.models.UserSettingsEntity
import com.medisyncplus.data.models.VitalEntity
import com.medisyncplus.ui.components.*
import com.medisyncplus.ui.theme.*
import com.medisyncplus.viewmodel.VitalsUiState

// ── Unit conversion helpers ───────────────────────────────────────────────────
private fun Float.kgToLbs(): Float = this * 2.20462f
private fun Float.celsiusToFahrenheit(): Float = this * 9f / 5f + 32f
private fun Float.mmolToMgdl(): Float = this * 18.0182f

/** Returns display value and unit string for weight based on user settings. */
private fun weightDisplay(valueKg: Float, imperial: Boolean): Pair<String, String> =
    if (imperial) "%.1f".format(valueKg.kgToLbs()) to "lbs"
    else "%.1f".format(valueKg) to "kg"

/** Returns display value and unit string for temperature based on user settings. */
private fun tempDisplay(valueCelsius: Float, fahrenheit: Boolean): Pair<String, String> =
    if (fahrenheit) "%.1f".format(valueCelsius.celsiusToFahrenheit()) to "°F"
    else "%.1f".format(valueCelsius) to "°C"

/** Returns display value and unit string for blood sugar based on user settings. */
private fun bloodSugarDisplay(valueMmol: Float, imperial: Boolean): Pair<String, String> =
    if (imperial) "%.0f".format(valueMmol.mmolToMgdl()) to "mg/dL"
    else "%.1f".format(valueMmol) to "mmol/L"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VitalsScreen(
    state: VitalsUiState,
    settings: UserSettingsEntity? = null,
    onRecordWeight: (Float) -> Unit,
    onRecordBp: (Int, Int) -> Unit,
    onRecordBloodSugar: (Float) -> Unit,
    onRecordPulse: (Int) -> Unit,
    onRecordSpo2: (Float) -> Unit = {},
    onRecordTemperature: (Float) -> Unit = {},
    onCriticalAlert: (String, String) -> Unit = { _, _ -> },
    onNavigate: (String) -> Unit
) {
    val isImperial = settings?.unitSystem == "IMPERIAL"
    val isFahrenheit = settings?.tempUnit == "FAHRENHEIT"

    var showLogSheet by remember { mutableStateOf(false) }
    var logType by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {

        ScreenHeader(
            title = "Monitoring",
            subtitle = "CHF Protocol · Auto-sync",
            onSettingsClick = { onNavigate("settings") }
        ) {
            Button(
                onClick = { showLogSheet = true },
                colors = ButtonDefaults.buttonColors(containerColor = Teal500),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) { 
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Log", fontWeight = FontWeight.Bold) 
            }
        }

        Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {

            // ── AI Agent analysis (Moved to Top) ─────────────────────────────
            Card(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                border = BorderStroke(1.dp, Teal500.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                var expanded by remember { mutableStateOf(false) }
                Column(Modifier.padding(16.dp).animateContentSize()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("🤖", fontSize = 18.sp)
                        Text("Condition Summary", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "Show Less" else "See Details", color = Teal500, fontSize = 12.sp)
                        }
                    }
                    val fullAnalysis = state.agentAnalysis.ifEmpty {
                        "CHF Monitoring Protocol Active. BP trend: mildly elevated. Weight stable. Blood sugar normal."
                    }
                    Text(
                        if (expanded) fullAnalysis else fullAnalysis.substringBefore(". ") + ".",
                        fontSize = 13.sp, color = SlateGrey, lineHeight = 20.sp
                    )
                }
            }

            Text("Latest Readings", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateGrey,
                letterSpacing = 0.5.sp, modifier = Modifier.padding(bottom = 8.dp))

            // ── Vitals grid ──────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // By using Modifier.height(IntrinsicSize.Min) on the Row, 
                // and Modifier.fillMaxHeight() on the children, 
                // we ensure all VitalCards in a row have the same height.
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VitalCard(
                        icon = "⚖️", label = "Weight",
                        value = state.latestWeight?.value?.let { v ->
                            if (isImperial) "%.1f".format(v.kgToLbs()) else "%.1f".format(v)
                        } ?: "--",
                        unit = if (isImperial) "lbs" else "kg", flag = state.latestWeight?.flag ?: "normal",
                        time = state.latestWeight?.recordedAt?.takeLast(8)?.take(5) ?: "",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { logType = "weight"; showLogSheet = true }
                    )
                    VitalCard(
                        icon = "🩺", label = "Blood Pressure",
                        value = state.latestBp?.let { "${it.systolic}/${it.diastolic}" } ?: "--",
                        unit = "mmHg", flag = state.latestBp?.flag ?: "normal",
                        time = state.latestBp?.recordedAt?.takeLast(8)?.take(5) ?: "",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { logType = "bp"; showLogSheet = true }
                    )
                }
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VitalCard(
                        icon = "🩸", label = "Blood Sugar",
                        value = state.latestBloodSugar?.value?.let { v ->
                            if (isImperial) "%.0f".format(v.mmolToMgdl()) else "%.1f".format(v)
                        } ?: "--",
                        unit = if (isImperial) "mg/dL" else "mmol/L", flag = state.latestBloodSugar?.flag ?: "normal",
                        time = state.latestBloodSugar?.recordedAt?.takeLast(8)?.take(5) ?: "",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { logType = "sugar"; showLogSheet = true }
                    )
                    VitalCard(
                        icon = "❤️", label = "Pulse",
                        value = state.latestPulse?.value?.toInt()?.toString() ?: "--",
                        unit = "bpm", flag = state.latestPulse?.flag ?: "normal",
                        time = state.latestPulse?.recordedAt?.takeLast(8)?.take(5) ?: "",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { logType = "pulse"; showLogSheet = true }
                    )
                }
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VitalCard(
                        icon = "🫁", label = "SpO2",
                        value = state.latestSpo2?.value?.let { "%.0f".format(it) } ?: "--",
                        unit = "%", flag = state.latestSpo2?.flag ?: "normal",
                        time = state.latestSpo2?.recordedAt?.takeLast(8)?.take(5) ?: "",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { logType = "spo2"; showLogSheet = true }
                    )
                    VitalCard(
                        icon = "🌡️", label = "Temperature",
                        value = state.latestTemperature?.value?.let { v ->
                            if (isFahrenheit) "%.1f".format(v.celsiusToFahrenheit()) else "%.1f".format(v)
                        } ?: "--",
                        unit = if (isFahrenheit) "°F" else "°C", flag = state.latestTemperature?.flag ?: "normal",
                        time = state.latestTemperature?.recordedAt?.takeLast(8)?.take(5) ?: "",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { logType = "temperature"; showLogSheet = true }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Weight trend chart ────────────────────────────────────────────
            if (state.weightHistory.isNotEmpty()) {
                SectionCard(title = "Weight — 7 Day Trend") {
                    VitalTrendChart(vitals = state.weightHistory, color = Teal500)
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── BP trend chart ────────────────────────────────────────────────
            if (state.bpHistory.isNotEmpty()) {
                SectionCard(title = "Blood Pressure — 7 Day Trend") {
                    VitalTrendChart(vitals = state.bpHistory, isBp = true, color = Blue500)
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── SpO2 trend chart ─────────────────────────────────────────────
            if (state.spo2History.isNotEmpty()) {
                SectionCard(title = "SpO2 — 7 Day Trend") {
                    VitalTrendChart(vitals = state.spo2History, color = Purple500)
                }
                Spacer(Modifier.height(12.dp))
            }

            // REMOVED FROM BOTTOM
            /*
            Card(
                Modifier.fillMaxWidth(),
                ...
            )
            */

            Spacer(Modifier.height(12.dp))

            // ── Recent log table ──────────────────────────────────────────────
            SectionCard(title = "Recent Log") {
                val allVitals = (state.weightHistory + state.bpHistory + state.bloodSugarHistory + state.spo2History)
                    .sortedByDescending { it.recordedAt }.take(10)
                if (allVitals.isEmpty()) {
                    Text("No vitals recorded yet. Tap + Log to start.", color = SlateGrey, fontSize = 13.sp)
                } else {
                    allVitals.forEachIndexed { idx, v ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(when (v.type) { "weight" -> "⚖️"; "bp" -> "🩺"; "blood_sugar" -> "🩸"; "spo2" -> "🫁"; "temperature" -> "🌡️"; else -> "❤️" }, fontSize = 16.sp)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (v.type == "bp") "${v.systolic}/${v.diastolic} ${v.unit}"
                                    else "${v.value} ${v.unit}",
                                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                                )
                                Text("${v.type.replace('_',' ')} · ${v.recordedAt.take(10)} ${v.recordedAt.takeLast(8).take(5)}",
                                    fontSize = 11.sp, color = SlateGrey)
                            }
                            Surface(
                                color = when (v.flag) { "critical" -> RedLight; "warning" -> AmberLight; else -> GreenLight },
                                shape = CircleShape
                            ) {
                                Text(
                                    v.flag.replaceFirstChar { it.uppercase() },
                                    color = when (v.flag) { "critical" -> Red500; "warning" -> Amber500; else -> Green500 },
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        if (idx < allVitals.lastIndex) HorizontalDivider(color = BorderGrey)
                    }
                }
            }
        }
    }

    // ── Log vital sheet ───────────────────────────────────────────────────────
    if (showLogSheet) {
        VitalLogSheet(
            preselectedType = logType,
            onDismiss = { showLogSheet = false; logType = "" },
            onRecordWeight = { v -> 
                onRecordWeight(v)
                showLogSheet = false 
            },
            onRecordBp = { s, d -> 
                onRecordBp(s, d)
                if (s >= 160 || d >= 100) onCriticalAlert("High Blood Pressure", "BP reading $s/$d is dangerously high. Care team notified.")
                else if (s <= 90 || d <= 60) onCriticalAlert("Low Blood Pressure", "BP reading $s/$d is lower than normal.")
                showLogSheet = false 
            },
            onRecordBloodSugar = { v -> 
                onRecordBloodSugar(v)
                if (v >= 11f) onCriticalAlert("Hyperglycemia", "Blood sugar is critically high (%.1f mmol/L).".format(v))
                else if (v < 3.9f) onCriticalAlert("Hypoglycemia", "Blood sugar is dangerously low (%.1f mmol/L).".format(v))
                showLogSheet = false 
            },
            onRecordPulse = { v -> 
                onRecordPulse(v)
                if (v >= 120 || v <= 50) onCriticalAlert("Pulse Alert", "Pulse rate $v bpm is outside normal range. Care team notified.")
                showLogSheet = false 
            },
            onRecordSpo2 = { v -> 
                onRecordSpo2(v)
                if (v < 90f) onCriticalAlert("Low Oxygen", "SpO2 reading %.0f%% is critically low.".format(v))
                showLogSheet = false
            },
            onRecordTemperature = { v -> 
                onRecordTemperature(v)
                if (v >= 39.0f || v <= 35.0f) onCriticalAlert("Temperature Alert", "Temperature %.1f°C is abnormal.".format(v))
                showLogSheet = false
            }
        )
    }
}

@Composable
fun VitalTrendChart(vitals: List<VitalEntity>, isBp: Boolean = false, color: Color = Teal500) {
    val values = if (isBp) vitals.mapNotNull { it.systolic?.toFloat() }
                 else vitals.mapNotNull { it.value }
    if (values.size < 2) {
        Text("Not enough data for trend", fontSize = 12.sp, color = SlateGrey)
        return
    }
    val minVal = values.min()
    val maxVal = values.max()
    val range  = (maxVal - minVal).coerceAtLeast(1f)

    Box(
        Modifier
            .fillMaxWidth()
            .height(110.dp)
            .drawWithContent {
                drawContent()
                val w = size.width; val h = size.height
                val padH = 20f; val padV = 16f
                val chartW = w - padH * 2; val chartH = h - padV * 2

                // Grid lines
                repeat(3) { i ->
                    val y = padV + chartH * (i / 2f)
                    drawLine(Color(0xFFE2E8F0), Offset(padH, y), Offset(w - padH, y), strokeWidth = 1f)
                }

                // Line path
                val path = Path()
                values.forEachIndexed { idx, v ->
                    val x = padH + idx.toFloat() / (values.lastIndex).coerceAtLeast(1) * chartW
                    val y = padV + chartH * (1f - (v - minVal) / range)
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = color, style = Stroke(width = 2.5f, cap = StrokeCap.Round))

                // Dots
                values.forEachIndexed { idx, v ->
                    val x = padH + idx.toFloat() / (values.lastIndex).coerceAtLeast(1) * chartW
                    val y = padV + chartH * (1f - (v - minVal) / range)
                    drawCircle(color, radius = 4f, center = Offset(x, y))
                    drawCircle(Color.White, radius = 2f, center = Offset(x, y))
                }
            }
    ) {
        // Y-axis labels
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Text("%.0f".format(maxVal), fontSize = 9.sp, color = SlateGrey)
            Text("%.0f".format((minVal + maxVal) / 2), fontSize = 9.sp, color = SlateGrey)
            Text("%.0f".format(minVal), fontSize = 9.sp, color = SlateGrey)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VitalLogSheet(
    preselectedType: String,
    onDismiss: () -> Unit,
    onRecordWeight: (Float) -> Unit,
    onRecordBp: (Int, Int) -> Unit,
    onRecordBloodSugar: (Float) -> Unit,
    onRecordPulse: (Int) -> Unit,
    onRecordSpo2: (Float) -> Unit = {},
    onRecordTemperature: (Float) -> Unit = {}
) {
    var selectedType by remember { mutableStateOf(preselectedType) }
    var weight  by remember { mutableStateOf("") }
    var sys     by remember { mutableStateOf("") }
    var dia     by remember { mutableStateOf("") }
    var sugar   by remember { mutableStateOf("") }
    var pulse   by remember { mutableStateOf("") }
    var spo2    by remember { mutableStateOf("") }
    var temp    by remember { mutableStateOf("") }
    
    var errorText by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Log Vital Sign", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(16.dp))

            // Type selector
            if (selectedType.isEmpty()) {
                Text("Select type", fontSize = 13.sp, color = SlateGrey, modifier = Modifier.padding(bottom = 10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("weight" to "⚖️ Weight", "bp" to "🩺 Blood Pressure",
                        "sugar" to "🩸 Blood Sugar", "pulse" to "❤️ Pulse Rate",
                        "spo2" to "🫁 SpO2", "temperature" to "🌡️ Temperature").chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { (type, label) ->
                                Card(
                                    onClick = { selectedType = type; errorText = null },
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = if (selectedType == type) TealLight else Color(0xFFF8FAFC)),
                                    border = BorderStroke(1.5.dp, if (selectedType == type) Teal500 else BorderGrey),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Box(Modifier.padding(16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Input fields
            when (selectedType) {
                "weight" -> OutlinedTextField(
                    value = weight, onValueChange = { weight = it; errorText = null },
                    label = { Text("Weight (kg)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    isError = errorText != null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                )
                "bp" -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = sys, onValueChange = { sys = it; errorText = null },
                        label = { Text("Systolic (mmHg)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        isError = errorText != null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                    OutlinedTextField(value = dia, onValueChange = { dia = it; errorText = null },
                        label = { Text("Diastolic (mmHg)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        isError = errorText != null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                }
                "sugar" -> OutlinedTextField(
                    value = sugar, onValueChange = { sugar = it; errorText = null },
                    label = { Text("Blood Sugar (mmol/L)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    isError = errorText != null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                )
                "pulse" -> OutlinedTextField(
                    value = pulse, onValueChange = { pulse = it; errorText = null },
                    label = { Text("Pulse Rate (bpm)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    isError = errorText != null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                "spo2" -> OutlinedTextField(
                    value = spo2, onValueChange = { spo2 = it; errorText = null },
                    label = { Text("SpO2 (%)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    isError = errorText != null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                )
                "temperature" -> OutlinedTextField(
                    value = temp, onValueChange = { temp = it; errorText = null },
                    label = { Text("Temperature (°C)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    isError = errorText != null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                )
            }

            if (selectedType.isNotEmpty()) {
                errorText?.let {
                    Text(it, color = Red500, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                }
                
                Text("Will be synced to hospital EMR automatically.",
                    fontSize = 11.sp, color = Teal500, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
                Button(
                    onClick = {
                        val w = weight.toFloatOrNull()
                        val s = sys.toIntOrNull()
                        val d = dia.toIntOrNull()
                        val sug = sugar.toFloatOrNull()
                        val p = pulse.toIntOrNull()
                        val sp = spo2.toFloatOrNull()
                        val t = temp.toFloatOrNull()

                        var hasError = false
                        when (selectedType) {
                            "weight" -> {
                                if (w == null) { errorText = "Please enter weight"; hasError = true }
                                else if (w < 30f || w > 300f) { errorText = "Please enter a reasonable weight (30-300kg)"; hasError = true }
                            }
                            "bp" -> {
                                if (s == null || d == null) { errorText = "Please enter BP"; hasError = true }
                                else if (s < 50 || s > 250 || d < 30 || d > 150) { errorText = "Please enter a reasonable BP"; hasError = true }
                            }
                            "sugar" -> {
                                if (sug == null) { errorText = "Please enter sugar level"; hasError = true }
                                else if (sug < 1f || sug > 50f) { errorText = "Please enter a reasonable sugar level (1-50)"; hasError = true }
                            }
                            "pulse" -> {
                                if (p == null) { errorText = "Please enter pulse"; hasError = true }
                                else if (p < 30 || p > 250) { errorText = "Please enter a reasonable pulse (30-250)"; hasError = true }
                            }
                            "spo2" -> {
                                if (sp == null) { errorText = "Please enter SpO2"; hasError = true }
                                else if (sp < 50f || sp > 100f) { errorText = "Please enter a reasonable SpO2 (50-100%)"; hasError = true }
                            }
                            "temperature" -> {
                                if (t == null) { errorText = "Please enter temperature"; hasError = true }
                                else if (t < 30f || t > 45f) { errorText = "Please enter a reasonable temperature (30-45°C)"; hasError = true }
                            }
                        }

                        if (!hasError) {
                            when (selectedType) {
                                "weight" -> w?.let { onRecordWeight(it) }
                                "bp"     -> if (s != null && d != null) onRecordBp(s, d)
                                "sugar"  -> sug?.let { onRecordBloodSugar(it) }
                                "pulse"  -> p?.let { onRecordPulse(it) }
                                "spo2"   -> sp?.let { onRecordSpo2(it) }
                                "temperature" -> t?.let { onRecordTemperature(it) }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal500),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Save & Sync to Hospital", fontWeight = FontWeight.SemiBold) }
            }

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            Spacer(Modifier.height(8.dp))
        }
    }
}
