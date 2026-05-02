package com.medisyncplus.ai.agents

import android.util.Log
import com.medisyncplus.ai.*
import com.medisyncplus.data.models.*
import com.medisyncplus.data.repository.MediSyncRepository
import kotlinx.coroutines.flow.first
import com.google.gson.Gson
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatAgent @Inject constructor(
    private val llmService: LlmApiService,
    private val toolRegistry: AgentToolRegistry,
    private val repo: MediSyncRepository,
    private val gson: Gson
) {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val tsFmt   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private fun now()   = LocalDateTime.now().format(tsFmt)

    companion object { private const val TAG = "ChatAgent" }

    suspend fun respond(
        userMessage: String,
        conversationHistory: List<ChatMessageEntity>
    ): ChatResponse {
        val pid = toolRegistry.patientId
        val patient = repo.getPatientOnce(pid)
        val medications = repo.getActiveMedicationsOnce(pid)
        val appointments = repo.getUpcomingAppointments(pid).first()
        val vitals = repo.getRecentVitals(pid, 5)
        val latestStay = repo.getLatestStay(pid)

        val patientName = patient?.name ?: "Margaret"
        val condition = patient?.primaryCondition ?: "Congestive Heart Failure (CHF)"

        // Format the Care Plan into human-readable text for the LLM
        val carePlanText = latestStay?.let { stay ->
            try {
                val json = JSONObject(stay.dischargeNoteParsed)
                val cp = json.optJSONObject("carePlan")
                if (cp != null) {
                    val fluid = cp.optString("fluidRestriction", "None")
                    val salt = cp.optString("saltRestriction", "None")
                    val weight = cp.optString("weightGainAlert", "Not specified")
                    "Dietary & Lifestyle Instructions from Care Plan:\n- Fluid Restriction: $fluid\n- Salt Restriction: $salt\n- Weight Gain Alert: $weight\n- Diagnosis: ${stay.primaryDiagnosis}"
                } else {
                    "Diagnosis: ${stay.primaryDiagnosis}\nPlan: ${stay.treatmentSummary}"
                }
            } catch (e: Exception) {
                "Care Plan: ${stay.dischargeNoteRaw}"
            }
        } ?: "No specific care plan instructions available in database."

        val contextInfo = """
PATIENT PROFILE:
Name: $patientName
Age: ${patient?.age ?: "72"}
Primary Condition: $condition
Allergies: ${patient?.allergies ?: "None reported"}
Risk Level: ${patient?.riskLevel ?: "STABLE"}

CARE PLAN SUMMARY:
$carePlanText

ACTIVE MEDICATIONS:
${if (medications.isEmpty()) "No active medications." else medications.joinToString("\n") { "- ${it.name} ${it.dosage} (${it.scheduledTime})" }}

UPCOMING APPOINTMENTS:
${if (appointments.isEmpty()) "No upcoming appointments." else appointments.joinToString("\n") { "- ${it.doctorName} (${it.specialty}) at ${it.dateTime}" }}

RECENT VITALS:
${if (vitals.isEmpty()) "No recent vitals." else vitals.joinToString("\n") { "- ${it.type}: ${it.value ?: "${it.systolic}/${it.diastolic}"} ${it.unit} (${it.recordedAt})" }}
        """.trimIndent()

        val fullSystemPrompt = """
${AgentPrompts.chatAgentSystem(patientName, condition)}

CURRENT PATIENT CONTEXT:
$contextInfo

${toolRegistry.toolManifest}

IMPORTANT:
1. Use the PATIENT CONTEXT provided above to answer questions. 
2. If the user asks about diet, exercise, or fluid, strictly follow the CARE PLAN SUMMARY above.
3. Do NOT tell the patient you cannot find their info if it is listed in the context above.
4. Only use tools for ACTIONS (recording symptoms, requesting appointments) or to fetch data NOT in the context.
""".trimIndent()

        val historyText = conversationHistory.takeLast(10).joinToString("\n") {
            "${it.role.uppercase()}: ${it.content}"
        }

        val userMsg = if (historyText.isNotEmpty()) {
            "CONVERSATION HISTORY:\n$historyText\n\nPATIENT'S NEW MESSAGE: $userMessage"
        } else {
            userMessage
        }

        val rawResponse = AgentOrchestratorHelper.runAgentWithTools(
            llmService, toolRegistry, gson,
            systemPrompt = fullSystemPrompt,
            userMessage = userMsg,
            maxToolRounds = 2 
        )

        var visibleResponse = rawResponse
            .stripToolCallBlocks()
            .replace(Regex("""TOOL_RESULTS:.*?(?=\n[A-Z]|\z)""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""SYMPTOM_RECORD:\s*\{.*?\}""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""APPOINTMENT_REQUEST:\s*\{.*?\}""", RegexOption.DOT_MATCHES_ALL), "")
            .trim()

        var recordedSymptom = false
        var requestedAppt = false
        var riskFlag = "STABLE"

        // Handle SYMPTOM_RECORD
        val symptomMatch = Regex("""SYMPTOM_RECORD:\s*(\{.*?\})""", RegexOption.DOT_MATCHES_ALL).find(rawResponse)
        if (symptomMatch != null) {
            try {
                val sJson = JSONObject(symptomMatch.groupValues[1])
                val symptoms = mutableListOf<String>()
                val arr = sJson.optJSONArray("symptoms")
                if (arr != null) {
                    for (i in 0 until arr.length()) symptoms.add(arr.getString(i))
                } else {
                    val single = sJson.optString("symptoms")
                    if (single.isNotEmpty()) symptoms.add(single)
                }
                
                val severity = sJson.optInt("severity", 1)
                toolRegistry.dispatch(ToolCall("record_symptom", mapOf(
                    "symptoms" to symptoms,
                    "severity" to severity.toDouble(),
                    "note" to "Reported via chat: $userMessage",
                )))
                recordedSymptom = true
                riskFlag = if (severity >= 3) "CRITICAL" else if (severity >= 2) "WARNING" else "STABLE"
            } catch (e: Exception) {
                Log.w(TAG, "Chat symptom parse error: ${e.message}")
            }
        }

        // Handle APPOINTMENT_REQUEST
        val apptMatch = Regex("""APPOINTMENT_REQUEST:\s*(\{.*?\})""", RegexOption.DOT_MATCHES_ALL).find(rawResponse)
        if (apptMatch != null) {
            try {
                val aJson = JSONObject(apptMatch.groupValues[1])
                toolRegistry.dispatch(ToolCall("request_appointment", mapOf(
                    "reason" to aJson.optString("reason", "Patient requested via chat"),
                    "preferredDate" to LocalDate.now().plusDays(1).format(dateFmt)
                )))
                requestedAppt = true
            } catch (e: Exception) {
                Log.w(TAG, "Chat appointment parse error: ${e.message}")
            }
        }

        if (visibleResponse.isEmpty()) {
            visibleResponse = "I'm here for you, $patientName. How can I help you today?"
        }

        return ChatResponse(
            text = visibleResponse,
            recordedSymptom = recordedSymptom,
            requestedAppointment = requestedAppt,
            riskFlag = riskFlag
        )
    }
}

private fun String.stripToolCallBlocks(): String {
    val result = StringBuilder()
    var i = 0
    while (i < length) {
        val markerIdx = indexOf("TOOL_CALL:", i)
        if (markerIdx == -1) {
            result.append(substring(i))
            break
        }
        result.append(substring(i, markerIdx))

        val braceStart = indexOf('{', markerIdx)
        if (braceStart == -1) {
            result.append(substring(markerIdx))
            break
        }

        var depth = 0
        var j = braceStart
        while (j < length) {
            when (this[j]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) { j++; break }
                }
            }
            j++
        }
        i = if (j < length && this[j] == '\n') j + 1 else j
    }
    return result.toString()
}
