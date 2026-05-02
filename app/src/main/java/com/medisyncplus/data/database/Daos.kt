package com.medisyncplus.data.database

import androidx.room.*
import com.medisyncplus.data.models.*
import kotlinx.coroutines.flow.Flow

// ─── Patient DAO ──────────────────────────────────────────────────────────────
@Dao
interface PatientDao {
    @Query("SELECT * FROM patients WHERE id = :id")
    fun getPatient(id: String): Flow<PatientEntity?>

    @Query("SELECT * FROM patients WHERE id = :id")
    suspend fun getPatientOnce(id: String): PatientEntity?

    @Query("SELECT * FROM patients WHERE email = :email LIMIT 1")
    suspend fun getPatientByEmail(email: String): PatientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(patient: PatientEntity)

    @Query("UPDATE patients SET riskLevel = :risk WHERE id = :id")
    suspend fun updateRiskLevel(id: String, risk: String)

    @Query("UPDATE patients SET adherenceScore = :score WHERE id = :id")
    suspend fun updateAdherenceScore(id: String, score: Int)

    @Query("UPDATE patients SET lastCheckedIn = :ts WHERE id = :id")
    suspend fun updateLastCheckedIn(id: String, ts: String)
}

// ─── Care Team DAO ────────────────────────────────────────────────────────────
@Dao
interface CareTeamDao {
    @Query("SELECT * FROM care_team WHERE id = :id")
    suspend fun getById(id: String): CareTeamEntity?

    @Query("SELECT * FROM care_team WHERE hospitalId = :hospitalId")
    fun getAllForHospital(hospitalId: String): Flow<List<CareTeamEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(member: CareTeamEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(members: List<CareTeamEntity>)
}

// ─── Medication DAO ───────────────────────────────────────────────────────────
@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications WHERE patientId = :patientId AND isActive = 1 ORDER BY scheduledTime ASC")
    fun getActiveMedications(patientId: String): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE patientId = :patientId AND isActive = 1 ORDER BY scheduledTime ASC")
    suspend fun getActiveMedicationsOnce(patientId: String): List<MedicationEntity>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getById(id: String): MedicationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(med: MedicationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(meds: List<MedicationEntity>)

    @Query("UPDATE medications SET isActive = 0 WHERE id = :id")
    suspend fun deactivate(id: String)
}

// ─── Medication Log DAO ───────────────────────────────────────────────────────
@Dao
interface MedicationLogDao {
    @Query("""
        SELECT * FROM medication_logs 
        WHERE patientId = :patientId AND scheduledDate = :date 
        ORDER BY scheduledTime ASC
    """)
    fun getLogsForDate(patientId: String, date: String): Flow<List<MedicationLogEntity>>

    @Query("""
        SELECT * FROM medication_logs 
        WHERE patientId = :patientId AND scheduledDate = :date 
        ORDER BY scheduledTime ASC
    """)
    suspend fun getLogsForDateOnce(patientId: String, date: String): List<MedicationLogEntity>

    @Query("""
        SELECT * FROM medication_logs 
        WHERE patientId = :patientId 
        ORDER BY scheduledDate DESC, scheduledTime DESC 
        LIMIT :limit
    """)
    fun getRecentLogs(patientId: String, limit: Int = 30): Flow<List<MedicationLogEntity>>

    @Query("""
        SELECT * FROM medication_logs 
        WHERE medicationId = :medId AND scheduledDate = :date
    """)
    suspend fun getLogForMedOnDate(medId: String, date: String): MedicationLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: MedicationLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(logs: List<MedicationLogEntity>)

