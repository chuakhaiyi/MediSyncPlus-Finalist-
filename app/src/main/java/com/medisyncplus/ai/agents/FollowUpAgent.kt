package com.medisyncplus.ai.agents

import android.util.Log
import com.medisyncplus.ai.*
import com.medisyncplus.data.models.*
import com.medisyncplus.data.repository.MediSyncRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FollowUpAgent @Inject constructor(
    private val toolRegistry: AgentToolRegistry,
    private val repo: MediSyncRepository
) {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val tsFmt   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private fun now()   = LocalDateTime.now().format(tsFmt)
    private fun today() = LocalDate.now().format(dateFmt)
    private val patientId: String get() = toolRegistry.patientId

    companion object { private const val TAG = "FollowUpAgent" }

    suspend fun analyse(): FollowUpResult = try {
        val nextAppt = repo.getNextAppointment(patientId)
        val upcoming = mutableListOf<AppointmentEntity>()
        repo.getUpcomingAppointments(patientId).collect { upcoming.addAll(it); return@collect }

        val daysUntil = nextAppt?.let { appt ->
            val apptDate = LocalDate.parse(appt.dateTime.take(10), dateFmt)
            ChronoUnit.DAYS.between(LocalDate.now(), apptDate).toInt()
        }

        val shouldAlert = when {
            daysUntil != null && daysUntil <= 0 -> true   // appointment is today or overdue
            daysUntil != null && daysUntil == 1 -> true   // tomorrow
            nextAppt?.status == "PENDING" -> true         // pending needs confirmation
            else -> false
        }

        val reminderMessage = when {
            daysUntil != null && daysUntil <= 0 -> "You have an appointment TODAY with ${nextAppt?.doctorName}. Don't forget to attend!"
            daysUntil == 1 -> "Your appointment with ${nextAppt?.doctorName} is TOMORROW. Please prepare your BP log and medication list."
            nextAppt?.status == "PENDING" -> "Your appointment with ${nextAppt?.doctorName} is pending confirmation. Please follow up."
            daysUntil != null && daysUntil <= 3 -> "Reminder: Appointment in $daysUntil days with ${nextAppt?.doctorName}."
            else -> "No immediate appointment reminders."
        }

        val alertReason = when {
            daysUntil != null && daysUntil <= 0 -> "Appointment is today/overdue"
            daysUntil == 1 -> "Appointment is tomorrow"
            nextAppt?.status == "PENDING" -> "Appointment pending confirmation"
            else -> ""
        }

        if (shouldAlert && nextAppt != null) {
            toolRegistry.dispatch(ToolCall("propose_emr_update", mapOf(
                "type" to "APPOINTMENT",
                "changes" to mapOf("appointmentId" to nextAppt.id, "daysUntil" to daysUntil, "status" to nextAppt.status),
                "justification" to "Follow-up agent: $alertReason — ${nextAppt.doctorName} on ${nextAppt.dateTime}",
                "urgency" to if (daysUntil != null && daysUntil <= 0) "URGENT" else "ROUTINE",
                "requiresReview" to false,
                "agentId" to "followup_agent"
            )))
        }

        if (nextAppt != null && !nextAppt.reminderSet && daysUntil != null && daysUntil <= 3) {
            repo.updateAppointmentStatus(nextAppt.id, nextAppt.status)
        }

        FollowUpResult(
            nextAppointment = nextAppt,
            daysUntilNext = daysUntil,
            pendingAppointments = upcoming.filter { it.status == "PENDING" },
            reminderMessage = reminderMessage,
            shouldAlert = shouldAlert,
            alertReason = alertReason
        )
    } catch (e: Exception) {
        Log.e(TAG, "analyse failed: ${e.message}")
        FollowUpResult(null, null, emptyList(), "Unable to check appointments.", false, "")
    }
}
