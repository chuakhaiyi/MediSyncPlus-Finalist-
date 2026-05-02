package com.medisyncplus.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.medisyncplus.data.database.DatabaseSeeder
import com.medisyncplus.data.models.*
import com.medisyncplus.data.repository.MediSyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import android.widget.Toast
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import android.util.Log


fun scheduleCustomReminder(context: Context, hour: Int, minute: Int, doctorName: String) {
    Log.d("NotificationWorkers", "Scheduling reminder for $hour:$minute for $doctorName")
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    // If the user picked a time that has already passed today, assume they mean tomorrow!
    if (target.before(now)) {
        target.add(Calendar.DAY_OF_MONTH, 1)
    }

    // Calculate the exact delay in milliseconds
    val delayInMillis = target.timeInMillis - now.timeInMillis

    // Pass the doctor's name into the worker
    val inputData = workDataOf("doctor_name" to doctorName)

    val request = OneTimeWorkRequestBuilder<CustomReminderWorker>()
        .setInitialDelay(delayInMillis, TimeUnit.MILLISECONDS) // Set the calculated delay
        .setInputData(inputData)
        .build()

    WorkManager.getInstance(context).enqueue(request)

    // Format the time nicely for the Toast message (e.g., "09:05" instead of "9:5")
    val timeFormatted = String.format(java.util.Locale.getDefault(), "%02d:%02d", hour, minute)
    Toast.makeText(context, "Reminder set for $timeFormatted", Toast.LENGTH_SHORT).show()
}

// ── Notification channels ─────────────────────────────────────────────────────
object NotificationChannels {
    const val MEDICATION   = "medication_reminders"
    const val CHECKLIST    = "checklist_reminders"
    const val ALERTS       = "clinical_alerts"
    const val APPOINTMENTS = "appointment_reminders"

    fun createAll(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        listOf(
            NotificationChannel(MEDICATION, "Medication Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders to take your medications on time" },
            NotificationChannel(CHECKLIST, "Daily Checklist", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Daily care task reminders" },
            NotificationChannel(ALERTS, "Clinical Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Urgent alerts from your care team" },
            NotificationChannel(APPOINTMENTS, "Appointment Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Upcoming appointment reminders" }
        ).forEach { nm.createNotificationChannel(it) }
    }
}

// ── Shared notification helper ────────────────────────────────────────────────
private fun sendNotification(context: Context, id: Int, title: String, body: String, channel: String, priority: Int) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val notif = NotificationCompat.Builder(context, channel)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(priority)
        .setAutoCancel(true)
        .build()
    nm.notify(id, notif)
}

// ── Medication reminder worker ────────────────────────────────────────────────
@HiltWorker
class MedicationReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repo: MediSyncRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val patientId = DatabaseSeeder.PATIENT_ID
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val logs  = repo.getMedLogsForDateOnce(patientId, today)
        val meds  = repo.getActiveMedicationsOnce(patientId)

        logs.filter { it.status == "UPCOMING" }.forEach { log ->
            val med = meds.find { it.id == log.medicationId } ?: return@forEach
            sendNotification(
                context, id = log.id.toInt(),
                title = "Medication Reminder",
                body = "Time to take ${med.name} ${med.dosage}",
                channel = NotificationChannels.MEDICATION,
                priority = NotificationCompat.PRIORITY_HIGH
            )
        }

        logs.filter { it.status == "MISSED" }.forEach { log ->
            val med = meds.find { it.id == log.medicationId } ?: return@forEach
            if (med.criticalMedication && log.missedStreak >= 1) {
                sendNotification(
                    context, id = (log.id + 10000).toInt(),
                    title = "Missed Medication Alert",
                    body = "${med.name} was not taken. This is a critical heart medication. Please take it now.",
                    channel = NotificationChannels.ALERTS,
                    priority = NotificationCompat.PRIORITY_MAX
                )
            }
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MedicationReminderWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "medication_reminders", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}

