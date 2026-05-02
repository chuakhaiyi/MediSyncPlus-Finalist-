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

@Singleton
class MedicineAgent @Inject constructor(
    private val llmService: LlmApiService,
    private val toolRegistry: AgentToolRegistry,
    private val repo: MediSyncRepository,
    private val gson: com.google.gson.Gson
) {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val tsFmt   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private fun now()   = LocalDateTime.now().format(tsFmt)
    private fun today() = LocalDate.now().format(dateFmt)
    private val patientId: String get() = toolRegistry.patientId

    companion object { private const val TAG = "MedicineAgent" }

    suspend fun analyse(): MedicineAgentResult = try {
        val logs = repo.getMedLogsForDateOnce(patientId, today())
        val meds = repo.getActiveMedicationsOnce(patientId)
        val score = repo.calculateAdherenceScore(patientId)

        val upcomingLogs = logs.filter { it.status == "UPCOMING" }
        val nextMedId = upcomingLogs.firstOrNull()?.medicationId
        val nextMed = meds.find { it.id == nextMedId }
        val nextLog = upcomingLogs.firstOrNull()

        val missed = logs.filter { it.status == "MISSED" }.mapNotNull { log ->
            val med = meds.find { it.id == log.medicationId } ?: return@mapNotNull null
            MissedMedInfo(med.name, log.missedStreak, med.criticalMedication)
        }

        val label = when { score >= 90 -> "Good"; score >= 70 -> "Fair"; score >= 50 -> "Poor"; else -> "Critical" }
        val risk = when { score < 50 -> "CRITICAL"; score < 70 -> "WARNING"; else -> "STABLE" }

        val result = MedicineAgentResult(
            nextMedication = nextMed,
            nextLog = nextLog,
            adherenceScore = score,
            adherenceLabel = label,
            missedMedications = missed,
            riskFromAdherence = risk,
            alertRequired = score < 70 || missed.any { it.critical },
            alertMessage = if (missed.any { it.critical }) "Critical medication missed: ${missed.filter { it.critical }.joinToString { it.name }}"
                else if (score < 70) "Adherence score low: $score%" else "",
            patientMessage = when {
                missed.any { it.critical } -> "Please take ${missed.filter { it.critical }.joinToString { it.name }} immediately — this is critical for your heart."
                score < 70 -> "Please remember to take all your medications as prescribed."
                else -> "Good job staying on track with your medications!"
            },
            emrUpdateRequired = score < 90 || missed.isNotEmpty()
        )

        if (result.alertRequired && result.alertMessage.isNotEmpty()) {
            toolRegistry.dispatch(ToolCall("send_clinician_alert", mapOf(
                "message" to result.alertMessage,
                "urgency" to if (risk == "CRITICAL") "IMMEDIATE" else "URGENT",
                "alertType" to "MEDICATION_MISS"
            )))
        }
        if (result.emrUpdateRequired) {
            toolRegistry.dispatch(ToolCall("propose_emr_update", mapOf(
                "type" to "MEDICATION_UPDATE",
                "changes" to mapOf("adherenceScore" to score, "missed" to missed.map { it.name }),
                "justification" to "Medicine agent analysis. Score: $score%. Missed: ${missed.joinToString { it.name }}",
                "urgency" to if (risk == "CRITICAL") "IMMEDIATE" else "URGENT",
                "requiresReview" to (score < 70),
                "agentId" to "medicine_agent"
            )))
        }
        result
    } catch (e: Exception) {
        Log.e(TAG, "analyse failed: ${e.message}")
        val score = repo.calculateAdherenceScore(patientId)
        MedicineAgentResult(
            nextMedication = null, nextLog = null,
            adherenceScore = score, adherenceLabel = "Unknown",
            missedMedications = emptyList(), riskFromAdherence = "STABLE",
            alertRequired = false, alertMessage = "", patientMessage = "",
            emrUpdateRequired = false
        )
    }

    suspend fun runLlmAnalysis(): MedicineAgentResult {
        val patient = repo.getPatientOnce(patientId)
        val patientName = patient?.name ?: "Patient"

        val userMsg = """
Analyse this patient's medication adherence for today and the past 7 days.
Call get_medication_logs and get_medications to fetch the data, then analyse.
Return your response as valid JSON only.
        """.trimIndent()

        val rawResponse = AgentOrchestratorHelper.runAgentWithTools(
            llmService, toolRegistry, gson,
            systemPrompt = AgentPrompts.adherenceAgentSystem(patientName) + "\n\n" + toolRegistry.toolManifest,
            userMessage = userMsg
        )

        val json = AgentOrchestratorHelper.parseJson(rawResponse)
        if (json == null) return analyse()

        return try {
            val missedArr = json.optJSONArray("missedMedications")
            val missedList = (0 until (missedArr?.length() ?: 0)).map { i ->
                val obj = missedArr!!.getJSONObject(i)
                MissedMedInfo(obj.optString("name"), obj.optInt("missedStreak"), obj.optBoolean("critical"))
            }
            MedicineAgentResult(
                nextMedication = null, nextLog = null,
                adherenceScore = json.optInt("adherenceScore", 80),
                adherenceLabel = json.optString("adherenceLabel", "Fair"),
                missedMedications = missedList,
                riskFromAdherence = json.optString("riskFromAdherence", "STABLE"),
                alertRequired = json.optBoolean("alertRequired", false),
                alertMessage = json.optString("alertMessage", ""),
                patientMessage = json.optString("patientMessage", ""),
                emrUpdateRequired = json.optBoolean("emrUpdateRequired", false)
            ).also { result ->
                if (result.alertRequired) {
                    toolRegistry.dispatch(ToolCall("send_clinician_alert", mapOf(
                        "message" to result.alertMessage,
                        "urgency" to if (result.riskFromAdherence == "CRITICAL") "IMMEDIATE" else "URGENT",
                        "alertType" to "MEDICATION_MISS"
                    )))
                }
                if (result.emrUpdateRequired) {
                    toolRegistry.dispatch(ToolCall("propose_emr_update", mapOf(
                        "type" to "MEDICATION_UPDATE",
                        "changes" to mapOf("adherenceScore" to result.adherenceScore),
                        "justification" to "Medicine agent LLM analysis. Score: ${result.adherenceScore}%",
                        "urgency" to "URGENT", "requiresReview" to (result.adherenceScore < 70),
                        "agentId" to "medicine_agent"
                    )))
                }
            }
        } catch (e: Exception) { analyse() }
    }
}
