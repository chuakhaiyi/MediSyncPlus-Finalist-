package com.medisyncplus.data.mock

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * MockHospitalApi simulates the real hospital / EMR system API.
 * Replaces actual hospital HTTP calls during development and testing.
 * All data mirrors what the doctor's side would have entered.
 */
object MockHospitalApi {

    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val tsFmt   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private fun now()   = LocalDateTime.now().format(tsFmt)
    private fun today() = LocalDate.now().format(dateFmt)

    // ── Shared contract data classes ──────────────────────────────────────────

    data class HospitalPatientRecord(
        val mrn: String,
        val fullName: String,
        val ward: String,
        val attendingDoctor: String,
        val primaryDiagnosis: String,
        val dischargeDate: String,
        val riskLevel: String,
        val allergies: List<String>
    )

    data class HospitalMedicationOrder(
        val orderId: String,
        val medicationName: String,
        val dosage: String,
        val frequency: String,
        val scheduledTime: String,
        val prescribedBy: String,
        val startDate: String,
        val endDate: String?,
        val criticalMedication: Boolean,
        // UI support fields (often derived or part of the drug database)
        val instruction: String,
        val pillColorHex: String,
        val medicationClass: String,
        val sideEffects: String,
        val requiresFood: Boolean
    )

    data class HospitalAppointment(
        val appointmentId: String,
        val doctorName: String,
        val specialty: String,
        val dateTime: String,
        val location: String,
        val type: String,
        val status: String,
        val notes: String
    )

    data class HospitalVitalThreshold(
        val vitalType: String,
        val warningMin: Float?,
        val warningMax: Float?,
        val criticalMin: Float?,
        val criticalMax: Float?
    )

    data class HospitalCarePlanTask(
        val taskId: String,
        val description: String,
        val actionKeyword: String,
        val timeOfDay: String,
        val iconName: String,
        val requiresVitalInput: Boolean,
        val vitalType: String?,
        val scheduledTime: String,
        val templateId: String
    )

    data class EmrSyncAck(
        val proposalId: Long,
        val status: String,
        val message: String,
        val syncedAt: String
    )

    data class HospitalInfo(
        val hospitalId: String,
        val hospitalName: String,
        val wardName: String,
        val wardPhone: String,
        val emergencyPhone: String,
        val address: String
    )

    // ── Mock responses ────────────────────────────────────────────────────────

    /** GET /api/patients/{mrn} */
    fun getPatientRecord(mrn: String): HospitalPatientRecord = HospitalPatientRecord(
        mrn = mrn,
        fullName = "Margaret Chen",
        ward = "Cardiac Unit – Ward 4B",
        attendingDoctor = "Dr. Oscar",
        primaryDiagnosis = "Congestive Heart Failure (CHF)",
        dischargeDate = LocalDate.now().minusDays(5).format(dateFmt),
        riskLevel = "WARNING",
        allergies = listOf("Penicillin", "NSAIDs")
    )

    /** GET /api/patients/{mrn}/medications */
    fun getMedicationOrders(mrn: String): List<HospitalMedicationOrder> {
        val startDate = LocalDate.now().minusDays(5).format(dateFmt)
        return listOf(
            HospitalMedicationOrder(
                "MED001", "Furosemide", "40 mg", "once_daily", "08:00", "Dr. Oscar", startDate, null, true,
                "Take with water. Avoid evening doses to prevent nocturia.", "#2A7FBD", "Loop Diuretic", "Increased urination, dizziness, low potassium", false
            ),
            HospitalMedicationOrder(
                "MED002", "Lisinopril", "10 mg", "once_daily", "12:00", "Dr. Oscar", startDate, null, true,
                "May cause dizziness on first dose. Sit slowly before standing.", "#E8831A", "ACE Inhibitor", "Dry cough, dizziness, low blood pressure", true
            ),
            HospitalMedicationOrder(
                "MED003", "Carvedilol", "6.25 mg", "twice_daily", "08:00", "Dr. Oscar", startDate, null, true,
                "Take twice daily with food. Do not stop suddenly.", "#3A9E6F", "Beta Blocker", "Fatigue, slow heartbeat, dizziness", true
            ),
            HospitalMedicationOrder(
                "MED004", "Spironolactone", "25 mg", "once_daily", "08:00", "Dr. Oscar", startDate, null, true,
                "Take in the morning. Avoid high-potassium foods.", "#7B68EE", "Aldosterone Antagonist", "High potassium, breast tenderness, frequent urination", false
            ),
            HospitalMedicationOrder(
                "MED005", "Atorvastatin", "40 mg", "once_daily", "21:00", "Dr. Oscar", startDate, null, false,
                "Take at bedtime. Avoid grapefruit juice.", "#E74C8B", "Statin", "Muscle aches, liver enzyme changes", false
            )
        )
    }