// ── Checklist reminder worker ─────────────────────────────────────────────────
@HiltWorker
class ChecklistReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repo: MediSyncRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val patientId = DatabaseSeeder.PATIENT_ID
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val pendingCount = repo.countPendingTasks(patientId, today)
        if (pendingCount > 0) {
            sendNotification(
                context, id = 9999,
                title = "Daily Checklist Reminder",
                body = "You have $pendingCount task(s) remaining today. Stay on track!",
                channel = NotificationChannels.CHECKLIST,
                priority = NotificationCompat.PRIORITY_DEFAULT
            )
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ChecklistReminderWorker>(4, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "checklist_reminders", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}

// ── Appointment reminder worker ───────────────────────────────────────────────
@HiltWorker
class AppointmentReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repo: MediSyncRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val patientId = DatabaseSeeder.PATIENT_ID
        val nextAppt = repo.getNextAppointment(patientId)

        nextAppt?.let { appt ->
            val settings = repo.getSettingsOnce(patientId)
            val minutesBefore = settings?.notificationMinutesBefore ?: appt.reminderTimeMinutesBefore
            
            val apptDateTime = LocalDateTime.parse(appt.dateTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            val reminderTime = apptDateTime.minusMinutes(minutesBefore.toLong())
            val now = LocalDateTime.now()

            // Only notify if we are within 30 mins of the calculated reminder time
            val diff = java.time.Duration.between(now, reminderTime).toMinutes()
            
            if (Math.abs(diff) < 30) {
                 sendNotification(
                    context, id = 8005,
                    title = "Appointment Reminder",
                    body = "Your appointment with ${appt.doctorName} is in $minutesBefore minutes.",
                    channel = NotificationChannels.APPOINTMENTS,
                    priority = NotificationCompat.PRIORITY_HIGH
                )
            }

            val today = LocalDate.now()
            val apptDate = LocalDate.parse(appt.dateTime.take(10), DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, apptDate).toInt()

            when (daysUntil) {
                0 -> sendNotification(
                    context, id = 8001,
                    title = "Appointment TODAY",
                    body = "You have an appointment with ${appt.doctorName} today at ${appt.dateTime.takeLast(5)}. Location: ${appt.location}",
                    channel = NotificationChannels.APPOINTMENTS,
                    priority = NotificationCompat.PRIORITY_HIGH
                )
                1 -> sendNotification(
                    context, id = 8002,
                    title = "Appointment Tomorrow",
                    body = "Reminder: Appointment with ${appt.doctorName} tomorrow at ${appt.dateTime.takeLast(5)}. Don't forget your BP log!",
                    channel = NotificationChannels.APPOINTMENTS,
                    priority = NotificationCompat.PRIORITY_DEFAULT
                )
                in 2..3 -> sendNotification(
                    context, id = 8003,
                    title = "Upcoming Appointment",
                    body = "You have an appointment with ${appt.doctorName} in $daysUntil days on ${appt.dateTime.take(10)}.",
                    channel = NotificationChannels.APPOINTMENTS,
                    priority = NotificationCompat.PRIORITY_DEFAULT
                )
            }

            if (appt.status == "PENDING") {
                sendNotification(
                    context, id = 8004,
                    title = "Appointment Needs Confirmation",
                    body = "Your appointment with ${appt.doctorName} on ${appt.dateTime.take(10)} is pending. Please confirm.",
                    channel = NotificationChannels.APPOINTMENTS,
                    priority = NotificationCompat.PRIORITY_DEFAULT
                )
            }
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AppointmentReminderWorker>(6, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "appointment_reminders", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}

// ── Custom reminder worker ────────────────────────────────────────────────────
@HiltWorker
class CustomReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val doctorName = inputData.getString("doctor_name") ?: "Doctor"
        Log.d("NotificationWorkers", "CustomReminderWorker firing for $doctorName")

        sendNotification(
            context, id = System.currentTimeMillis().toInt(),
            title = "Follow-up Reminder",
            body = "Scheduled reminder regarding $doctorName. Tap for details.",
            channel = NotificationChannels.APPOINTMENTS,
            priority = NotificationCompat.PRIORITY_HIGH
        )
        return Result.success()
    }
}

// ── Daily checklist generator worker ──────────────────────────────────────────
@HiltWorker
class DailyChecklistGeneratorWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repo: MediSyncRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val patientId = DatabaseSeeder.PATIENT_ID
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        // Check if today's checklist already exists
        val existing = repo.getChecklistForDateOnce(patientId, today)
        if (existing.isNotEmpty()) return Result.success()

        // Generate tasks from doctor's care plan (via mock hospital API) instead of hardcoded templates
        val patient = repo.getPatientOnce(patientId)
        val mrn = patient?.mrn ?: "MRN-UNKNOWN"
        val carePlan = com.medisyncplus.data.mock.MockHospitalApi.getCarePlanTasks(mrn)
        val tasks = carePlan.mapIndexed { i, t ->
            com.medisyncplus.data.models.ChecklistTaskEntity(
                id                 = "CHK_${today}_${i + 1}",
                patientId          = patientId,
                description        = t.description,
                timeOfDay          = t.timeOfDay,
                iconName           = t.iconName,
                requiresVitalInput = t.requiresVitalInput,
                vitalType          = t.vitalType,
                scheduledDate      = today,
                templateId         = t.templateId,
                scheduledTime      = t.scheduledTime
            )
        }
        repo.upsertChecklistTasks(tasks)

        // Audit trail
        repo.insertAuditTrail(AgentAuditTrailEntity(
            patientId = patientId,
            agentId = "checklist_agent",
            action = "GENERATE_DAILY_TASKS",
            inputSummary = "Auto-generate daily checklist for $today",
            outputSummary = "Generated ${tasks.size} tasks",
            riskLevel = "STABLE",
            toolCallsMade = "[]",
            timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        ))

        sendNotification(
            context, id = 7001,
            title = "Daily Checklist Ready",
            body = "Your care tasks for today are ready. ${tasks.size} tasks to complete.",
            channel = NotificationChannels.CHECKLIST,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            // Run once daily at ~6 AM
            val request = PeriodicWorkRequestBuilder<DailyChecklistGeneratorWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "daily_checklist_generator", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}

// ── EMR sync worker ───────────────────────────────────────────────────────────
@HiltWorker
class EmrSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repo: MediSyncRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val patientId = DatabaseSeeder.PATIENT_ID
        var syncedCount = 0

        // Sync pending EMR proposals (auto-approve ROUTINE ones)
        val pending = mutableListOf<EmrProposalEntity>()
        repo.getPendingProposals(patientId).collect { pending.addAll(it); return@collect }

        pending.filter { it.autoApprovable }.forEach { proposal ->
            repo.approveProposal(proposal.id, "auto_sync")
            syncedCount++
        }

        // Mark vitals as synced
        // In production: push to hospital API
        // Here: we just mark them

        if (syncedCount > 0) {
            repo.insertAuditTrail(AgentAuditTrailEntity(
                patientId = patientId,
                agentId = "emr_sync_worker",
                action = "EMR_SYNC",
                inputSummary = "Background EMR sync",
                outputSummary = "Synced $syncedCount proposals to hospital EMR",
                riskLevel = "STABLE",
                toolCallsMade = "[]",
                timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            ))
        }

        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<EmrSyncWorker>(4, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "emr_sync", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}

// ── Boot receiver ─────────────────────────────────────────────────────────────
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            MedicationReminderWorker.schedule(context)
            ChecklistReminderWorker.schedule(context)
            AppointmentReminderWorker.schedule(context)
            DailyChecklistGeneratorWorker.schedule(context)
            EmrSyncWorker.schedule(context)
        }
    }
}