    @Query("UPDATE medication_logs SET status = :status, takenAt = :takenAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, takenAt: String?)

    @Query("""
        SELECT COUNT(*) FROM medication_logs 
        WHERE patientId = :patientId AND status = 'MISSED' 
        AND scheduledDate >= :since
    """)
    suspend fun countMissedSince(patientId: String, since: String): Int

    @Query("""
        SELECT COUNT(*) FROM medication_logs 
        WHERE patientId = :patientId AND status = 'TAKEN' 
        AND scheduledDate >= :since
    """)
    suspend fun countTakenSince(patientId: String, since: String): Int

    @Query("""
        SELECT * FROM medication_logs 
        WHERE medicationId = :medId AND status = 'MISSED' 
        ORDER BY scheduledDate DESC LIMIT 5
    """)
    suspend fun getRecentMissesForMed(medId: String): List<MedicationLogEntity>

    @Query("UPDATE medication_logs SET agentFlagged = 1 WHERE id = :id")
    suspend fun flagByAgent(id: Long)
}

// ─── Vitals DAO ───────────────────────────────────────────────────────────────
@Dao
interface VitalDao {
    @Query("""
        SELECT * FROM vitals WHERE patientId = :patientId 
        ORDER BY recordedAt DESC
    """)
    fun getAllVitals(patientId: String): Flow<List<VitalEntity>>

    @Query("""
        SELECT * FROM vitals WHERE patientId = :patientId AND type = :type 
        ORDER BY recordedAt DESC LIMIT :limit
    """)
    fun getVitalsOfType(patientId: String, type: String, limit: Int = 14): Flow<List<VitalEntity>>

    @Query("""
        SELECT * FROM vitals WHERE patientId = :patientId AND type = :type 
        ORDER BY recordedAt DESC LIMIT 1
    """)
    suspend fun getLatestVital(patientId: String, type: String): VitalEntity?

    @Query("""
        SELECT * FROM vitals WHERE patientId = :patientId 
        ORDER BY recordedAt DESC LIMIT :limit
    """)
    suspend fun getRecentOnce(patientId: String, limit: Int = 10): List<VitalEntity>

    @Insert
    suspend fun insert(vital: VitalEntity): Long

