package com.medisyncplus.ai.agents

import android.util.Log
import com.medisyncplus.ai.*
import com.medisyncplus.data.models.*
import com.medisyncplus.data.repository.MediSyncRepository
import com.google.gson.Gson
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SymptomAgent @Inject constructor(
    private val llmService: LlmApiService,
    private val toolRegistry: AgentToolRegistry,
    private val repo: MediSyncRepository,
    private val gson: Gson
) {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val tsFmt   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private fun now()   = LocalDateTime.now().format(tsFmt)
    private fun today() = LocalDate.now().format(dateFmt)

    companion object { private const val TAG = "SymptomAgent" }

    suspend fun analyse(symptoms: List<String>, severity: Int, note: String, patientId: String): SymptomAnalysisResult {
        val patient = repo.getPatientOnce(patientId)
        val medications = repo.getActiveMedicationsOnce(patientId)
        val history = repo.getRecentSymptomReports(patientId, 5)
        val vitals = repo.getRecentVitals(patientId, 10)

        val condition = patient?.primaryCondition ?: "Chronic Heart Failure"

        val contextInfo = """
PATIENT PROFILE: $condition, Allergies: ${patient?.allergies}
MEDICATIONS: ${medications.joinToString(", ") { it.name }}
RECENT HISTORY: ${history.joinToString("; ") { h -> "${h.symptoms} (Risk: ${h.agentRiskLevel})" }}
RECENT VITALS: ${vitals.joinToString("; ") { v -> "${v.type}: ${v.value ?: "${v.systolic}/${v.diastolic}"}" }}
        """.trimIndent()

        // Step 1: Preprocess input
        val processedNote = InputProcessor.preprocess(note)
        val stats = InputProcessor.getProcessingStats(processedNote)
        
        // Step 2: Logging for oversized inputs
        if (stats.strategy != "normal") {
            repo.insertAuditTrail(AgentAuditTrailEntity(
                patientId = patientId,
                agentId = "input_processor",
                action = "INPUT_HANDLING",
                inputSummary = "Oversized symptom note from patient",
                outputSummary = "Strategy: ${stats.strategy}, Original: ${stats.originalLength}, Processed: ${stats.processedLength}",
                riskLevel = "STABLE",
                toolCallsMade = "[]",
                timestamp = now()
            ))
        }

        // Step 3: Handle chunks or truncation
        val effectiveNote = if (stats.isTruncated) {
            InputProcessor.truncateInput(processedNote) + "\n[INPUT TRUNCATED]"
        } else {
            processedNote
        }

        val userMsg = buildString {
            appendLine("PATIENT CLINICAL CONTEXT:")
            appendLine(contextInfo)
            appendLine()
            appendLine("NEW PATIENT REPORT:")
            appendLine("Symptoms: ${symptoms.joinToString(", ")}")
            appendLine("Severity: $severity/3 (1=mild, 2=moderate, 3=severe)")
            appendLine("Patient note: ${effectiveNote.ifEmpty { "none" }}")
            if (stats.isTruncated) appendLine("WARNING: The patient note was truncated due to excessive length.")
            appendLine()
            appendLine("Instructions: Use the provided CLINICAL CONTEXT and historical data to perform a highly accurate CHF risk assessment.")
        }

        // Use AgentOrchestratorHelper with retry and validation
        val rawResponse = AgentOrchestratorHelper.runAgentWithTools(
            llmService, toolRegistry, gson,
            systemPrompt = AgentPrompts.symptomAgentSystem(condition) + "\n\n" + toolRegistry.toolManifest,
            userMessage = userMsg,
            requiredJsonFields = listOf("riskLevel", "recommendedAction")
        )

        val json = AgentOrchestratorHelper.parseJson(rawResponse)
        if (json == null || json.optString("status") == "fallback_triggered") {
            Log.w(TAG, "Symptom agent returned fallback or invalid JSON, using safety fallback")
            val isEscalated = json?.optBoolean("escalation_required", false) ?: ResponseValidator.isHighRisk(userMsg)
            return fallbackAnalysis(symptoms, severity, isEscalated, patientId)
        }

        try {
            val result = SymptomAnalysisResult(
                riskLevel             = json.optString("riskLevel", "STABLE"),
                riskReason            = json.optString("riskReason", ""),
                detectedSymptoms      = symptoms,
                recommendedAction     = json.optString("recommendedAction", "Monitor"),
                escalateImmediately   = json.optBoolean("escalateImmediately", false),
                bookAppointment       = json.optBoolean("bookAppointment", false),
                appointmentReason     = json.optString("appointmentReason").takeIf { it.isNotEmpty() && it != "null" },
                emrUpdateRequired     = json.optBoolean("emrUpdateRequired", false),
                agentNotes            = json.optString("agentNotes", "") + if (stats.isTruncated) " (Note was truncated)" else ""
            )
            executePostAnalysis(result, symptoms, severity, effectiveNote)
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Symptom agent parse error: ${e.message}")
            return fallbackAnalysis(symptoms, severity, false, patientId)
        }
    }

    private suspend fun executePostAnalysis(
        result: SymptomAnalysisResult,
        symptoms: List<String>,
        severity: Int,
        note: String
    ) {
        if (result.escalateImmediately) {
            toolRegistry.dispatch(ToolCall("send_clinician_alert", mapOf(
                "message" to "CRITICAL: Patient reported ${symptoms.joinToString(", ")}. ${result.riskReason}",
                "urgency" to "IMMEDIATE",
                "alertType" to "SYMPTOM_ESCALATION"
            )))
        }
        if (result.emrUpdateRequired) {
            toolRegistry.dispatch(ToolCall("propose_emr_update", mapOf(
                "type" to "SYMPTOM_RECORD",
                "changes" to mapOf("symptoms" to symptoms, "riskLevel" to result.riskLevel),
                "justification" to result.riskReason,
                "urgency" to if (result.escalateImmediately) "IMMEDIATE" else "URGENT",
                "requiresReview" to true,
                "agentId" to "symptom_agent"
            )))
        }
        if (result.bookAppointment && result.appointmentReason != null) {
            toolRegistry.dispatch(ToolCall("request_appointment", mapOf(
                "reason" to result.appointmentReason,
                "preferredDate" to LocalDate.now().plusDays(1).format(dateFmt),
                "notes" to "Auto-requested by symptom agent. Risk: ${result.riskLevel}",
                "urgency" to if (result.riskLevel == "CRITICAL") "IMMEDIATE" else "URGENT"
            )))
        }
        toolRegistry.dispatch(ToolCall("record_symptom", mapOf(
            "symptoms" to symptoms,
            "severity" to severity.toDouble(),
            "note" to note,
            "source" to "manual",
            "riskLevel" to result.riskLevel,
            "reason" to result.riskReason,
            "action" to result.recommendedAction,
            "escalate" to result.escalateImmediately
        )))
    }

    private suspend fun fallbackAnalysis(symptoms: List<String>, severity: Int, escalated: Boolean, patientId: String): SymptomAnalysisResult {
        val risk = if (escalated || severity == 3) "CRITICAL" else if (severity == 2) "WARNING" else "STABLE"
        
        if (escalated) {
            repo.insertAuditTrail(AgentAuditTrailEntity(
                patientId = patientId, agentId = "symptom_agent",
                action = "AI_RESPONSE_VALIDATION", 
                inputSummary = "Symptom report with high risk",
                outputSummary = "Validation failed after retries. Escalating to human clinician.",
                riskLevel = "CRITICAL", toolCallsMade = "[\"human_escalation\"]", timestamp = now()
            ))
            toolRegistry.dispatch(ToolCall("send_clinician_alert", mapOf(
                "message" to "HUMAN ESCALATION: AI could not process high-risk symptom report safely. Symptoms: ${symptoms.joinToString(", ")}",
                "urgency" to "IMMEDIATE",
                "alertType" to "ESCALATION"
            )))
        }

        return SymptomAnalysisResult(
            riskLevel = risk, riskReason = "Safe fallback triggered due to processing uncertainty.",
            detectedSymptoms = symptoms,
            recommendedAction = when (risk) {
                "CRITICAL" -> "Call 999 or go to A&E immediately"
                "WARNING" -> "Contact your care team today"
                else -> "Please try describing your symptoms again or contact your clinic."
            },
            escalateImmediately = risk == "CRITICAL",
            bookAppointment = risk == "WARNING",
            appointmentReason = if (risk == "WARNING") "Symptom review" else null,
            emrUpdateRequired = true,
            agentNotes = "FALLBACK USED: System was unable to validate AI output safely."
        )
    }
}
