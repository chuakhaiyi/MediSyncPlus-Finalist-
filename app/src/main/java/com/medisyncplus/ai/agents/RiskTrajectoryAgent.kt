package com.medisyncplus.ai.agents

import android.util.Log
import com.medisyncplus.ai.*
import com.medisyncplus.data.repository.MediSyncRepository
import com.google.gson.Gson
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RiskTrajectoryAgent @Inject constructor(
    private val llmService: LlmApiService,
    private val toolRegistry: AgentToolRegistry,
    private val repo: MediSyncRepository,
    private val gson: Gson
) {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val patientId: String get() = toolRegistry.patientId

    companion object { private const val TAG = "RiskTrajectoryAgent" }

    suspend fun analyse(): RiskTrajectory {
        val patient = repo.getPatientOnce(patientId)
        val condition = patient?.primaryCondition ?: "Chronic Heart Failure"

        val userMsg = """
Analyse this patient's overall risk trajectory for the past 7 days.
Call get_vitals, get_medication_logs, get_symptom_reports, and get_patient_profile.
Assess weight trend, BP trend, adherence pattern, and symptom frequency.
Predict risk in next 48h. Return valid JSON only.
        """.trimIndent()

        val rawResponse = AgentOrchestratorHelper.runAgentWithTools(
            llmService, toolRegistry, gson,
            systemPrompt = AgentPrompts.riskTrajectorySystem(condition) + "\n\n" + toolRegistry.toolManifest,
            userMessage = userMsg
        )

        val json = AgentOrchestratorHelper.parseJson(rawResponse)
        if (json == null) return defaultTrajectory()

        return try {
            RiskTrajectory(
                trajectory = json.optString("trajectory", "STABLE"),
                trajectoryReason = json.optString("trajectoryReason", "Insufficient data"),
                predictedRiskIn48h = json.optString("predictedRiskIn48h", "STABLE"),
                keyRiskFactors = buildList {
                    val arr = json.optJSONArray("keyRiskFactors")
                    if (arr != null) for (i in 0 until arr.length()) add(arr.getString(i))
                },
                recommendedMonitoringFrequency = json.optString("recommendedMonitoringFrequency", "daily"),
                interventionRequired = json.optBoolean("interventionRequired", false),
                interventionType = json.optString("interventionType", "monitoring_only")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Risk trajectory parse error: ${e.message}")
            defaultTrajectory()
        }
    }

    fun defaultTrajectory() = RiskTrajectory(
        trajectory = "STABLE",
        trajectoryReason = "Insufficient data for trajectory analysis",
        predictedRiskIn48h = "STABLE",
        keyRiskFactors = emptyList(),
        recommendedMonitoringFrequency = "daily",
        interventionRequired = false,
        interventionType = "monitoring_only"
    )
}
