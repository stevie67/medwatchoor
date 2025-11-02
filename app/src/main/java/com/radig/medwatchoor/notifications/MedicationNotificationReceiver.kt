package com.radig.medwatchoor.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.radig.medwatchoor.MainActivity
import com.radig.medwatchoor.R

/**
 * Receives alarm broadcasts and displays medication notifications
 */
class MedicationNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getIntExtra("MEDICATION_ID", -1)
        val medicationName = intent.getStringExtra("MEDICATION_NAME") ?: "Medication"
        val medicationTime = intent.getStringExtra("MEDICATION_TIME") ?: ""

        Log.d("MedicationNotification", "Received alarm for $medicationName at $medicationTime")

        if (medicationId != -1) {
            // Check if medication has already been taken
            if (!isMedicationTaken(context, medicationId)) {
                showNotification(context, medicationId, medicationName, medicationTime)
            } else {
                Log.d("MedicationNotification", "Medication $medicationName already taken - skipping notification")
            }

            // Reschedule for tomorrow
            rescheduleMedication(context, medicationId)
        }
    }

    private fun isMedicationTaken(context: Context, medicationId: Int): Boolean {
        return try {
            val database = com.radig.medwatchoor.data.MedicationDatabase.getDatabase(context)
            val medications = kotlinx.coroutines.runBlocking {
                database.medicationDao().getAllMedications()
            }
            val medication = medications.find { it.id == medicationId }
            medication?.isTaken == true
        } catch (e: Exception) {
            Log.e("MedicationNotification", "Error checking medication status", e)
            false // If error, show notification to be safe
        }
    }

    private fun showNotification(
        context: Context,
        medicationId: Int,
        medicationName: String,
        medicationTime: String
    ) {
        createNotificationChannel(context)

        // Vibrate immediately when notification is shown
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        vibrator.vibrate(longArrayOf(0, 500, 200, 500, 200, 500), -1)

        // Intent to open the app when notification is tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            medicationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Time for $medicationName")
            .setContentText("Scheduled for $medicationTime")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setOngoing(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(medicationId, notification)
            Log.d("MedicationNotification", "Notification shown for $medicationName")
        } catch (e: SecurityException) {
            Log.e("MedicationNotification", "Permission denied to show notification", e)
        }
    }

    private fun createNotificationChannel(context: Context) {
        val name = "Medication Reminders"
        val descriptionText = "Notifications for medication times"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            setShowBadge(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        Log.d("MedicationNotification", "Created notification channel with vibration enabled")
    }

    private fun rescheduleMedication(context: Context, medicationId: Int) {
        // Reschedule will happen automatically when the app next loads medications
        // Or we could trigger a WorkManager task here to reschedule
        Log.d("MedicationNotification", "Medication $medicationId will be rescheduled on next app start")
    }

    companion object {
        private const val CHANNEL_ID = "medication_reminders"
    }
}
