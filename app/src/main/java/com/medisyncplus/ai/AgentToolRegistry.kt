package com.medisyncplus.ai

import android.util.Log
import com.google.gson.Gson
import com.medisyncplus.data.models.*
import com.medisyncplus.data.repository.MediSyncRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class ToolResult(
    val toolName: String,
    val success: Boolean,
    val data: Any?,
    val error: String? = null
)

data class ToolCall(
    val name: String,
    val parameters: Map<String, Any?> = emptyMap()
)

@Singleton
class AgentToolRegistry @Inject constructor(
    private val repo: MediSyncRepository,
    private val gson: Gson
) {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val tsFmt   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private fun now()   = LocalDateTime.now().format(tsFmt)
    private fun today() = LocalDate.now().format(dateFmt)
    
    var patientId: String = "P001"

    companion object { private const val TAG = "ToolRegistry" }

    val toolManifest = """
AVAILABLE TOOLS:

1. get_patient_profile()
   → Returns patient demographics, condition, and risk level.

2. get_medications()
   → Returns list of active prescribed medications.

3. get_medication_logs(date?: "yyyy-MM-dd")
   → Returns medication adherence logs, including patient subjective "feeling" notes.

4. get_vitals(type?: "weight|bp|pulse", limit?: int)
   → Returns recent vital sign records.

5. get_care_plan()
   → Returns the discharge care plan, including dietary and fluid restrictions.

6. record_symptom(symptoms: string[], severity: int, note: string)
   → Records a patient's self-reported symptom.

7. request_appointment(reason: string, preferredDate: string)
   → Creates a pending appointment request.

To call a tool, you must respond with:
TOOL_CALL: {"name": "tool_name", "parameters": {"key": "value"}}
""".trimIndent()

    suspend fun dispatch(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "get_patient_profile" -> getPatientProfile()
                "get_medications"     -> getMedications()
                "get_medication_logs" -> getMedicationLogs(call.parameters)
                "get_vitals"          -> getVitals(call.parameters)
                "get_care_plan"       -> getCarePlan()
                "record_symptom"      -> recordSymptom(call.parameters)
                "request_appointment" -> requestAppointment(call.parameters)
                else -> ToolResult(call.name, false, null, "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            ToolResult(call.name, false, null, "Tool error: ${e.message}")
        }
    }

    private suspend fun getPatientProfile(): ToolResult {
        val patient = repo.getPatientOnce(patientId)
        return ToolResult("get_patient_profile", patient != null, patient)
    }

    private suspend fun getMedications(): ToolResult {
        val meds = repo.getActiveMedicationsOnce(patientId)
        return ToolResult("get_medications", true, meds)
    }

    private suspend fun getMedicationLogs(params: Map<String, Any?>): ToolResult {
        val date = params["date"] as? String ?: today()
        val logs = repo.getMedLogsForDateOnce(patientId, date)
        return ToolResult("get_medication_logs", true, logs)
    }

    private suspend fun getVitals(params: Map<String, Any?>): ToolResult {
        val type = params["type"] as? String
        val limit = (params["limit"] as? Double)?.toInt() ?: 5
        val vitals = repo.getRecentVitals(patientId, limit).let { list ->
            if (type != null) list.filter { it.type == type } else list
        }
        return ToolResult("get_vitals", true, vitals)
    }

    private suspend fun getCarePlan(): ToolResult {
        val stay = repo.getLatestStay(patientId)
            ?: return ToolResult("get_care_plan", false, null, "No hospital stay found in database")
        return ToolResult("get_care_plan", true, mapOf(
            "diagnosis" to stay.primaryDiagnosis,
            "instructions" to stay.dischargeNoteParsed
        ))
    }

    private suspend fun recordSymptom(params: Map<String, Any?>): ToolResult {
        val symptomsRaw = params["symptoms"]
        val symptoms = if (symptomsRaw is List<*>) {
            symptomsRaw.map { it.toString() }
        } else {
            listOf(symptomsRaw?.toString() ?: "")
        }

        val report = SymptomReportEntity(
            patientId = patientId,
            symptoms = gson.toJson(symptoms),
            severity = (params["severity"] as? Double)?.toInt() ?: 1,
            additionalNote = params["note"] as? String ?: "",
            agentRiskLevel = "STABLE",
            agentReason = "Recorded via chat tool call",
            agentRecommendedAction = "Monitor symptoms",
            escalatedImmediately = false,
            appointmentBooked = false,
            reportedAt = now(),
            source = "chat"
        )
        val id = repo.insertSymptomReport(report)
        return ToolResult("record_symptom", true, mapOf("reportId" to id))
    }

    private suspend fun requestAppointment(params: Map<String, Any?>): ToolResult {
        val appt = AppointmentEntity(
            id = "REQ_${System.currentTimeMillis()}", patientId = patientId,
            doctorId = "D001", doctorName = "Attending Physician",
            specialty = "General",
            dateTime = (params["preferredDate"] as? String ?: today()) + " 09:00",
            location = "Main Clinic",
            appointmentType = "FOLLOW_UP", status = "PENDING",
            notes = params["reason"] as? String ?: "Requested via chat",
            createdAt = now(), source = "chat"
        )
        repo.upsertAppointment(appt)
        return ToolResult("request_appointment", true, mapOf("appointmentId" to appt.id))
    }
}
