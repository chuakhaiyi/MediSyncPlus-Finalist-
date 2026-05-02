package com.medisyncplus.ai.agents

import android.util.Log
import com.medisyncplus.ai.*
import com.medisyncplus.data.models.*
import com.medisyncplus.data.repository.MediSyncRepository
import com.google.gson.Gson
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class DischargeInterpretation(
    val fluidRestriction: String?,
    val saltRestriction: String?,
    val dailyWeighIn: Boolean,
    val weightGainAlert: String?,
    val activityLevel: String,
    val dietaryNotes: List<String>,
    val followUpWeeks: Int,
    val redFlags: List<String>,
    val medicationsToMonitor: List<String>,
    val patientEducationPoints: List<String>,
    val agentId: String = "discharge_agent"
)

@Singleton
class DischargeAgent @Inject constructor(
    private val llmService: LlmApiService,
    private val toolRegistry: AgentToolRegistry,
    private val repo: MediSyncRepository,
    private val gson: Gson
) {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val patientId: String get() = toolRegistry.patientId

    companion object { private const val TAG = "DischargeAgent" }

    suspend fun interpretDischargeNote(rawNote: String): DischargeInterpretation {
        val userMsg = """
Parse the following raw discharge note into a structured care plan.

RAW DISCHARGE NOTE:
$rawNote

Call get_patient_profile first for context, then parse.
Return valid JSON only.
        """.trimIndent()

        val rawResponse = AgentOrchestratorHelper.runAgentWithTools(
            llmService, toolRegistry, gson,
            systemPrompt = AgentPrompts.dischargeInterpretationSystem() + "\n\n" + toolRegistry.toolManifest,
            userMessage = userMsg
        )

        val json = AgentOrchestratorHelper.parseJson(rawResponse)
        if (json == null) return fallbackInterpretation(rawNote)

        return try {
            val carePlan = json.optJSONObject("carePlan")
            val dietaryArr = carePlan?.optJSONArray("dietaryNotes")
            val redFlagsArr = json.optJSONArray("redFlags")
            val medMonitorArr = json.optJSONArray("medicationsToMonitor")
            val eduArr = json.optJSONArray("patientEducationPoints")

            DischargeInterpretation(
                fluidRestriction = carePlan?.optString("fluidRestriction")?.takeIf { it != "null" && it.isNotEmpty() },
                saltRestriction = carePlan?.optString("saltRestriction")?.takeIf { it != "null" && it.isNotEmpty() },
                dailyWeighIn = carePlan?.optBoolean("dailyWeighIn", true) ?: true,
                weightGainAlert = carePlan?.optString("weightGainAlert")?.takeIf { it != "null" && it.isNotEmpty() },
                activityLevel = carePlan?.optString("activityLevel", "light") ?: "light",
                dietaryNotes = (0 until (dietaryArr?.length() ?: 0)).mapNotNull { dietaryArr?.getString(it) },
                followUpWeeks = json.optInt("followUpWeeks", 2),
                redFlags = (0 until (redFlagsArr?.length() ?: 0)).mapNotNull { redFlagsArr?.getString(it) },
                medicationsToMonitor = (0 until (medMonitorArr?.length() ?: 0)).mapNotNull { medMonitorArr?.getString(it) },
                patientEducationPoints = (0 until (eduArr?.length() ?: 0)).mapNotNull { eduArr?.getString(it) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Discharge agent parse error: ${e.message}")
            fallbackInterpretation(rawNote)
        }
    }

    private fun fallbackInterpretation(note: String) = DischargeInterpretation(
        fluidRestriction = "1.5L/day",
        saltRestriction = "<2g/day",
        dailyWeighIn = true,
        weightGainAlert = "2kg in 2 days",
        activityLevel = "light",
        dietaryNotes = listOf("avoid salty foods", "limit fluid intake"),
        followUpWeeks = 2,
        redFlags = listOf("chest pain", "sudden breathlessness", "weight gain >2kg in 2 days"),
        medicationsToMonitor = listOf("Furosemide (potassium)", "Spironolactone (potassium)"),
        patientEducationPoints = listOf("weigh every morning", "call if gain >2kg", "take all medications")
    )
}
