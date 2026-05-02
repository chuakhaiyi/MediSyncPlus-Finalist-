package com.medisyncplus.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisyncplus.ai.*
import com.medisyncplus.ai.agents.*
import com.medisyncplus.data.database.DatabaseSeeder
import com.medisyncplus.data.database.hospitalInfo
import com.medisyncplus.data.models.*
import com.medisyncplus.data.repository.MediSyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

data class UiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val toastMessage: String? = null,
    val emergencyAlert: String? = null,
    val showFeelingDialogFor: MedicationEntity? = null,
    val isLoggedIn: Boolean = false
)

data class HomeUiState(
    val patient: PatientEntity? = null,
    val nextMedication: MedicationEntity? = null,
    val nextMedLog: MedicationLogEntity? = null,
    val todayTasksDone: Int = 0,
    val todayTasksTotal: Int = 0,
    val todayTasks: List<ChecklistTaskEntity> = emptyList(),
    val auditEntries: List<AgentAuditTrailEntity> = emptyList(),
    val unreadReminders: Int = 0,
    val pendingProposals: Int = 0,
    val agentStatus: MedicineAgentResult? = null,
    val riskTrajectory: RiskTrajectory? = null,
    val followUpResult: FollowUpResult? = null,
    val morningSummary: String = "",
    val predictedHealthStatus: String = "Normal",
    val predictiveInsight: String = ""
)

data class MedicationUiState(
    val medications: List<MedicationEntity> = emptyList(),
    val todayLogs: List<MedicationLogEntity> = emptyList(),
    val adherenceResult: MedicineAgentResult? = null
)

data class VitalsUiState(
    val latestWeight: VitalEntity? = null,
    val latestBp: VitalEntity? = null,
    val latestBloodSugar: VitalEntity? = null,
    val latestPulse: VitalEntity? = null,
    val latestSpo2: VitalEntity? = null,
    val latestTemperature: VitalEntity? = null,
    val weightHistory: List<VitalEntity> = emptyList(),
    val bpHistory: List<VitalEntity> = emptyList(),
    val bloodSugarHistory: List<VitalEntity> = emptyList(),
    val spo2History: List<VitalEntity> = emptyList(),
    val agentAnalysis: String = ""
)

data class SymptomUiState(
    val recentReports: List<SymptomReportEntity> = emptyList(),
    val analysisResult: SymptomAnalysisResult? = null,
    val isAnalysing: Boolean = false
)

data class ChecklistUiState(
    val tasks: List<ChecklistTaskEntity> = emptyList(),
    val completedCount: Int = 0
)

data class AppointmentUiState(
    val upcoming: List<AppointmentEntity> = emptyList(),
    val all: List<AppointmentEntity> = emptyList(),
    val hospitalStays: List<HospitalStayEntity> = emptyList(),
    val careTeamReminders: List<CareTeamReminderEntity> = emptyList()
)

data class ChatUiState(
    val messages: List<ChatMessageEntity> = emptyList(),
    val isThinking: Boolean = false,
    val lastRiskFlag: String = "STABLE"
)

data class AuditUiState(
    val entries: List<AgentAuditTrailEntity> = emptyList()
)

data class SettingsUiState(
    val settings: UserSettingsEntity? = null
)

data class HospitalInfoUiState(
    val hospitalInfo: HospitalInfoEntity? = null
)

