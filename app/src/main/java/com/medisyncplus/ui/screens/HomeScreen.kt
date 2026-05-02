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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medisyncplus.data.models.*
import com.medisyncplus.ai.*
import com.medisyncplus.ui.components.*
import com.medisyncplus.ui.theme.*
import com.medisyncplus.viewmodel.*

@Composable
fun HomeScreen(
    homeState: HomeUiState,
    onNavigate: (String) -> Unit,
    onSOS: () -> Unit,
    onMarkMedTaken: (MedicationEntity) -> Unit,
    onToggleTask: (String, Boolean) -> Unit
) {
    val patient = homeState.patient
    val progressPct = if (homeState.todayTasksTotal > 0)
        homeState.todayTasksDone.toFloat() / homeState.todayTasksTotal else 0f

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        // ── Top Bar ──────────────────────────────────────────────────────────
        ScreenHeader(
            title = "MediSync+",
            subtitle = "AI Post-Discharge Care",
            onSettingsClick = { onNavigate("settings") }
        ) {
            Button(
                onClick = onSOS,
                colors = ButtonDefaults.buttonColors(containerColor = Red500, contentColor = Color.White), // High contrast SOS
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(10.dp)
            ) { 
                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("SOS", fontWeight = FontWeight.Black, fontSize = 14.sp) 
            }
        }

        Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {

            // ── Predictive Health Insight ────────────────────────────────────
            Card(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when(homeState.predictedHealthStatus) {
                        "Normal" -> Color(0xFFF0FDF4)
                        "WARNING" -> AmberLight
                        "CRITICAL" -> RedLight
                        else -> Color(0xFFF8FAFC)
                    }
                ),
                border = BorderStroke(1.dp, when(homeState.predictedHealthStatus) {
                    "Normal" -> Teal500.copy(0.3f)
                    "WARNING" -> Amber500.copy(0.3f)
                    "CRITICAL" -> Red500.copy(0.3f)
                    else -> BorderGrey
                }),
                shape = RoundedCornerShape(14.dp)
            ) {
                var expanded by remember { mutableStateOf(false) }
                Column(Modifier.padding(16.dp).animateContentSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                            Text(if(homeState.predictedHealthStatus == "Normal") "✅" else "⚠️", fontSize = 20.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("AI Health Prediction: ${homeState.predictedHealthStatus}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            val summary = homeState.predictiveInsight.ifEmpty { "Monitoring clinical trajectory based on vitals and adherence." }
                            Text(
                                if (expanded) summary else summary.substringBefore(". ") + ".",
                                fontSize = 12.sp, color = SlateGrey
                            )
                        }
                        IconButton(onClick = { expanded = !expanded }) {
                            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = SlateGrey)
                        }
                    }
                }
            }

            // ── Patient Card ─────────────────────────────────────────────────
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderGrey),
                shape = RoundedCornerShape(14.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(Color(0xFFF0FDF4), Color(0xFFEFF6FF))))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Good morning,", fontSize = 13.sp, color = SlateGrey)
                                Text(
                                    patient?.name ?: "Loading...",
                                    fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp
                                )
                                Text(
                                    patient?.primaryCondition ?: "",
                                    fontSize = 13.sp, color = SlateGrey, modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            RiskBadge(patient?.riskLevel ?: "STABLE")
                        }
                        HorizontalDivider(color = BorderGrey, modifier = Modifier.padding(vertical = 12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Column {
                                Text("DOCTOR", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = SlateGrey, letterSpacing = 0.5.sp)
                                Text("Dr. Oscar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Column {
                                Text("DISCHARGED", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = SlateGrey, letterSpacing = 0.5.sp)
                                Text(patient?.dischargeDate ?: "--", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Column {
                                Text("MRN", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = SlateGrey, letterSpacing = 0.5.sp)
                                Text(patient?.mrn?.takeLast(6) ?: "--", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Missed medication alert ───────────────────────────────────────
            if ((homeState.agentStatus?.missedMedications?.isNotEmpty()) == true) {
                AlertBanner(
                    message = "⚠ Important dose(s) missed — please check your schedule. Care team has been notified.",
                    type = AlertType.WARNING,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // ── Next Medication ──────────────────────────────────────────────
            homeState.nextMedication?.let { med ->
                Card(
                    onClick = { onNavigate("medications") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.2.dp, BorderGrey.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape)
                                .background(try { Color(android.graphics.Color.parseColor(med.pillColorHex)).copy(alpha = 0.15f) } catch (e: Exception) { TealLight }),
                            contentAlignment = Alignment.Center
                        ) { Text("💊", fontSize = 24.sp) }
                        Column(Modifier.weight(1f)) {
                            Text("Next Medication", fontSize = 12.sp, color = SlateGrey,
                                fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            Text(med.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("${med.dosage} · ${med.scheduledTime}", fontSize = 14.sp, color = SlateGrey)
                        }
                        Button(
                            onClick = { onMarkMedTaken(med) },
                            colors = ButtonDefaults.buttonColors(containerColor = Teal500),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) { Text("Take", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // ── Today's Tasks (Relocated) ───────────────────────────────────
            Text("Today's Tasks", fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                modifier = Modifier.padding(vertical = 10.dp))
            
            if (homeState.todayTasks.isEmpty()) {
                Text("No tasks for today.", fontSize = 13.sp, color = SlateGrey, modifier = Modifier.padding(bottom = 12.dp))
            } else {
                homeState.todayTasks.forEach { task ->
                    ChecklistTaskItem(task, onToggle = { onToggleTask(task.id, it) })
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Agent Audit Trail (Dedicated Section) ────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Agent Audit Trail", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                TextButton(onClick = { onNavigate("audit") }) {
                    Text("View History", color = Teal500, fontSize = 13.sp)
                }
            }
            
            if (homeState.auditEntries.isEmpty()) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderGrey), shape = RoundedCornerShape(14.dp)) {
                    Text("No agent decisions yet.", modifier = Modifier.padding(16.dp), fontSize = 13.sp, color = SlateGrey)
                }
            } else {
                homeState.auditEntries.forEach { entry ->
                    AuditSmallItem(entry)
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Unread care team reminders badge ─────────────────────────────
            if (homeState.unreadReminders > 0) {
                AlertBanner(
                    message = "${homeState.unreadReminders} unread message(s) from your care team",
                    type = AlertType.INFO,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // ── Quick Actions Grid ───────────────────────────────────────────
            // REMOVED "Quick Actions" Section as per requirement #5
            /*
            Text("Quick Actions", ...
            */

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun AuditSmallItem(entry: AgentAuditTrailEntity) {
    val riskColor = when (entry.riskLevel) {
        "CRITICAL" -> Red500; "WARNING" -> Amber500; else -> Green500
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderGrey),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(riskColor))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(entry.agentId.replace("_agent", "").uppercase(), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = SlateGrey)
                    Text(entry.timestamp.takeLast(8).take(5), fontSize = 10.sp, color = SlateGrey)
                }
                Text(entry.outputSummary, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}
