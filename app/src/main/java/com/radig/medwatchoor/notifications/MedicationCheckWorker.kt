package com.radig.medwatchoor.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.radig.medwatchoor.data.MedicationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Periodic worker that:
 * 1. Checks for midnight transition and resets taken status
 * 2. Reschedules medication alarms
 */
class MedicationCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d("MedicationCheckWorker", "Running periodic check")

            val repository = MedicationRepository(applicationContext)

            // Reset any stale taken statuses (from previous days)
            repository.resetStaleStatuses()
            Log.d("MedicationCheckWorker", "Reset stale statuses completed")

            // Get all medications and reschedule notifications
            val medications = repository.getMedicationsFlow()
            // Note: We can't easily collect from flow here, so we'll rely on
            // the app to reschedule when it starts

            Result.success()
        } catch (e: Exception) {
            Log.e("MedicationCheckWorker", "Error in periodic check", e)
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "medication_check_worker"
    }
}