    @Query("UPDATE vitals SET syncedToHospital = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Query("""
        SELECT * FROM vitals WHERE patientId = :patientId 
        AND type = 'weight' AND recordedAt >= :since 
        ORDER BY recordedAt ASC
    """)
    suspend fun getWeightSince(patientId: String, since: String): List<VitalEntity>

    @Query("""
        SELECT * FROM vitals WHERE patientId = :patientId 
        AND type = 'bp' AND recordedAt >= :since 
        ORDER BY recordedAt ASC
    """)
    suspend fun getBpSince(patientId: String, since: String): List<VitalEntity>
}

// ─── Symptom Report DAO ───────────────────────────────────────────────────────
@Dao
interface SymptomReportDao {
    @Query("""
        SELECT * FROM symptom_reports WHERE patientId = :patientId 
        ORDER BY reportedAt DESC
    """)
    fun getAllReports(patientId: String): Flow<List<SymptomReportEntity>>

    @Query("""
        SELECT * FROM symptom_reports WHERE patientId = :patientId 
        ORDER BY reportedAt DESC LIMIT :limit
    """)
    suspend fun getRecentOnce(patientId: String, limit: Int = 10): List<SymptomReportEntity>

    @Query("""
        SELECT * FROM symptom_reports WHERE patientId = :patientId 
        AND agentRiskLevel IN ('WARNING','CRITICAL') 
        ORDER BY reportedAt DESC LIMIT 5
    """)
    fun getHighRiskReports(patientId: String): Flow<List<SymptomReportEntity>>

    @Insert
    suspend fun insert(report: SymptomReportEntity): Long

    @Query("UPDATE symptom_reports SET clinicianReviewed = 1 WHERE id = :id")
    suspend fun markReviewed(id: Long)

    @Query("UPDATE symptom_reports SET syncedToHospital = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)
}

// ─── Appointment DAO ──────────────────────────────────────────────────────────
@Dao
interface AppointmentDao {
    @Query("""
        SELECT * FROM appointments WHERE patientId = :patientId 
        AND status NOT IN ('CANCELLED','COMPLETED') 
        ORDER BY dateTime ASC
    """)
    fun getUpcomingAppointments(patientId: String): Flow<List<AppointmentEntity>>

    @Query("""
        SELECT * FROM appointments WHERE patientId = :patientId 
        ORDER BY dateTime DESC
    """)
    fun getAllAppointments(patientId: String): Flow<List<AppointmentEntity>>

    @Query("""
        SELECT * FROM appointments WHERE patientId = :patientId 
        AND status NOT IN ('CANCELLED','COMPLETED') 
        ORDER BY dateTime ASC LIMIT 1
    """)
    suspend fun getNextAppointment(patientId: String): AppointmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(appt: AppointmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(appts: List<AppointmentEntity>)

    @Query("UPDATE appointments SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE appointments SET reminderSet = 1 WHERE id = :id")
    suspend fun markReminderSet(id: String)
}

// ─── Checklist DAO ────────────────────────────────────────────────────────────
@Dao
interface ChecklistDao {
    @Query("""
        SELECT * FROM checklist_tasks 
        WHERE patientId = :patientId AND scheduledDate = :date 
        ORDER BY CASE timeOfDay WHEN 'MORNING' THEN 1 WHEN 'AFTERNOON' THEN 2 ELSE 3 END
    """)
    fun getTasksForDate(patientId: String, date: String): Flow<List<ChecklistTaskEntity>>

    @Query("""
        SELECT * FROM checklist_tasks 
        WHERE patientId = :patientId AND scheduledDate = :date 
        ORDER BY CASE timeOfDay WHEN 'MORNING' THEN 1 WHEN 'AFTERNOON' THEN 2 ELSE 3 END
    """)
    suspend fun getTasksForDateOnce(patientId: String, date: String): List<ChecklistTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: ChecklistTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasks: List<ChecklistTaskEntity>)

    @Query("UPDATE checklist_tasks SET isDone = :isDone, completedAt = :ts WHERE id = :id")
    suspend fun updateStatus(id: String, isDone: Boolean, ts: String?)

    @Query("UPDATE checklist_tasks SET notificationSent = 1 WHERE id = :id")
    suspend fun markNotificationSent(id: String)

    @Query("""
        SELECT COUNT(*) FROM checklist_tasks 
        WHERE patientId = :patientId AND scheduledDate = :date AND isDone = 0
    """)
    suspend fun countPendingTasks(patientId: String, date: String): Int
}

// ─── Hospital Stay DAO ────────────────────────────────────────────────────────
@Dao
interface HospitalStayDao {
    @Query("SELECT * FROM hospital_stays WHERE patientId = :patientId ORDER BY admissionDate DESC")
    fun getStays(patientId: String): Flow<List<HospitalStayEntity>>

    @Query("SELECT * FROM hospital_stays WHERE patientId = :patientId ORDER BY dischargeDate DESC LIMIT 1")
    suspend fun getLatestStay(patientId: String): HospitalStayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stay: HospitalStayEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(stays: List<HospitalStayEntity>)
}

// ─── Care Team Reminders DAO ──────────────────────────────────────────────────
@Dao
interface CareTeamReminderDao {
    @Query("""
        SELECT * FROM care_team_reminders WHERE patientId = :patientId 
        ORDER BY createdAt DESC
    """)
    fun getReminders(patientId: String): Flow<List<CareTeamReminderEntity>>

    @Query("""
        SELECT * FROM care_team_reminders WHERE patientId = :patientId AND isRead = 0 
        ORDER BY createdAt DESC
    """)
    fun getUnreadReminders(patientId: String): Flow<List<CareTeamReminderEntity>>

    @Insert
    suspend fun insert(reminder: CareTeamReminderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(reminders: List<CareTeamReminderEntity>)

    @Query("UPDATE care_team_reminders SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE care_team_reminders SET isRead = 1 WHERE patientId = :patientId")
    suspend fun markAllRead(patientId: String)
}

// ─── EMR Proposals DAO ───────────────────────────────────────────────────────
@Dao
interface EmrProposalDao {
    @Query("""
        SELECT * FROM emr_proposals WHERE patientId = :patientId 
        ORDER BY createdAt DESC
    """)
    fun getAll(patientId: String): Flow<List<EmrProposalEntity>>

    @Query("""
        SELECT * FROM emr_proposals WHERE patientId = :patientId AND status = 'PENDING' 
        ORDER BY createdAt DESC
    """)
    fun getPending(patientId: String): Flow<List<EmrProposalEntity>>

    @Insert
    suspend fun insert(proposal: EmrProposalEntity): Long

    @Query("UPDATE emr_proposals SET status = :status, reviewedAt = :ts, reviewedBy = :by WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, ts: String, by: String)

    @Query("UPDATE emr_proposals SET status = 'SYNCED' WHERE id = :id")
    suspend fun markSynced(id: Long)
}

// ─── Chat Messages DAO ────────────────────────────────────────────────────────
@Dao
interface ChatMessageDao {
    @Query("""
        SELECT * FROM chat_messages WHERE patientId = :patientId
        ORDER BY timestamp ASC
    """)
    fun getMessages(patientId: String): Flow<List<ChatMessageEntity>>

    @Query("""
        SELECT * FROM chat_messages WHERE patientId = :patientId
        ORDER BY timestamp DESC LIMIT :limit
    """)
    suspend fun getRecentMessages(patientId: String, limit: Int = 20): List<ChatMessageEntity>

    @Insert
    suspend fun insert(msg: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE patientId = :patientId AND timestamp < :before")
    suspend fun pruneOldMessages(patientId: String, before: String)
}

// ─── Agent Audit Trail DAO ────────────────────────────────────────────────────
@Dao
interface AgentAuditTrailDao {
    @Query("""
        SELECT * FROM agent_audit_trail WHERE patientId = :patientId
        ORDER BY timestamp DESC
    """)
    fun getAll(patientId: String): Flow<List<AgentAuditTrailEntity>>

    @Query("""
        SELECT * FROM agent_audit_trail WHERE patientId = :patientId
        ORDER BY timestamp DESC LIMIT :limit
    """)
    suspend fun getRecent(patientId: String, limit: Int = 50): List<AgentAuditTrailEntity>

    @Query("""
        SELECT * FROM agent_audit_trail WHERE patientId = :patientId AND agentId = :agentId
        ORDER BY timestamp DESC LIMIT :limit
    """)
    suspend fun getByAgent(patientId: String, agentId: String, limit: Int = 20): List<AgentAuditTrailEntity>

    @Insert
    suspend fun insert(entry: AgentAuditTrailEntity): Long

    @Query("DELETE FROM agent_audit_trail WHERE patientId = :patientId AND timestamp < :before")
    suspend fun pruneOldEntries(patientId: String, before: String)
}

// ─── User Settings DAO ────────────────────────────────────────────────────────
@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM user_settings WHERE patientId = :patientId")
    fun getSettings(patientId: String): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE patientId = :patientId")
    suspend fun getSettingsOnce(patientId: String): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: UserSettingsEntity)
}

// ─── Hospital Info DAO ────────────────────────────────────────────────────────
@Dao
interface HospitalInfoDao {
    @Query("SELECT * FROM hospital_info WHERE hospitalId = :hospitalId LIMIT 1")
    fun getHospitalInfo(hospitalId: String): Flow<HospitalInfoEntity?>

    @Query("SELECT * FROM hospital_info WHERE hospitalId = :hospitalId LIMIT 1")
    suspend fun getHospitalInfoOnce(hospitalId: String): HospitalInfoEntity?

    @Query("SELECT * FROM hospital_info LIMIT 1")
    fun getFirst(): Flow<HospitalInfoEntity?>

    @Query("SELECT * FROM hospital_info LIMIT 1")
    suspend fun getFirstOnce(): HospitalInfoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(info: HospitalInfoEntity)
}