    /** GET /api/patients/{mrn}/appointments */
    fun getAppointments(mrn: String): List<HospitalAppointment> = listOf(
        HospitalAppointment(
            appointmentId = "APT_MOCK_001",
            doctorName    = "Dr. Oscar",
            specialty     = "Cardiology",
            dateTime      = LocalDate.now().plusDays(2).format(dateFmt) + " 10:30",
            location      = "Cardiology Outpatient Clinic, Level 3",
            type          = "FOLLOW_UP",
            status        = "CONFIRMED",
            notes         = "Post-discharge follow-up. Bring home BP log and medication list."
        ),
        HospitalAppointment(
            appointmentId = "APT_MOCK_002",
            doctorName    = "Dr. Toh Bin Bin",
            specialty     = "Physiotherapy",
            dateTime      = LocalDate.now().plusDays(7).format(dateFmt) + " 14:00",
            location      = "Rehabilitation Centre, Level 1",
            type          = "SPECIALIST",
            status        = "CONFIRMED",
            notes         = "Cardiac rehabilitation session."
        )
    )

    /** GET /api/patients/{mrn}/vital-thresholds */
    fun getVitalThresholds(mrn: String): List<HospitalVitalThreshold> = listOf(
        HospitalVitalThreshold("weight",       60f,  70f,  55f,  80f),
        HospitalVitalThreshold("bp_systolic",  90f, 140f,  80f, 180f),
        HospitalVitalThreshold("bp_diastolic", 60f,  90f,  50f, 110f),
        HospitalVitalThreshold("pulse",        50f, 100f,  40f, 120f),
        HospitalVitalThreshold("spo2",         95f, 100f,  90f, 100f),
        HospitalVitalThreshold("blood_sugar",   4f,  7.8f,  3f,  11f),
        HospitalVitalThreshold("temperature",  36f, 37.5f, 35f, 38.5f)
    )

    /**
     * GET /api/hospital/{hospitalId}/info
     * Returns ward name, phone etc. that populate the SOS dialog.
     */
    fun getHospitalInfo(hospitalId: String): HospitalInfo = HospitalInfo(
        hospitalId    = hospitalId,
        hospitalName  = "ParkCity Medical Centre",
        wardName      = "Ward 4B",
        wardPhone     = "03-1234 5678",
        emergencyPhone = "999",
        address       = "No. 2, Jalan Intisari Perdana, Desa ParkCity, 52200 Kuala Lumpur"
    )

    /** POST /api/sync/emr-proposals — acknowledge submitted proposals */
    fun acknowledgeEmrProposal(proposalId: Long, proposalType: String): EmrSyncAck = EmrSyncAck(
        proposalId = proposalId,
        status     = "ACCEPTED",
        message    = "EMR proposal ($proposalType) received and queued for clinician review.",
        syncedAt   = now()
    )

    /**
     * GET /api/patients/{mrn}/care-plan/tasks
     * Doctor-prescribed care-plan tasks; the AI checklist generator reads
     * these to build today's checklist instead of using hardcoded templates.
     */
    fun getCarePlanTasks(mrn: String): List<HospitalCarePlanTask> = listOf(
        HospitalCarePlanTask("T_WEIGHT",   "Weigh yourself and record",                                   "Weight",     "MORNING",   "scale",      true,  "weight", "08:00", "T_WEIGHT"),
        HospitalCarePlanTask("T_MED_AM",   "Morning medications (Furosemide, Carvedilol, Spironolactone)","Medication", "MORNING",   "medication", false, null,     "08:30", "T_MED_AM"),
        HospitalCarePlanTask("T_WALK",     "10-minute gentle walk or light exercise",                      "Walk",       "MORNING",   "walk",       false, null,     "10:00", "T_WALK"),
        HospitalCarePlanTask("T_MED_NOON", "Lunch medication (Lisinopril)",                                "Medication", "AFTERNOON", "medication", false, null,     "13:00", "T_MED_NOON"),
        HospitalCarePlanTask("T_BP",       "Blood pressure check",                                         "Vitals",     "AFTERNOON", "bp",         true,  "bp",     "15:00", "T_BP"),
        HospitalCarePlanTask("T_MED_PM",   "Evening medication (Carvedilol 2nd dose, Atorvastatin)",       "Medication", "EVENING",   "medication", false, null,     "20:00", "T_MED_PM"),
        HospitalCarePlanTask("T_SWELLING", "Check for ankle swelling",                                     "Check",      "EVENING",   "check",      false, null,     "21:00", "T_SWELLING"),
        HospitalCarePlanTask("T_SYMPTOMS", "Report any new symptoms via app",                              "Report",     "EVENING",   "report",     false, null,     "22:00", "T_SYMPTOMS")
    )
}