@HiltViewModel
class MediSyncViewModel @Inject constructor(
    private val repo: MediSyncRepository,
    private val orchestrator: AgentOrchestrator,
    private val toolRegistry: AgentToolRegistry
) : ViewModel() {

    private val _currentPatientId = MutableStateFlow<String?>(null)
    private val patientId: String get() = _currentPatientId.value ?: DatabaseSeeder.PATIENT_ID

    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val tsFmt   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private fun now()   = LocalDateTime.now().format(tsFmt)
    private fun today() = LocalDate.now().format(dateFmt)

    private val _ui     = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _home   = MutableStateFlow(HomeUiState())
    val home: StateFlow<HomeUiState> = _home.asStateFlow()

    private val _meds   = MutableStateFlow(MedicationUiState())
    val meds: StateFlow<MedicationUiState> = _meds.asStateFlow()

    private val _vitals = MutableStateFlow(VitalsUiState())
    val vitals: StateFlow<VitalsUiState> = _vitals.asStateFlow()

    private val _symptom = MutableStateFlow(SymptomUiState())
    val symptom: StateFlow<SymptomUiState> = _symptom.asStateFlow()

    private val _checklist = MutableStateFlow(ChecklistUiState())
    val checklist: StateFlow<ChecklistUiState> = _checklist.asStateFlow()

    private val _appointments = MutableStateFlow(AppointmentUiState())
    val appointments: StateFlow<AppointmentUiState> = _appointments.asStateFlow()

    private val _chat = MutableStateFlow(ChatUiState())
    val chat: StateFlow<ChatUiState> = _chat.asStateFlow()

    private val _audit = MutableStateFlow(AuditUiState())
    val audit: StateFlow<AuditUiState> = _audit.asStateFlow()

    private val _settings = MutableStateFlow(SettingsUiState())
    val settings: StateFlow<SettingsUiState> = _settings.asStateFlow()

    private val _hospitalInfo = MutableStateFlow(HospitalInfoUiState())
    val hospitalInfo: StateFlow<HospitalInfoUiState> = _hospitalInfo.asStateFlow()

    init {
        seedAndLoad()
    }

    private fun seedAndLoad() {
        viewModelScope.launch(Dispatchers.IO) {
            val id = DatabaseSeeder.PATIENT_ID
            toolRegistry.patientId = id
            
            val existingPatient = repo.getPatientOnce(id)
            val stays = repo.getHospitalStays(id).first()
            val appts = repo.getAllAppointments(id).first()
            
            if (existingPatient == null || existingPatient.primaryCondition.isBlank() || stays.isEmpty() || appts.isEmpty()) {
                Log.i("MediSync", "Critical data missing. Seeding database...")
                seedDatabase()
            } else {
                Log.i("MediSync", "Database check passed. Patient: ${existingPatient.name}")
            }

            if (repo.getHospitalInfoOnce() == null) {
                repo.upsertHospitalInfo(DatabaseSeeder.hospitalInfo())
            }
            generateAiChecklistForToday(id)
            observeAll(id)
            runMorningChecks()
        }
    }

    private suspend fun seedDatabase() {
        repo.upsertPatient(DatabaseSeeder.patient())
        repo.upsertCareTeam(DatabaseSeeder.careTeam())
        repo.upsertMedications(DatabaseSeeder.medications())
        repo.upsertMedLogs(DatabaseSeeder.medicationLogs())
        DatabaseSeeder.vitals().forEach { repo.insertVital(it) }
        repo.upsertAppointments(DatabaseSeeder.appointments())
        repo.upsertHospitalStays(DatabaseSeeder.hospitalStays())
        if (repo.getHospitalInfoOnce() == null) {
            repo.upsertHospitalInfo(DatabaseSeeder.hospitalInfo())
        }
        repo.upsertReminders(DatabaseSeeder.careTeamReminders())
        DatabaseSeeder.symptomReports().forEach { repo.insertSymptomReport(it) }
        DatabaseSeeder.emrProposals().forEach { repo.insertEmrProposal(it) }
    }

    private suspend fun generateAiChecklistForToday(patientId: String) {
        val date = today()
        val existing = repo.getChecklistForDateOnce(patientId, date)
        if (existing.isNotEmpty()) return

        try {
            val patient    = repo.getPatientOnce(patientId) ?: return
            val meds       = repo.getActiveMedicationsOnce(patientId)
            val carePlan   = com.medisyncplus.data.mock.MockHospitalApi.getCarePlanTasks(patient.mrn)

            val medSummary = meds.groupBy { it.scheduledTime }.entries.joinToString("\n") { (time, medList) ->
                "  $time: ${medList.joinToString(", ") { "${it.name} ${it.dosage}" }}"
            }
            val prompt = """
You are a clinical AI assistant. Generate today's care tasks for a post-discharge patient as a JSON array.

Patient: ${patient.name}, Age ${patient.age}
Condition: ${patient.primaryCondition}
Risk Level: ${patient.riskLevel}

Active Medications and Schedule:
$medSummary

Doctor-prescribed care plan tasks:
${carePlan.joinToString("\n") { "  - [${it.timeOfDay}] ${it.description} (${it.scheduledTime})" }}

Return ONLY a valid JSON array where each element has:
{
  "id": "CHK_${date}_<number>",
  "description": "<task description>",
  "timeOfDay": "<MORNING|AFTERNOON|EVENING>",
  "iconName": "<scale|medication|walk|bp|check|report|pill>",
  "requiresVitalInput": <true|false>,
  "vitalType": "<weight|bp|blood_sugar|null>",
  "scheduledTime": "<HH:mm>",
  "templateId": "<templateId from care plan or T_CUSTOM_N>"
}
""".trimIndent()

            val llmResponse = try {
                orchestrator.callLlmRaw(
                    systemPrompt = "You are a clinical AI assistant. Output only valid JSON.",
                    userPrompt = prompt
                )
            } catch (e: Exception) {
                null
            }

            val tasks: List<ChecklistTaskEntity> = if (llmResponse != null) {
                try {
                    val clean = llmResponse.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    val gson = com.google.gson.Gson()
                    val type = object : com.google.gson.reflect.TypeToken<List<Map<String, Any?>>>() {}.type
                    val parsed: List<Map<String, Any?>> = gson.fromJson(clean, type)
                    parsed.mapIndexed { i, m ->
                        ChecklistTaskEntity(
                            id                 = (m["id"] as? String) ?: "CHK_${date}_${i+1}",
                            patientId          = patientId,
                            description        = (m["description"] as? String) ?: "",
                            timeOfDay          = (m["timeOfDay"] as? String) ?: "MORNING",
                            iconName           = (m["iconName"] as? String) ?: "check",
                            requiresVitalInput = (m["requiresVitalInput"] as? Boolean) ?: false,
                            vitalType          = m["vitalType"] as? String,
                            scheduledDate      = date,
                            templateId         = (m["templateId"] as? String) ?: "T_CUSTOM_${i+1}",
                            scheduledTime      = (m["scheduledTime"] as? String) ?: "08:00"
                        )
                    }
                } catch (e: Exception) {
                    carePlanToTasks(carePlan, patientId, date)
                }
            } else {
                carePlanToTasks(carePlan, patientId, date)
            }

            repo.upsertChecklistTasks(tasks)

            repo.insertAuditTrail(AgentAuditTrailEntity(
                patientId = patientId,
                agentId = "checklist_agent",
                action = "GENERATE_CHECKLIST",
                inputSummary = "Generating checklist for $date",
                outputSummary = "Generated ${tasks.size} tasks",
                riskLevel = "STABLE",
                toolCallsMade = "[]",
                timestamp = now()
            ))
        } catch (e: Exception) {
            Log.e("MediSync", "Error generating checklist: ${e.message}")
        }
    }

    private fun carePlanToTasks(
        carePlan: List<com.medisyncplus.data.mock.MockHospitalApi.HospitalCarePlanTask>,
        patientId: String,
        date: String
    ): List<ChecklistTaskEntity> {
        return carePlan.mapIndexed { i, t ->
            ChecklistTaskEntity(
                id = "CHK_${date}_${i + 1}",
                patientId = patientId,
                description = t.description,
                timeOfDay = t.timeOfDay,
                iconName = t.iconName,
                requiresVitalInput = t.requiresVitalInput,
                vitalType = t.vitalType,
                scheduledDate = date,
                templateId = t.templateId,
                scheduledTime = t.scheduledTime
            )
        }
    }

    private fun observeAll(pId: String) {
        viewModelScope.launch {
            repo.getPatient(pId).collect { p ->
                _home.update { it.copy(patient = p) }
            }
        }
        viewModelScope.launch {
            repo.getActiveMedications(pId).collect { list ->
                _meds.update { it.copy(medications = list) }
            }
        }
        viewModelScope.launch {
            repo.getMedLogsForDate(pId, today()).collect { list ->
                _meds.update { it.copy(todayLogs = list) }
                updateNextMedication(list)
            }
        }
        viewModelScope.launch {
            repo.getChecklistForDate(pId, today()).collect { list ->
                _checklist.update { it.copy(tasks = list, completedCount = list.count { it.isDone }) }
                _home.update { it.copy(
                    todayTasks = list,
                    todayTasksDone = list.count { it.isDone },
                    todayTasksTotal = list.size
                ) }
            }
        }
        viewModelScope.launch {
            repo.getAllVitals(pId).collect { list ->
                val weight = list.filter { it.type == "weight" }
                val bp = list.filter { it.type == "bp" }
                val sugar = list.filter { it.type == "blood_sugar" }
                val pulse = list.filter { it.type == "pulse" }
                val spo2 = list.filter { it.type == "spo2" }
                val temp = list.filter { it.type == "temperature" }
                
                _vitals.update { it.copy(
                    latestWeight = weight.maxByOrNull { it.recordedAt },
                    latestBp = bp.maxByOrNull { it.recordedAt },
                    latestBloodSugar = sugar.maxByOrNull { it.recordedAt },
                    latestPulse = pulse.maxByOrNull { it.recordedAt },
                    latestSpo2 = spo2.maxByOrNull { it.recordedAt },
                    latestTemperature = temp.maxByOrNull { it.recordedAt },
                    weightHistory = weight.sortedByDescending { it.recordedAt },
                    bpHistory = bp.sortedByDescending { it.recordedAt },
                    bloodSugarHistory = sugar.sortedByDescending { it.recordedAt },
                    spo2History = spo2.sortedByDescending { it.recordedAt }
                ) }
            }
        }
        viewModelScope.launch {
            repo.getAllSymptomReports(pId).collect { list ->
                _symptom.update { it.copy(recentReports = list.sortedByDescending { it.reportedAt }) }
            }
        }
        viewModelScope.launch {
            repo.getUpcomingAppointments(pId).collect { list ->
                _appointments.update { it.copy(upcoming = list) }
            }
        }
        viewModelScope.launch {
            repo.getAllAppointments(pId).collect { list ->
                _appointments.update { it.copy(all = list) }
            }
        }
        viewModelScope.launch {
            repo.getHospitalStays(pId).collect { list ->
                _appointments.update { it.copy(hospitalStays = list.sortedByDescending { it.admissionDate ?: "" }) }
            }
        }
        viewModelScope.launch {
            repo.getReminders(pId).collect { list ->
                _appointments.update { it.copy(careTeamReminders = list.sortedByDescending { it.createdAt }) }
                _home.update { it.copy(unreadReminders = list.count { !it.isRead }) }
            }
        }
        viewModelScope.launch {
            repo.getAuditTrail(pId).collect { list ->
                _audit.update { it.copy(entries = list.sortedByDescending { it.timestamp }) }
                _home.update { it.copy(auditEntries = list.sortedByDescending { it.timestamp }.take(5)) }
            }
        }
        viewModelScope.launch {
            repo.getChatMessages(pId).collect { list ->
                _chat.update { it.copy(messages = list) }
            }
        }
        viewModelScope.launch {
            repo.getHospitalInfo().collect { info ->
                _hospitalInfo.update { it.copy(hospitalInfo = info) }
            }
        }
        viewModelScope.launch {
            repo.getSettings(pId).collect { s ->
                _settings.update { it.copy(settings = s) }
            }
        }
    }

    private fun updateNextMedication(logs: List<MedicationLogEntity>) {
        viewModelScope.launch {
            val upcoming = logs.filter { it.status == "UPCOMING" }.sortedBy { it.scheduledTime }
            if (upcoming.isNotEmpty()) {
                val nextLog = upcoming.first()
                val med = repo.getMedicationById(nextLog.medicationId)
                _home.update { it.copy(nextMedication = med, nextMedLog = nextLog) }
            } else {
                _home.update { it.copy(nextMedication = null, nextMedLog = null) }
            }
        }
    }

    private fun runMorningChecks() {
        viewModelScope.launch {
            val result = orchestrator.runFullMorningCheck(patientId)
            _home.update { it.copy(
                morningSummary = result.summary,
                predictedHealthStatus = result.overallRisk,
                riskTrajectory = result.riskTrajectory,
                followUpResult = result.followUpResult
            ) }
            
            if (result.overallRisk == "CRITICAL") {
                triggerEmergencyAlert("Health Check Alert: ${result.summary}")
            }
        }
    }

    fun login(email: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val p = repo.getPatientByEmail(email)
            if (p != null && p.password == password) {
                _ui.update { it.copy(isLoggedIn = true) }
                _currentPatientId.value = p.id
                toolRegistry.patientId = p.id
                observeAll(p.id)
                onSuccess()
            } else {
                onError("Invalid credentials")
            }
        }
    }

    fun logout() {
        _ui.update { it.copy(isLoggedIn = false) }
        _currentPatientId.value = null
    }

    fun clearToast() {
        _ui.update { it.copy(toastMessage = null) }
    }

    fun dismissEmergencyAlert() {
        _ui.update { it.copy(emergencyAlert = null) }
    }

    fun triggerEmergencyAlert(message: String) {
        _ui.update { it.copy(emergencyAlert = message) }
        // Audit the alert
        viewModelScope.launch {
            repo.insertAuditTrail(AgentAuditTrailEntity(
                patientId = patientId,
                agentId = "orchestrator",
                action = "EMERGENCY_POPUP",
                inputSummary = "System triggered emergency UI",
                outputSummary = message,
                riskLevel = "CRITICAL",
                toolCallsMade = "[]",
                timestamp = now()
            ))
        }
    }

    fun toggleTask(id: String, done: Boolean) {
        viewModelScope.launch {
            repo.toggleTask(id, done, patientId)
            
            if (done) {
                // If it was a medication task, trigger feeling dialog
                val tasks = repo.getChecklistForDateOnce(patientId, today())
                val task = tasks.find { it.id == id }
                if (task?.templateId?.startsWith("T_MED") == true) {
                    val meds = repo.getActiveMedicationsOnce(patientId)
                    val medsInTaskNames = task.description.substringAfter("(", "").substringBefore(")", "").split(",").map { it.trim() }
                    val firstMed = meds.find { med -> medsInTaskNames.any { it.equals(med.name, ignoreCase = true) } }
                    if (firstMed != null) {
                        _ui.update { it.copy(showFeelingDialogFor = firstMed) }
                    }
                }
            }
        }
    }

    fun markMedicationTaken(med: MedicationEntity) {
        viewModelScope.launch {
            val log = repo.getLogForMedOnDate(med.id, today())
            if (log != null) {
                repo.markMedicationTaken(log.id, patientId)
                val analysis = orchestrator.runMedicineAgent()
                _meds.update { it.copy(adherenceResult = analysis) }
                
                if (analysis.riskFromAdherence == "CRITICAL") {
                    triggerEmergencyAlert(analysis.alertMessage)
                }

                // Trigger feeling dialog
                _ui.update { it.copy(showFeelingDialogFor = med) }
            }
        }
    }

    fun unmarkMedicationTaken(med: MedicationEntity) {
        viewModelScope.launch {
            val log = repo.getLogForMedOnDate(med.id, today())
            if (log != null) {
                repo.unmarkMedicationTaken(log.id, patientId)
                val analysis = orchestrator.runMedicineAgent()
                _meds.update { it.copy(adherenceResult = analysis) }
            }
        }
    }

    fun recordWeight(value: Float) = addVital("weight", value, unit = "kg")
    fun recordBp(sys: Int, dia: Int) = addVital("bp", null, sys, dia, "mmHg")
    fun recordBloodSugar(value: Float) = addVital("blood_sugar", value, unit = "mmol/L")
    fun recordPulse(value: Float) = addVital("pulse", value, unit = "bpm")
    fun recordSpo2(value: Float) = addVital("spo2", value, unit = "%")
    fun recordTemperature(value: Float) = addVital("temperature", value, unit = "°C")

    private fun addVital(type: String, value: Float?, systolic: Int? = null, diastolic: Int? = null, unit: String) {
        viewModelScope.launch {
            val vital = VitalEntity(
                patientId = patientId,
                type = type,
                value = value,
                systolic = systolic,
                diastolic = diastolic,
                unit = unit,
                recordedAt = now(),
                recordedBy = "patient",
                flag = "normal"
            )
            repo.insertVital(vital)
        }
    }

    fun submitSymptomReport(symptoms: List<String>, severity: Int, note: String) {
        viewModelScope.launch {
            _symptom.update { it.copy(isAnalysing = true) }
            val analysis = orchestrator.runSymptomAgent(symptoms, severity, note, patientId)
            
            val report = SymptomReportEntity(
                patientId = patientId,
                symptoms = com.google.gson.Gson().toJson(symptoms),
                severity = severity,
                additionalNote = note,
                agentRiskLevel = analysis.riskLevel,
                agentReason = analysis.riskReason,
                agentRecommendedAction = analysis.recommendedAction,
                escalatedImmediately = analysis.escalateImmediately,
                appointmentBooked = analysis.bookAppointment,
                reportedAt = now(),
                source = "manual"
            )
            repo.insertSymptomReport(report)
            _symptom.update { it.copy(analysisResult = analysis, isAnalysing = false) }
            
            if (analysis.riskLevel == "CRITICAL") {
                triggerEmergencyAlert("Clinical Risk Alert: ${analysis.riskReason}")
            }
        }
    }

    fun requestAppointment(reason: String, doctorId: String, type: String, notes: String) {
        viewModelScope.launch {
            val appt = AppointmentEntity(
                id = "REQ_${UUID.randomUUID()}",
                patientId = patientId,
                doctorId = doctorId,
                doctorName = "Pending",
                specialty = "",
                dateTime = today() + " 00:00",
                location = "Main Clinic",
                appointmentType = type,
                status = "PENDING",
                notes = notes,
                createdAt = now(),
                source = "patient_request"
            )
            repo.upsertAppointment(appt)
        }
    }

    fun rescheduleAppointment(id: String, newDate: String) {
        viewModelScope.launch {
            repo.updateAppointmentStatus(id, "RESCHEDULED")
        }
    }

    fun cancelAppointmentRequest(id: String) {
        viewModelScope.launch {
            repo.updateAppointmentStatus(id, "CANCELLED")
        }
    }

    fun sendChatMessage(text: String) {
        viewModelScope.launch {
            val userMsg = ChatMessageEntity(
                patientId = patientId,
                role = "user",
                content = text,
                timestamp = now()
            )
            repo.insertChatMessage(userMsg)
            _chat.update { it.copy(isThinking = true) }
            val history = repo.getRecentChatMessages(patientId, 10)
            val response = orchestrator.runChatAgent(text, patientId)
            val assistantMsg = ChatMessageEntity(
                patientId = patientId,
                role = "assistant",
                content = response.text,
                timestamp = now(),
                triggeredSymptomRecord = response.recordedSymptom,
                triggeredAppointmentRequest = response.requestedAppointment
            )
            repo.insertChatMessage(assistantMsg)
            _chat.update { it.copy(isThinking = false, lastRiskFlag = response.riskFlag) }
            
            if (response.riskFlag == "CRITICAL") {
                triggerEmergencyAlert("AI Analysis Alert: Emergency help may be required.")
            }
        }
    }

    fun submitFeelingAfterMedication(medication: MedicationEntity, feeling: String?) {
        _ui.update { it.copy(showFeelingDialogFor = null) }
        if (feeling != null) {
            viewModelScope.launch {
                // 1. Store in medication log notes
                val log = repo.getLogForMedOnDate(medication.id, today())
                if (log != null) {
                    repo.upsertMedLog(log.copy(notes = feeling))
                }
                
                // 2. AI analysis for clinical significance
                try {
                    val analysis = orchestrator.runSymptomAgent(
                        symptoms = emptyList(),
                        severity = 2,
                        note = "Subjective feeling reported after taking ${medication.name}: $feeling",
                        patientId = patientId
                    )
                    
                    if (analysis.riskLevel == "CRITICAL" || analysis.riskLevel == "WARNING") {
                        val report = SymptomReportEntity(
                            patientId = patientId,
                            symptoms = "[]",
                            severity = 2,
                            additionalNote = "Reported after ${medication.name}: $feeling",
                            agentRiskLevel = analysis.riskLevel,
                            agentReason = analysis.riskReason,
                            agentRecommendedAction = analysis.recommendedAction,
                            escalatedImmediately = analysis.escalateImmediately,
                            appointmentBooked = analysis.bookAppointment,
                            reportedAt = now(),
                            source = "medication_feeling"
                        )
                        repo.insertSymptomReport(report)
                        _ui.update { it.copy(toastMessage = "Agent flagged a concern: ${analysis.riskReason}") }
                    }
                    
                    if (analysis.riskLevel == "CRITICAL") {
                        triggerEmergencyAlert("Medication Response Alert: ${analysis.riskReason}")
                    }
                    
                    // Audit trail entry
                    repo.insertAuditTrail(AgentAuditTrailEntity(
                        patientId = patientId,
                        agentId = "symptom_agent",
                        action = "ANALYSE_FEELING",
                        inputSummary = "User feeling after ${medication.name}: $feeling",
                        outputSummary = "Analysis: ${analysis.riskLevel}. ${analysis.riskReason}",
                        riskLevel = analysis.riskLevel,
                        toolCallsMade = "[]",
                        timestamp = now()
                    ))
                } catch (e: Exception) {
                    Log.e("MediSync", "Feeling analysis failed", e)
                }

                Log.i("MediSync", "Patient feeling after ${medication.name}: $feeling")
            }
        }
    }

    fun updateSettings(s: UserSettingsEntity) {
        viewModelScope.launch {
            repo.upsertSettings(s)
        }
    }

    fun updateHospitalInfo(info: HospitalInfoEntity) {
        viewModelScope.launch {
            repo.upsertHospitalInfo(info)
        }
    }

    fun updateEmergencyContact(name: String, phone: String) {
        viewModelScope.launch {
            val patient = repo.getPatientOnce(patientId)
            if (patient != null) {
                repo.upsertPatient(patient.copy(
                    emergencyContactName = name,
                    emergencyContact = phone
                ))
                _ui.update { it.copy(toastMessage = "Emergency contact updated.") }
            }
        }
    }

    fun testAiConnection() {
        viewModelScope.launch {
            val result = orchestrator.testConnection()
            _ui.update { it.copy(toastMessage = result) }
        }
    }

    fun markReminderRead(id: Long) {
        viewModelScope.launch {
            repo.markReminderRead(id)
        }
    }

    fun dismissToast() {
        _ui.update { it.copy(toastMessage = null) }
    }
}
