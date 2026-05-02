package com.medisyncplus.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.medisyncplus.data.models.*

class Converters {
    @TypeConverter fun fromList(v: List<String>): String = v.joinToString("|")
    @TypeConverter fun toList(v: String): List<String> = if (v.isEmpty()) emptyList() else v.split("|")
}

@Database(
    entities = [
        PatientEntity::class,
        CareTeamEntity::class,
        MedicationEntity::class,
        MedicationLogEntity::class,
        VitalEntity::class,
        SymptomReportEntity::class,
        AppointmentEntity::class,
        ChecklistTaskEntity::class,
        HospitalStayEntity::class,
        CareTeamReminderEntity::class,
        EmrProposalEntity::class,
        ChatMessageEntity::class,
        AgentAuditTrailEntity::class,
        UserSettingsEntity::class,
        HospitalInfoEntity::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MediSyncDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun careTeamDao(): CareTeamDao
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationLogDao(): MedicationLogDao
    abstract fun vitalDao(): VitalDao
    abstract fun symptomReportDao(): SymptomReportDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun hospitalStayDao(): HospitalStayDao
    abstract fun careTeamReminderDao(): CareTeamReminderDao
    abstract fun emrProposalDao(): EmrProposalDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun agentAuditTrailDao(): AgentAuditTrailDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun hospitalInfoDao(): HospitalInfoDao
}
