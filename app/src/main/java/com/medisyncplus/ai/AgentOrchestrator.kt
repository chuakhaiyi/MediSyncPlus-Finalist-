package com.medisyncplus.ai

import android.util.Log
import com.medisyncplus.BuildConfig
import com.medisyncplus.data.models.*
import com.medisyncplus.data.repository.MediSyncRepository
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MediSyncOrchestrator"

// ─── Agent result types ───────────────────────────────────────────────────────
data class SymptomAnalysisResult(
    val riskLevel: String,
    val riskReason: String,
    val detectedSymptoms: List<String>,
    val recommendedAction: String,
    val escalateImmediately: Boolean,
    val bookAppointment: Boolean,
    val appointmentReason: String?,
    val emrUpdateRequired: Boolean,
    val agentNotes: String
)

data class MedicineAgentResult(
    val nextMedication: MedicationEntity?,
    val nextLog: MedicationLogEntity?,
    val adherenceScore: Int,
    val adherenceLabel: String,
    val missedMedications: List<MissedMedInfo>,
    val riskFromAdherence: String,
    val alertRequired: Boolean,
    val alertMessage: String,
    val patientMessage: String,
    val emrUpdateRequired: Boolean,
    val agentId: String = "medicine_agent"
)

data class MissedMedInfo(val name: String, val missedStreak: Int, val critical: Boolean)

data class ChatResponse(
    val text: String,
    val recordedSymptom: Boolean,
    val requestedAppointment: Boolean,
    val riskFlag: String
)

data class RiskTrajectory(
    val trajectory: String,
    val trajectoryReason: String,
    val predictedRiskIn48h: String,
    val keyRiskFactors: List<String>,
    val recommendedMonitoringFrequency: String,
    val interventionRequired: Boolean,
    val interventionType: String
)

data class FollowUpResult(
    val nextAppointment: AppointmentEntity?,
    val daysUntilNext: Int?,
    val pendingAppointments: List<AppointmentEntity>,
    val reminderMessage: String,
    val shouldAlert: Boolean,
    val alertReason: String,
    val agentId: String = "followup_agent"
)

data class ChecklistAgentResult(
    val completedCount: Int,
    val totalCount: Int,
    val pendingVitalTasks: List<ChecklistTaskEntity>
)

data class MorningCheckResult(
    val overallRisk: String,
    val summary: String,
    val medicineResult: MedicineAgentResult,
    val followUpResult: FollowUpResult,
    val checklistResult: ChecklistAgentResult,
    val riskTrajectory: RiskTrajectory
)

