package com.radig.medwatchoor.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.radig.medwatchoor.data.Medication
import java.util.Calendar

/**
 * Schedules exact-time alarms for medication reminders
 */
class MedicationScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedules notifications for all medications
     */
    fun scheduleAllMedications(medications: List<Medication>) {
        // Cancel all existing alarms first
        cancelAllMedications(medications)

        // Schedule new alarms for each medication
        medications.forEach { medication ->
            scheduleMedication(medication)
        }
    }

    /**
     * Schedules a notification for a single medication
     * Respects weekday restrictions
     */
    private fun scheduleMedication(medication: Medication) {
        val (hour, minute) = parseTime(medication.timeToTake)

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If the time has already passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }

            // If medication has weekday restrictions, find next valid day
            if (!medication.weekdays.isNullOrEmpty()) {
                var daysChecked = 0
                while (daysChecked < 7) {
                    val dayOfWeek = get(Calendar.DAY_OF_WEEK)
                    val currentDay = if (dayOfWeek == Calendar.SUNDAY) 7 else dayOfWeek - 1

                    if (medication.weekdays.contains(currentDay)) {
                        break // Found a valid day
                    }

                    // Move to next day
                    add(Calendar.DAY_OF_YEAR, 1)
                    daysChecked++
                }

                if (daysChecked >= 7) {
                    Log.e("MedicationScheduler", "No valid weekday found for ${medication.name}")
                    return
                }
            }
        }

        val intent = Intent(context, MedicationNotificationReceiver::class.java).apply {
            putExtra("MEDICATION_ID", medication.id)
            putExtra("MEDICATION_NAME", medication.name)
            putExtra("MEDICATION_TIME", medication.timeToTake)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medication.id, // Use medication ID as request code
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
            Log.d("MedicationScheduler", "Scheduled ${medication.name} for ${calendar.time}")
        } catch (e: SecurityException) {
            Log.e("MedicationScheduler", "Failed to schedule alarm for ${medication.name}", e)
        }
    }

    /**
     * Cancels all scheduled medication alarms
     */
    private fun cancelAllMedications(medications: List<Medication>) {
        medications.forEach { medication ->
            cancelMedication(medication.id)
        }
    }

    /**
     * Cancels a specific medication alarm
     */
    private fun cancelMedication(medicationId: Int) {
        val intent = Intent(context, MedicationNotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medicationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /**
     * Parses time string in HH:mm format
     */
    private fun parseTime(timeString: String): Pair<Int, Int> {
        val parts = timeString.split(":")
        return Pair(parts[0].toInt(), parts[1].toInt())
    }
}
