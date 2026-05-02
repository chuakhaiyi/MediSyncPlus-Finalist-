package com.medisyncplus.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medisyncplus.ai.SymptomAnalysisResult
import com.medisyncplus.data.models.*
import com.medisyncplus.ui.components.*
import com.medisyncplus.ui.theme.*
import com.medisyncplus.viewmodel.*
import com.medisyncplus.workers.scheduleCustomReminder
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// SYMPTOM SCREEN
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

private val symptomList = listOf(
    "Chest pain", "Shortness of breath", "Ankle swelling", "Palpitations",
    "Dizziness", "Nausea", "Fever", "Fatigue",
    "Loss of appetite", "Cough", "Blurred vision", "Headache",
    "Rapid weight gain", "Reduced urine output"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SymptomScreen(
    state: SymptomUiState,
    onSubmit: (List<String>, Int, String) -> Unit,
    onNavigate: (String) -> Unit
) {
    var selectedSymptoms by remember { mutableStateOf<Set<String>>(emptySet()) }
    var severity         by remember { mutableIntStateOf(2) }
    var note             by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {

        ScreenHeader(
            title = "Symptom Report",
            subtitle = "AI analyses instantly",
            onSettingsClick = { onNavigate("settings") }
        )

        Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {

            Text("Select any symptoms you are experiencing. The AI agent will analyse them and alert your care team if needed.",
                fontSize = 14.sp, color = SlateGrey, lineHeight = 20.sp, modifier = Modifier.padding(bottom = 16.dp))

            // ── Symptom chips ─────────────────────────────────────────────────
            SectionCard(title = "Common Symptoms") {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    symptomList.forEach { symptom ->
                        val selected = symptom in selectedSymptoms
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedSymptoms = if (selected) selectedSymptoms - symptom
                                else selectedSymptoms + symptom
                            },
                            label = { Text(symptom, fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TealLight,
                                selectedLabelColor = Teal700
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Severity selector ─────────────────────────────────────────────
            SectionCard(title = "Severity") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to Triple("Mild", GreenLight, Green500),
                           2 to Triple("Moderate", AmberLight, Amber500),
                           3 to Triple("Severe", RedLight, Red500)).forEach { (lvl, cfg) ->
                        val (label, bg, fg) = cfg
                        Surface(
                            onClick = { severity = lvl },
                            modifier = Modifier.weight(1f),
                            color = if (severity == lvl) bg else Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(if (severity == lvl) 2.dp else 1.dp,
                                if (severity == lvl) fg else BorderGrey)
                        ) {
                            Text(label, fontWeight = FontWeight.SemiBold, color = if (severity == lvl) fg else SlateGrey,
                                fontSize = 13.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(12.dp).fillMaxWidth())
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Additional note ───────────────────────────────────────────────
            SectionCard(title = "Additional Notes") {
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    placeholder = { Text("Describe how you feel in your own words...", color = SlateGrey) },
                    modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 5
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Analysis result ───────────────────────────────────────────────
            AnimatedVisibility(visible = state.isAnalysing) {
                AgentThinkingCard()
                Spacer(Modifier.height(12.dp))
            }

            state.analysisResult?.let { result ->
                SymptomResultCard(result)
                Spacer(Modifier.height(12.dp))
            }

            // ── Submit button ─────────────────────────────────────────────────
            Button(
                onClick = {
                    onSubmit(selectedSymptoms.toList(), severity, note)
                    note = ""
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = (selectedSymptoms.isNotEmpty() || note.isNotBlank()) && !state.isAnalysing,
                colors = ButtonDefaults.buttonColors(containerColor = Teal500),
                shape = RoundedCornerShape(12.dp)
            ) { Text("🤖 Send Report to Care Team", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }

            Spacer(Modifier.height(20.dp))

            // ── Recent reports ────────────────────────────────────────────────
            if (state.recentReports.isNotEmpty()) {
                SectionCard(title = "Recent Reports") {
                    state.recentReports.take(5).forEachIndexed { idx, r ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    try { com.google.gson.Gson().fromJson(r.symptoms, Array<String>::class.java).joinToString(", ") }
                                    catch (e: Exception) { r.symptoms },
                                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                                )
                                Text("${r.reportedAt.take(10)} · ${r.agentRecommendedAction}",
                                    fontSize = 11.sp, color = SlateGrey, modifier = Modifier.padding(top = 2.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            RiskBadge(r.agentRiskLevel)
                        }
                        if (idx < (state.recentReports.size - 1).coerceAtMost(4))
                            HorizontalDivider(color = BorderGrey)
                    }
                }
            }
        }
    }
}

@Composable
fun SymptomResultCard(result: SymptomAnalysisResult) {
    val (bg, border, icon) = when (result.riskLevel) {
        "CRITICAL" -> Triple(RedLight, Red500, "🚨")
        "WARNING"  -> Triple(AmberLight, Amber500, "⚠️")
        else       -> Triple(GreenLight, Green500, "✅")
    }
    var expanded by remember { mutableStateOf(false) }
    
    Surface(color = bg, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, border),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp).animateContentSize()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 20.sp)
                Text("Agent Analysis: ${result.riskLevel}", fontWeight = FontWeight.Bold, color = border, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = border)
                }
            }
            
            Text(
                if (expanded) result.riskReason else result.riskReason.substringBefore(". ") + ".",
                fontSize = 13.sp, color = border, lineHeight = 19.sp
            )
            
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text("Action: ${result.recommendedAction}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = border)
                if (result.bookAppointment) {
                    Text("📅 Appointment request sent to care team", fontSize = 12.sp, color = border, modifier = Modifier.padding(top = 4.dp))
                }
                Text("✓ Recorded to hospital database · Care team notified",
                    fontSize = 11.sp, color = border.copy(alpha = 0.7f), modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// APPOINTMENT SCREEN
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentScreen(
    state: AppointmentUiState,
    onRequestAppointment: (String, String, String, String) -> Unit,
    onMarkReminderRead: (Long) -> Unit,
    onReschedule: (String, String) -> Unit = { _, _ -> },
    onCancel: (String) -> Unit = { },
    onNavigate: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showRequestSheet by remember { mutableStateOf(false) }
    var selectedAppt by remember { mutableStateOf<AppointmentEntity?>(null) }
    val nextAppt = state.upcoming.firstOrNull()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {

        ScreenHeader(
            title = "Follow-up",
            onSettingsClick = { onNavigate("settings") }
        ) {
            Button(
                onClick = { showRequestSheet = true },
                colors = ButtonDefaults.buttonColors(containerColor = Teal500),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Request", fontWeight = FontWeight.Bold)
            }
        }

        Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {

            // ── Next appointment hero card ─────────────────────────────────────
            nextAppt?.let { appt ->
                Card(
                    onClick = { selectedAppt = appt },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(2.dp, Teal500),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                            Column {
                                Text(appt.doctorName, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Text(appt.specialty, color = SlateGrey, fontSize = 14.sp)
                            }
                            StatusBadge(
                                label = if (appt.status == "CONFIRMED") "✓ Confirmed" else appt.status,
                                color = when (appt.status) { 
                                    "CONFIRMED" -> Green500; "PENDING" -> Amber500; "COMPLETED" -> SlateGrey; 
                                    "CANCELLED" -> Red500; else -> Blue500 
                                },
                                bgColor = when (appt.status) { 
                                    "CONFIRMED" -> GreenLight; "PENDING" -> AmberLight; "COMPLETED" -> BorderGrey; 
                                    "CANCELLED" -> RedLight; else -> BlueLight 
                                }
                            )
                        }
                        
                        HorizontalDivider(color = BorderGrey, modifier = Modifier.padding(vertical = 12.dp))
                        
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoRow("📅", "Date & Time", appt.dateTime)
                            InfoRow("📍", "Location", appt.location.ifEmpty { "Main Clinic" })
                            if (appt.notes.isNotEmpty()) InfoRow("📝", "Notes", appt.notes)
                        }

                        // Cancellation option for Pending or Scheduled
                        if (appt.status == "PENDING" || appt.status == "SCHEDULED") {
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = { onCancel(appt.id) },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Red500),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Cancel Request", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White) 
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // ── All appointments ──────────────────────────────────────────────
            Text("All Appointments", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A),
                modifier = Modifier.padding(bottom = 12.dp))
            
            if (state.all.isEmpty()) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderGrey), shape = RoundedCornerShape(14.dp)) {
                    Text("No appointments history found.", modifier = Modifier.padding(16.dp), fontSize = 13.sp, color = SlateGrey)
                }
            } else {
                state.all.forEach { appt ->
                    Card(
                        onClick = { selectedAppt = appt },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, BorderGrey),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(appt.doctorName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${appt.specialty} · ${appt.appointmentType}", fontSize = 12.sp, color = SlateGrey)
                                Text("📅 ${appt.dateTime}", fontSize = 12.sp, color = Blue500, modifier = Modifier.padding(top = 3.dp))
                            }
                            StatusBadge(
                                appt.status,
                                when (appt.status) { 
                                    "CONFIRMED" -> Green500; "PENDING" -> Amber500; "COMPLETED" -> SlateGrey; 
                                    "CANCELLED" -> Red500; else -> Blue500 
                                },
                                when (appt.status) { 
                                    "CONFIRMED" -> GreenLight; "PENDING" -> AmberLight; "COMPLETED" -> BorderGrey; 
                                    "CANCELLED" -> RedLight; else -> BlueLight 
                                }
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))

            // ── Hospital history ──────────────────────────────────────────────
            if (state.hospitalStays.isNotEmpty()) {
                SectionCard(title = "Hospital Stay History") {
                    state.hospitalStays.forEachIndexed { idx, stay ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Blue500))
                            Column(Modifier.padding(bottom = if (idx < state.hospitalStays.lastIndex) 14.dp else 0.dp)) {
                                Text("${stay.admissionDate} → ${stay.dischargeDate ?: "Active"}",
                                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(stay.primaryDiagnosis, fontSize = 12.sp, color = SlateGrey, modifier = Modifier.padding(top = 2.dp))
                                Text("${stay.ward} · Dr. Oscar", fontSize = 11.sp, color = SlateGrey)
                                Text("Rx: ${stay.treatmentSummary.take(70)}…", fontSize = 11.sp, color = Teal500, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                        if (idx < state.hospitalStays.lastIndex) HorizontalDivider(color = BorderGrey, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── Care team messages ────────────────────────────────────────────
            if (state.careTeamReminders.isNotEmpty()) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = PurpleLight),
                    border = BorderStroke(1.dp, Purple500.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 12.dp)) {
                            Text("👩‍⚕️", fontSize = 18.sp)
                            Text("Care Team Messages", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                        state.careTeamReminders.forEachIndexed { idx, reminder ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.clickable { onMarkReminderRead(reminder.id) }) {
                                Box(
                                    Modifier.size(34.dp).clip(CircleShape).background(Purple500),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(reminder.fromName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""),
                                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Column(Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(reminder.fromName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        if (!reminder.isRead) {
                                            Box(Modifier.size(7.dp).clip(CircleShape).background(Red500))
                                        }
                                    }
                                    Text("${reminder.role} · ${reminder.createdAt.take(10)}", fontSize = 11.sp, color = SlateGrey)
                                    Text(reminder.message, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                            if (idx < state.careTeamReminders.lastIndex)
                                HorizontalDivider(color = Purple500.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 10.dp))
                        }
                    }
                }
            }
        }
    }

    // ── Appointment detail sheet ──────────────────────────────────────────────
    if (selectedAppt != null) {
        val appt = selectedAppt!!
        ModalBottomSheet(onDismissRequest = { selectedAppt = null }) {
            Column(Modifier.padding(20.dp).navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Appointment Detail", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    StatusBadge(
                        label = appt.status,
                        color = when (appt.status) { 
                            "CONFIRMED" -> Green500; "PENDING" -> Amber500; "COMPLETED" -> SlateGrey; 
                            "CANCELLED" -> Red500; else -> Blue500 
                        },
                        bgColor = when (appt.status) { 
                            "CONFIRMED" -> GreenLight; "PENDING" -> AmberLight; "COMPLETED" -> BorderGrey; 
                            "CANCELLED" -> RedLight; else -> BlueLight 
                        }
                    )
                }
                Spacer(Modifier.height(20.dp))
                
                Text(appt.doctorName, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                Text(appt.specialty, color = SlateGrey, fontSize = 16.sp)
                
                Spacer(Modifier.height(24.dp))
                InfoRow("📅", "Date & Time", appt.dateTime)
                InfoRow("📍", "Location", appt.location)
                InfoRow("🏷️", "Type", appt.appointmentType)
                if (appt.notes.isNotEmpty()) InfoRow("📝", "Notes", appt.notes)
                
                Spacer(Modifier.height(32.dp))
                
                // Cancellation logic: Only PENDING or SCHEDULED
                if (appt.status == "PENDING" || appt.status == "SCHEDULED") {
                    Button(
                        onClick = { onCancel(appt.id); selectedAppt = null },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Red500, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Cancel Appointment", fontWeight = FontWeight.Bold)
                    }
                }
                
                // Reschedule / Reminder options
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
                    OutlinedButton(
                        onClick = {
                            val calendar = Calendar.getInstance()
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val newDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                                    onReschedule(appt.id, newDate)
                                    selectedAppt = null
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).apply { datePicker.minDate = System.currentTimeMillis() }.show()
                        },
                        modifier = Modifier.weight(1f).height(46.dp),
                        border = BorderStroke(1.dp, Teal500),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Reschedule", color = Teal500) }
                    
                    Button(
                        onClick = {
                            val calendar = Calendar.getInstance()
                            android.app.TimePickerDialog(
                                context,
                                { _, h, m ->
                                    scheduleCustomReminder(context, h, m, appt.doctorName)
                                    selectedAppt = null
                                },
                                calendar.get(Calendar.HOUR_OF_DAY),
                                calendar.get(Calendar.MINUTE),
                                false
                            ).show()
                        },
                        modifier = Modifier.weight(1f).height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Teal500),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Set Reminder 🔔") }
                }
                
                TextButton(onClick = { selectedAppt = null }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("Close", color = SlateGrey)
                }
            }
        }
    }

    // ── Request appointment sheet ─────────────────────────────────────────────
    if (showRequestSheet) {
        var reason  by remember { mutableStateOf("Routine follow-up") }
        var date    by remember { mutableStateOf("") }
        var time    by remember { mutableStateOf("09:00") }
        var noteReq by remember { mutableStateOf("") }

        ModalBottomSheet(onDismissRequest = { showRequestSheet = false }) {
            Column(Modifier.padding(20.dp).navigationBarsPadding()) {
                Text("Request Appointment", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(16.dp))
                
                // Reason Selection
                Text("Reason", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateGrey,
                    letterSpacing = 0.5.sp, modifier = Modifier.padding(bottom = 6.dp))
                listOf("Routine follow-up","Worsening symptoms","Medication review","Emergency").forEach { r ->
                    Row(Modifier.fillMaxWidth().clickable { reason = r }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RadioButton(selected = reason == r, onClick = { reason = r },
                            colors = RadioButtonDefaults.colors(selectedColor = Teal500))
                        Text(r, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Date Picker
                    OutlinedTextField(
                        value = date,
                        onValueChange = { },
                        label = { Text("Date") },
                        modifier = Modifier.weight(1f).clickable {
                            val calendar = Calendar.getInstance()
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    date = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).apply { datePicker.minDate = System.currentTimeMillis() }.show()
                        },
                        readOnly = true, enabled = false,
                        trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Teal500) },
                        colors = OutlinedTextFieldDefaults.colors(disabledTextColor = Color.Black, disabledBorderColor = BorderGrey, disabledLabelColor = SlateGrey)
                    )

                    // Time Picker
                    OutlinedTextField(
                        value = time,
                        onValueChange = { },
                        label = { Text("Time") },
                        modifier = Modifier.weight(1f).clickable {
                            val calendar = Calendar.getInstance()
                            android.app.TimePickerDialog(
                                context,
                                { _, h, m -> time = String.format(Locale.US, "%02d:%02d", h, m) },
                                9, 0, true
                            ).show()
                        },
                        readOnly = true, enabled = false,
                        trailingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null, tint = Teal500) },
                        colors = OutlinedTextFieldDefaults.colors(disabledTextColor = Color.Black, disabledBorderColor = BorderGrey, disabledLabelColor = SlateGrey)
                    )
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = noteReq, onValueChange = { noteReq = it },
                    label = { Text("Notes for care team") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onRequestAppointment(reason, date, time, noteReq); showRequestSheet = false },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = date.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal500),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Submit Request", fontWeight = FontWeight.SemiBold) }
                
                TextButton(onClick = { showRequestSheet = false }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// CHAT SCREEN
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

private val chatSuggestions = listOf(
    "What is Furosemide for?", "Why am I on Lisinopril?",
    "I have chest pain", "I feel dizzy",
    "When is my next appointment?", "My ankle is swelling",
    "Can I exercise?", "What foods should I avoid?"
)

@Composable
fun ChatScreen(
    state: ChatUiState,
    patientName: String,
    onSendMessage: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    var inputText   by remember { mutableStateOf("") }
    val listState   = rememberLazyListState()
    val coroutine   = rememberCoroutineScope()
    val suggestions = remember { chatSuggestions.shuffled().take(3) }

    // Auto-scroll to bottom
    LaunchedEffect(state.messages.size, state.isThinking) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Column(Modifier.fillMaxSize()) {

        // ── Header ────────────────────────────────────────────────────────────
        Surface(color = Color.White, shadowElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Teal500, Blue500))),
                    contentAlignment = Alignment.Center
                ) { Text("🤖", fontSize = 18.sp) }
                Column(Modifier.weight(1f)) {
                    Text("MediSync AI", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Green500))
                        Text("Online · Multi-Agent Active", fontSize = 11.sp, color = SlateGrey)
                    }
                }
                IconButton(onClick = { onNavigate("settings") }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = SlateGrey)
                }
            }
        }

        // ── Risk alert if needed ──────────────────────────────────────────────
        if (state.lastRiskFlag == "CRITICAL") {
            AlertBanner("🚨 Critical symptom detected. Please call 999 or go to A&E.",
                AlertType.DANGER, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        } else if (state.lastRiskFlag == "WARNING") {
            AlertBanner("⚠️ Concerning symptom recorded. Care team has been notified.",
                AlertType.WARNING, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }

        // ── Welcome if empty ──────────────────────────────────────────────────
        if (state.messages.isEmpty() && !state.isThinking) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🤖", fontSize = 48.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text("Hi $patientName!", fontWeight = FontWeight.Bold, fontSize = 22.sp, textAlign = TextAlign.Center)
                Text("I'm your MediSync AI nurse.\nHow are you feeling today?",
                    fontSize = 15.sp, color = SlateGrey, textAlign = TextAlign.Center, lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 32.dp))
            }
        } else {
            // ── Messages list ─────────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.messages) { msg ->
                    ChatMessageBubble(msg)
                }
                if (state.isThinking) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(28.dp).clip(CircleShape)
                                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Teal500, Blue500))),
                                contentAlignment = Alignment.Center) { Text("🤖", fontSize = 14.sp) }
                            Surface(color = Color.White, shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                                border = BorderStroke(1.dp, BorderGrey)) {
                                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(12.dp), color = Teal500, strokeWidth = 2.dp)
                                    Text("Thinking...", fontSize = 13.sp, color = SlateGrey)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Suggestions ───────────────────────────────────────────────────────
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            items(suggestions) { sug ->
                Surface(
                    onClick = { onSendMessage(sug) },
                    color = Color(0xFFF8FAFC),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, BorderGrey)
                ) { Text(sug, fontSize = 12.sp, color = SlateGrey, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) }
            }
        }

        // ── Input bar ─────────────────────────────────────────────────────────
        Surface(color = Color.White, shadowElevation = 4.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Ask about medications, symptoms...", color = SlateGrey, fontSize = 14.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp)
                )
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText.trim())
                            inputText = ""
                        }
                    },
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(Teal500)
                ) { Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White) }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(msg: ChatMessageEntity) {
    val isUser = msg.role == "user"
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.size(28.dp).clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Teal500, Blue500))),
                    contentAlignment = Alignment.Center) { Text("🤖", fontSize = 14.sp) }
                Surface(
                    color = Color.White, shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                    border = BorderStroke(1.dp, BorderGrey), modifier = Modifier.widthIn(max = 280.dp)
                ) {
                    Text(
                        text = parseMarkdown(msg.content),
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        } else {
            Surface(
                color = Teal500, shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Text(
                    text = parseMarkdown(msg.content),
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        if (msg.triggeredSymptomRecord) {
            Text("📋 Symptom recorded to hospital database",
                fontSize = 10.sp, color = SlateGrey, modifier = Modifier.padding(start = 36.dp, top = 3.dp))
        }
        Text(
            msg.timestamp.takeLast(8).take(5),
            fontSize = 10.sp, color = SlateGrey,
            modifier = Modifier.padding(top = 2.dp, start = if (!isUser) 36.dp else 0.dp)
        )
    }
}

/**
 * Basic markdown parser for **bold** and *italic* using AnnotatedString.
 */
fun parseMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
        val italicRegex = Regex("\\*(.*?)\\*")
        
        var currentIndex = 0
        
        // Find all matches and sort them by their starting position
        val matches = (boldRegex.findAll(text) + italicRegex.findAll(text))
            .sortedBy { it.range.first }
            .toList()
        
        for (match in matches) {
            // Check if this match overlaps with the previously processed text
            if (match.range.first < currentIndex) continue
            
            // Append plain text before the match
            append(text.substring(currentIndex, match.range.first))
            
            val isBold = match.value.startsWith("**")
            val style = if (isBold) SpanStyle(fontWeight = FontWeight.Bold) 
                        else SpanStyle(fontStyle = FontStyle.Italic)
            
            val content = match.groupValues[1]
            
            pushStyle(style)
            append(content)
            pop()
            
            currentIndex = match.range.last + 1
        }
        
        // Append remaining text
        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// AGENT AUDIT TRAIL SCREEN
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun AuditTrailScreen(state: AuditUiState, onNavigate: (String) -> Unit) {
    var selectedEntry by remember { mutableStateOf<AgentAuditTrailEntity?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        ScreenHeader(
            title = "Agent Audit Trail",
            subtitle = "Transparency log of all AI decisions",
            onSettingsClick = { onNavigate("settings") }
        )

        Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {

            if (state.entries.isEmpty()) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                    border = BorderStroke(1.dp, Teal500.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🤖", fontSize = 32.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No agent decisions recorded yet.", color = SlateGrey, fontSize = 14.sp)
                        Text("Agent actions will appear here as they occur.", color = SlateGrey, fontSize = 12.sp)
                    }
                }
            } else {
                Text("Recent Agent Decisions", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateGrey,
                    letterSpacing = 0.5.sp, modifier = Modifier.padding(bottom = 8.dp))

                state.entries.take(30).forEachIndexed { idx, entry ->
                    val riskColor = when (entry.riskLevel) {
                        "CRITICAL" -> Red500; "WARNING" -> Amber500; else -> Green500
                    }
                    val riskBg = when (entry.riskLevel) {
                        "CRITICAL" -> RedLight; "WARNING" -> AmberLight; else -> GreenLight
                    }

                    Card(
                        onClick = { selectedEntry = entry },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, riskColor.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(color = riskBg, shape = CircleShape) {
                                        Text(entry.agentId.replace("_agent", "").replace("_", " ").replaceFirstChar { it.uppercase() },
                                            color = riskColor, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                    }
                                    Text(entry.action, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Text(entry.timestamp.take(16), fontSize = 10.sp, color = SlateGrey)
                            }
                            if (entry.inputSummary.isNotEmpty()) {
                                Text("Input: ${entry.inputSummary.take(80)}", fontSize = 11.sp, color = SlateGrey,
                                    modifier = Modifier.padding(top = 4.dp))
                            }
                            Text("Output: ${entry.outputSummary.take(100)}", fontSize = 11.sp,
                                color = riskColor, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 2.dp))
                            Row(
                                Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                RiskBadge(entry.riskLevel)
                                if (entry.toolCallsMade != "[]") {
                                    Text("Tools: ${entry.toolCallsMade.take(40)}", fontSize = 9.sp, color = SlateGrey)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedEntry != null) {
        val entry = selectedEntry!!
        AlertDialog(
            onDismissRequest = { selectedEntry = null },
            title = { Text("${entry.agentId.uppercase()} Decision", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    InfoRow("🎬", "Action", entry.action)
                    InfoRow("🕒", "Timestamp", entry.timestamp)
                    InfoRow("📥", "Input Context", entry.inputSummary)
                    InfoRow("📤", "Output / Result", entry.outputSummary)
                    InfoRow("🚦", "Risk Level", entry.riskLevel, valueColor = when(entry.riskLevel){"CRITICAL"-> Red500; "WARNING"-> Amber500; else-> Green500})
                    if (entry.toolCallsMade != "[]") {
                        InfoRow("🛠️", "Tools Called", entry.toolCallsMade)
                    }
                }
            },
            confirmButton = { Button(onClick = { selectedEntry = null }) { Text("Close") } }
        )
    }
}