// ─── Orchestrator ─────────────────────────────────────────────────────────────
@Singleton
class AgentOrchestrator @Inject constructor(
    private val apiService: LlmApiService,
    private val toolRegistry: AgentToolRegistry,
    private val repo: MediSyncRepository,
    private val gson: Gson
) {
    private val authHeader get() = "Bearer ${BuildConfig.LLM_API_KEY}"

    /**
     * Delegates symptom analysis to the Backend Clinical Agent via Orchestrator.
     */
    suspend fun runSymptomAgent(
        symptoms: List<String>,
        severity: Int,
        note: String,
        patientId: String
    ): SymptomAnalysisResult {
        val userInput = "Symptoms: ${symptoms.joinToString()}, Severity: $severity. Note: $note"
        val response = try {
            apiService.interact(authHeader, AiInteractRequest(userInput, patientId))
        } catch (e: Exception) {
            Log.e(TAG, "Backend symptom analysis failed", e)
            emptyMap<String, Any?>()
        }
        
        val data = response["data"] as? Map<*, *>
        
        return SymptomAnalysisResult(
            riskLevel = data?.get("urgency") as? String ?: "STABLE",
            riskReason = data?.get("riskReason") as? String ?: "",
            detectedSymptoms = symptoms,
            recommendedAction = data?.get("recommendedAction") as? String ?: "Monitor symptoms.",
            escalateImmediately = (data?.get("urgency") == "EMERGENCY" || data?.get("urgency") == "CRITICAL"),
            bookAppointment = data?.get("bookAppointment") as? Boolean ?: false,
            appointmentReason = data?.get("appointmentReason") as? String,
            emrUpdateRequired = true,
            agentNotes = data?.get("agentNotes") as? String ?: ""
        )
    }

    /**
     * Executes a full health assessment via the Backend Orchestrator.
     */
    suspend fun runFullMorningCheck(patientId: String): MorningCheckResult {
        val response = try {
            apiService.interact(authHeader, AiInteractRequest("Perform full morning health check.", patientId))
        } catch (e: Exception) {
            Log.e(TAG, "Backend morning check failed", e)
            emptyMap<String, Any?>()
        }
        
        val data = response["data"] as? Map<*, *>
        
        return MorningCheckResult(
            overallRisk = data?.get("overallRisk") as? String ?: "STABLE",
            summary = data?.get("summary") as? String ?: "Your health status is currently stable.",
            medicineResult = MedicineAgentResult(null, null, 100, "Good", emptyList(), "STABLE", false, "", "", false),
            followUpResult = FollowUpResult(null, 0, emptyList(), "", false, ""),
            checklistResult = ChecklistAgentResult(
                data?.get("tasksDone") as? Int ?: 0,
                data?.get("tasksTotal") as? Int ?: 0,
                emptyList()
            ),
            riskTrajectory = RiskTrajectory("STABLE", "", "STABLE", emptyList(), "daily", false, "")
        )
    }

    /**
     * Multi-Agent Chat: Routes general queries to the Backend Chat Agent.
     */
    suspend fun runChatAgent(
        userMessage: String,
        patientId: String
    ): ChatResponse {
        val response = try {
            apiService.interact(authHeader, AiInteractRequest(userMessage, patientId))
        } catch (e: Exception) {
            Log.e(TAG, "Backend chat failed", e)
            emptyMap<String, Any?>()
        }
        
        val data = response["data"] as? Map<*, *>
        
        return ChatResponse(
            text = data?.get("text") as? String ?: data?.get("message") as? String ?: "I'm sorry, I couldn't process that.",
            recordedSymptom = data?.get("recordedSymptom") as? Boolean ?: false,
            requestedAppointment = data?.get("requestedAppointment") as? Boolean ?: false,
            riskFlag = data?.get("urgency") as? String ?: data?.get("riskLevel") as? String ?: "STABLE"
        )
    }

    /**
     * Medicine agent run via backend.
     */
    suspend fun runMedicineAgent(): MedicineAgentResult {
        val response = try {
            apiService.interact(authHeader, AiInteractRequest("Analyze my medication adherence.", toolRegistry.patientId))
        } catch (e: Exception) {
            Log.e(TAG, "Backend medication analysis failed", e)
            emptyMap<String, Any?>()
        }
        
        val data = response["data"] as? Map<*, *>
        
        return MedicineAgentResult(
            nextMedication = null,
            nextLog = null,
            adherenceScore = (data?.get("adherenceScore") as? Number)?.toInt() ?: 100,
            adherenceLabel = data?.get("status") as? String ?: "Good",
            missedMedications = emptyList(),
            riskFromAdherence = "STABLE",
            alertRequired = false,
            alertMessage = "",
            patientMessage = "",
            emrUpdateRequired = false
        )
    }
    
    // Legacy support methods
    suspend fun runFollowUpAgent(): FollowUpResult = FollowUpResult(null, 0, emptyList(), "", false, "")
    suspend fun runChecklistAgent(): ChecklistAgentResult = ChecklistAgentResult(0, 0, emptyList())
    suspend fun runRiskTrajectoryAgent(): RiskTrajectory = RiskTrajectory("STABLE", "", "STABLE", emptyList(), "daily", false, "")

    /**
     * Low-level LLM call for use outside individual agents (e.g. AI checklist generation).
     */
    suspend fun callLlmRaw(systemPrompt: String, userPrompt: String, maxTokens: Int = 2000): String {
        val request = LlmRequest(
            model = BuildConfig.LLM_MODEL,
            messages = listOf(
                LlmMessage("system", systemPrompt),
                LlmMessage("user", userPrompt)
            ),
            maxTokens = maxTokens
        )
        return try {
            val response = apiService.complete("Bearer ${BuildConfig.LLM_API_KEY}", request)
            response.choices.firstOrNull()?.message?.content ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Raw LLM call failed", e)
            ""
        }
    }

    suspend fun testConnection(): String {
        return try {
            val res = apiService.interact(authHeader, AiInteractRequest("ping", toolRegistry.patientId))
            "Backend Connected: $res"
        } catch (e: Exception) {
            "Connection failed: ${e.message}"
        }
    }
}
