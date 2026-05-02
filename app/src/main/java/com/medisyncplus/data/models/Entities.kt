package com.medisyncplus.data.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// ─── Patient Profile ────────────────────────────────────────────────────────
@Entity(tableName = "patients")
data class PatientEntity(
    @PrimaryKey val id: String,
    val name: String,
    val age: Int,
    val email: String = "",
    val password: String = "",
    val mrn: String,                    // Medical Record Number
    val ward: String,
    val dischargeDate: String,
    val primaryCondition: String,
    val attendingDoctorId: String,
    val riskLevel: String,              // STABLE | WARNING | CRITICAL
    val bloodType: String,
    val allergies: String,              // JSON array string
    val contactNumber: String,
    val emergencyContact: String,
    val emergencyContactName: String,
    val lastCheckedIn: String,
    val adherenceScore: Int = 100,
    val isActive: Boolean = true
)

// ─── Doctor / Nurse ─────────────────────────────────────────────────────────
@Entity(tableName = "care_team")
data class CareTeamEntity(
    @PrimaryKey val id: String,
    val name: String,
    val role: String,                   // DOCTOR | NURSE | SPECIALIST
    val specialty: String,
    val phone: String,
    val email: String,
    val hospitalId: String
)

// ─── Medications ─────────────────────────────────────────────────────────────
@Entity(
    tableName = "medications",
    foreignKeys = [ForeignKey(entity = PatientEntity::class, parentColumns = ["id"], childColumns = ["patientId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("patientId")]
)
data class MedicationEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val name: String,
    val dosage: String,
    val instruction: String,
    val scheduledTime: String,          // "HH:mm" 24h format
    val pillColorHex: String,
    val medicationClass: String,        // Diuretic, Beta Blocker, etc.
    val frequency: String,              // once_daily | twice_daily | thrice_daily
    val sideEffects: String,
    val prescribedBy: String,
    val startDate: String,
    val endDate: String?,
    val isActive: Boolean = true,
    val requiresFood: Boolean = false,
    val criticalMedication: Boolean = false  // Flag for cardiac-critical meds
)

// ─── Medication Logs (taken / missed records) ────────────────────────────────
@Entity(
    tableName = "medication_logs",
    foreignKeys = [ForeignKey(entity = MedicationEntity::class, parentColumns = ["id"], childColumns = ["medicationId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("medicationId"), Index("patientId")]
)
data class MedicationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: String,
    val patientId: String,
    val scheduledDate: String,          // "yyyy-MM-dd"
    val scheduledTime: String,
    val status: String,                 // TAKEN | MISSED | SKIPPED | UPCOMING
    val takenAt: String?,
    val missedStreak: Int = 0,
    val agentFlagged: Boolean = false,
    val notes: String = ""
)

// ─── Vitals / Condition Records ──────────────────────────────────────────────
@Entity(
    tableName = "vitals",
    foreignKeys = [ForeignKey(entity = PatientEntity::class, parentColumns = ["id"], childColumns = ["patientId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("patientId")]
)
data class VitalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: String,
    val type: String,                   // weight | bp | blood_sugar | pulse | spo2 | temperature
    val value: Float?,                  // For single-value vitals
    val systolic: Int?,                 // For blood pressure
    val diastolic: Int?,
    val unit: String,
    val flag: String,                   // normal | warning | critical
    val recordedAt: String,             // ISO timestamp
    val recordedBy: String,             // patient | nurse | device
    val syncedToHospital: Boolean = false,
    val notes: String = ""
)

// ─── Symptom Reports ──────────────────────────────────────────────────────────
@Entity(
    tableName = "symptom_reports",
    foreignKeys = [ForeignKey(entity = PatientEntity::class, parentColumns = ["id"], childColumns = ["patientId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("patientId")]
)
data class SymptomReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: String,
    val symptoms: String,               // JSON array of symptom names
    val severity: Int,                  // 1=mild, 2=moderate, 3=severe
    val additionalNote: String,
    val agentRiskLevel: String,         // STABLE | WARNING | CRITICAL
    val agentReason: String,
    val agentRecommendedAction: String,
    val escalatedImmediately: Boolean,
    val appointmentBooked: Boolean,
    val reportedAt: String,
    val source: String,                 // manual | chat | auto
    val syncedToHospital: Boolean = false,
    val clinicianReviewed: Boolean = false
)

// ─── Appointments ─────────────────────────────────────────────────────────────
@Entity(
    tableName = "appointments",
    foreignKeys = [ForeignKey(entity = PatientEntity::class, parentColumns = ["id"], childColumns = ["patientId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("patientId")]
)
data class AppointmentEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val doctorId: String,
    val doctorName: String,
    val specialty: String,
    val dateTime: String,               // ISO "yyyy-MM-dd HH:mm"
    val location: String,
    val appointmentType: String,        // FOLLOW_UP | ROUTINE | EMERGENCY | SPECIALIST
    val status: String,                 // CONFIRMED | PENDING | CANCELLED | COMPLETED | RESCHEDULED
    val notes: String,
    val reminderSet: Boolean = false,
    val reminderTimeMinutesBefore: Int = 60, // Default 1 hour before
    val patientPreNotes: String = "",   // Patient's pre-visit notes
    val createdAt: String,
    val source: String                  // doctor | patient_request | agent
)

// ─── Daily Checklist Tasks ────────────────────────────────────────────────────
@Entity(
    tableName = "checklist_tasks",
    foreignKeys = [ForeignKey(entity = PatientEntity::class, parentColumns = ["id"], childColumns = ["patientId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("patientId")]
)
data class ChecklistTaskEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val description: String,
    val actionKeyword: String = "Task",  // simplified word: e.g. "Weight", "Medication", "Walk"
    val timeOfDay: String,              // MORNING | AFTERNOON | EVENING
    val iconName: String,
    val requiresVitalInput: Boolean,
    val vitalType: String?,
    val scheduledDate: String,          // "yyyy-MM-dd"
    val isDone: Boolean = false,
    val completedAt: String? = null,
    val notificationSent: Boolean = false,
    val templateId: String,             // links to a task template for recurring generation
    val scheduledTime: String = "08:00"  // Default due time
)

// ─── Hospital Stay History (Inpatient records 住院记录) ──────────────────────
@Entity(
    tableName = "hospital_stays",
    foreignKeys = [ForeignKey(entity = PatientEntity::class, parentColumns = ["id"], childColumns = ["patientId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("patientId")]
)
data class HospitalStayEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val admissionDate: String,
    val dischargeDate: String?,
    val ward: String,
    val primaryDiagnosis: String,
    val secondaryDiagnoses: String,     // JSON array
    val treatmentSummary: String,
    val attendingDoctorId: String,
    val dischargeNoteRaw: String,       // Raw discharge note text
    val dischargeNoteParsed: String,    // JSON from DischargeInterpretationAgent
    val status: String                  // ACTIVE | DISCHARGED
)

// ─── Doctor/Nurse Reminders (care team → patient) ────────────────────────────
@Entity(
    tableName = "care_team_reminders",
    foreignKeys = [ForeignKey(entity = PatientEntity::class, parentColumns = ["id"], childColumns = ["patientId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("patientId")]
)
data class CareTeamReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: String,
    val fromCareTeamMemberId: String,
    val fromName: String,
    val role: String,
    val message: String,
    val priority: String,               // ROUTINE | URGENT | CRITICAL
    val createdAt: String,
    val isRead: Boolean = false,
    val requiresAction: Boolean = false,
    val actionType: String?             // TAKE_MEDICATION | BOOK_APPOINTMENT | LOG_VITAL
)

// ─── EMR Update Proposals (agent → hospital system) ─────────────────────────
@Entity(
    tableName = "emr_proposals",
    foreignKeys = [ForeignKey(entity = PatientEntity::class, parentColumns = ["id"], childColumns = ["patientId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("patientId")]
)
data class EmrProposalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: String,
    val proposalType: String,           // RISK_FLAG | SYMPTOM_RECORD | MEDICATION_UPDATE | CARE_PLAN_REVISION | APPOINTMENT
    val proposedChanges: String,        // JSON
    val justification: String,
    val urgency: String,                // ROUTINE | URGENT | IMMEDIATE
    val requiresClinicianReview: Boolean,
    val autoApprovable: Boolean,
    val status: String,                 // PENDING | APPROVED | REJECTED | SYNCED
    val createdAt: String,
    val reviewedAt: String? = null,
    val reviewedBy: String? = null,
    val agentId: String                 // which agent created this
)

// ─── Chat Message History ─────────────────────────────────────────────────────
@Entity(
    tableName = "chat_messages",
    foreignKeys = [ForeignKey(entity = PatientEntity::class, parentColumns = ["id"], childColumns = ["patientId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("patientId")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: String,
    val role: String,                   // user | assistant
    val content: String,
    val timestamp: String,
    val triggeredSymptomRecord: Boolean = false,
    val triggeredAppointmentRequest: Boolean = false,
    val agentUsed: String = "chat_agent"
)

// ─── Agent Audit Trail (decision log for transparency) ────────────────────────
@Entity(
    tableName = "agent_audit_trail",
    foreignKeys = [ForeignKey(entity = PatientEntity::class, parentColumns = ["id"], childColumns = ["patientId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("patientId"), Index("agentId"), Index("timestamp")]
)
data class AgentAuditTrailEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: String,
    val agentId: String,                // medicine_agent | symptom_agent | chat_agent | orchestrator | etc.
    val action: String,                 // ANALYSE | ALERT | EMR_PROPOSAL | TOOL_CALL | ESCALATE | APPOINTMENT
    val inputSummary: String,           // Brief description of what triggered this
    val outputSummary: String,          // Brief description of the result
    val riskLevel: String,              // STABLE | WARNING | CRITICAL
    val toolCallsMade: String,          // JSON array of tool call names
    val timestamp: String,
    val sessionId: String = "",         // Groups related agent calls
    val durationMs: Long = 0            // How long the agent took
)

// ─── User Settings ───────────────────────────────────────────────────────────
@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val patientId: String,
    val notificationMinutesBefore: Int = 60,
    val theme: String = "LIGHT",         // LIGHT | DARK
    val unitSystem: String = "METRIC",   // METRIC | IMPERIAL
    val tempUnit: String = "CELSIUS"     // CELSIUS | FAHRENHEIT
)

// ─── Hospital Info (doctor-side input, synced from hospital / mock API) ───────
@Entity(tableName = "hospital_info")
data class HospitalInfoEntity(
    @PrimaryKey val hospitalId: String,
    val hospitalName: String,
    val wardName: String,
    val wardPhone: String,
    val emergencyPhone: String = "999",
    val address: String = ""
)
