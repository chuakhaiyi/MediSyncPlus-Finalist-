package com.medisyncplus.ai.agents

import android.util.Log
import com.medisyncplus.ai.*
import com.medisyncplus.data.models.*
import com.medisyncplus.data.repository.MediSyncRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class ChecklistAgentResult(
    val pendingCount: Int,
    val completedCount: Int,
    val totalCount: Int,
    val pendingVitalTasks: List<ChecklistTaskEntity>,
    val reminderMessage: String,
    val shouldNotify: Boolean,
    val agentId: String = "checklist_agent"
)

@Singleton
class ChecklistAgent @Inject constructor(
    private val toolRegistry: AgentToolRegistry,
    private val repo: MediSyncRepository
) {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val tsFmt   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private fun now()   = LocalDateTime.now().format(tsFmt)
    private fun today() = LocalDate.now().format(dateFmt)
    private val patientId: String get() = toolRegistry.patientId

    companion object { private const val TAG = "ChecklistAgent" }

    suspend fun analyse(): ChecklistAgentResult = try {
        val tasks = repo.getChecklistForDateOnce(patientId, today())
        val completed = tasks.count { it.isDone }
        val pending = tasks.filter { !it.isDone }
        val pendingVitals = pending.filter { it.requiresVitalInput }

        val shouldNotify = pending.isNotEmpty() && isAfternoonOrLater()

        val reminderMessage = when {
            pending.isEmpty() -> "All tasks completed! Great job, Margaret."
            pendingVitals.isNotEmpty() -> "You still need to record: ${pendingVitals.joinToString { it.description }}. These are important for your care team."
            pending.size > 5 -> "You have ${pending.size} tasks remaining today. Try to complete them before evening."
            else -> "You have ${pending.size} task(s) remaining. Keep going!"
        }

        if (shouldNotify && pending.any { it.requiresVitalInput }) {
            toolRegistry.dispatch(ToolCall("propose_emr_update", mapOf(
                "type" to "CARE_PLAN_REVISION",
                "changes" to mapOf("pendingVitalTasks" to pendingVitals.map { it.vitalType }, "pendingCount" to pending.size),
                "justification" to "Checklist agent: ${pendingVitals.size} vital recording tasks still pending for today",
                "urgency" to "ROUTINE",
                "requiresReview" to false,
                "agentId" to "checklist_agent"
            )))
        }

        ChecklistAgentResult(
            pendingCount = pending.size,
            completedCount = completed,
            totalCount = tasks.size,
            pendingVitalTasks = pendingVitals,
            reminderMessage = reminderMessage,
            shouldNotify = shouldNotify
        )
    } catch (e: Exception) {
        Log.e(TAG, "analyse failed: ${e.message}")
        ChecklistAgentResult(0, 0, 0, emptyList(), "Unable to check tasks.", false)
    }

    suspend fun completeTaskWithVital(taskId: String, vitalType: String?, vitalValue: Any?) {
        repo.completeTask(taskId, patientId)
        if (vitalType != null && vitalValue != null) {
            when (vitalType) {
                "weight" -> {
                    val kg = (vitalValue as? Float) ?: return
                    val flag = if (kg > 72f) "warning" else "normal"
                    repo.insertVital(VitalEntity(
                        patientId = patientId, type = "weight", value = kg,
                        systolic = null, diastolic = null, unit = "kg",
                        flag = flag, recordedAt = now(), recordedBy = "patient"
                    ))
                }
                "bp" -> {
                    val pair = vitalValue as? Pair<*, *> ?: return
                    val sys = pair.first as? Int ?: return
                    val dia = pair.second as? Int ?: return
                    val flag = when { sys >= 180 || dia >= 110 -> "critical"; sys >= 140 || dia >= 90 -> "warning"; else -> "normal" }
                    repo.insertVital(VitalEntity(
                        patientId = patientId, type = "bp", value = null,
                        systolic = sys, diastolic = dia, unit = "mmHg",
                        flag = flag, recordedAt = now(), recordedBy = "patient"
                    ))
                }
                "blood_sugar" -> {
                    val mmol = (vitalValue as? Float) ?: return
                    val flag = when { mmol > 11f -> "critical"; mmol > 7.8f -> "warning"; else -> "normal" }
                    repo.insertVital(VitalEntity(
                        patientId = patientId, type = "blood_sugar", value = mmol,
                        systolic = null, diastolic = null, unit = "mmol/L",
                        flag = flag, recordedAt = now(), recordedBy = "patient"
                    ))
                }
            }
        }
    }

    private fun isAfternoonOrLater(): Boolean {
        val hour = LocalDateTime.now().hour
        return hour >= 14
    }
}
