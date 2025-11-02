package com.radig.medwatchoor.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Repository for managing medication data
 * Implements offline-first architecture with automatic sync
 */
class MedicationRepository(
    context: Context,
    private val apiService: MedicationApiService = MedicationApiService.create()
) {
    private val database = MedicationDatabase.getDatabase(context)
    private val medicationDao = database.medicationDao()
    private val networkMonitor = NetworkMonitor(context)

    /**
     * Get medications as a Flow for reactive UI updates
     * Always returns from local database (offline-first)
     * Filters medications based on weekday restrictions
     */
    fun getMedicationsFlow(): Flow<List<Medication>> {
        return medicationDao.getAllMedicationsFlow().map { medications ->
            medications.filter { medication ->
                shouldShowMedicationToday(medication)
            }
        }
    }

    /**
     * Check if a medication should be shown today based on weekday restrictions
     * @param medication The medication to check
     * @return true if medication should be shown today (null weekdays = every day)
     */
    private fun shouldShowMedicationToday(medication: Medication): Boolean {
        // If weekdays is null or empty, show every day
        if (medication.weekdays.isNullOrEmpty()) {
            return true
        }

        // Get current day of week (1 = Monday, 7 = Sunday)
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        // Convert Calendar's Sunday=1 to our Monday=1 format
        val currentDay = if (dayOfWeek == Calendar.SUNDAY) 7 else dayOfWeek - 1

        return medication.weekdays.contains(currentDay)
    }

    /**
     * Fetches medications from the server and updates local database
     * Falls back to local data if network is unavailable
     */
    suspend fun refreshMedications(forceRefresh: Boolean = false): Result<List<Medication>> {
        return withContext(Dispatchers.IO) {
            try {
                // If no network, return cached data
                if (!networkMonitor.isNetworkAvailable()) {
                    val cachedMedications = medicationDao.getAllMedications()
                    return@withContext if (cachedMedications.isNotEmpty()) {
                        android.util.Log.d("MedicationRepository", "No network - using cached data (${cachedMedications.size} medications)")
                        Result.success(cachedMedications)
                    } else {
                        Result.failure(Exception("No network connection and no cached data available"))
                    }
                }

                // Try to fetch from server
                android.util.Log.d("MedicationRepository", "Fetching medications from server...")
                val response = apiService.getMedications()

                // Get existing medications to preserve local state (isTaken, lastTakenTimestamp)
                val existingMedications = medicationDao.getAllMedications()
                val existingStateMap = existingMedications.associateBy { it.id }

                // Merge server data with local state
                val mergedMedications = response.medications.map { serverMed ->
                    val existingMed = existingStateMap[serverMed.id]
                    if (existingMed != null) {
                        // Preserve local state for existing medications
                        serverMed.copy(
                            isTaken = existingMed.isTaken,
                            lastTakenTimestamp = existingMed.lastTakenTimestamp,
                            isDirty = false
                        )
                    } else {
                        // New medication from server
                        serverMed.copy(isDirty = false)
                    }
                }

                // Delete all existing medications first, then insert merged ones
                // This ensures medications removed from server are also removed locally
                medicationDao.deleteAll()
                medicationDao.insertAll(mergedMedications)
                android.util.Log.d("MedicationRepository", "Replaced local database with ${mergedMedications.size} medications from server (preserved local state)")

                Result.success(mergedMedications)
            } catch (e: Exception) {
                android.util.Log.e("MedicationRepository", "Error fetching medications", e)

                // Return cached data if available
                val cachedMedications = medicationDao.getAllMedications()
                if (cachedMedications.isNotEmpty()) {
                    android.util.Log.d("MedicationRepository", "Using cached data after network error (${cachedMedications.size} medications)")
                    Result.success(cachedMedications)
                } else {
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * Marks a medication as taken with current timestamp
     * Marks medication as dirty for later sync
     */
    suspend fun markMedicationAsTaken(medicationId: Int) {
        withContext(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            medicationDao.markAsTaken(medicationId, timestamp)
            android.util.Log.d("MedicationRepository", "Marked medication $medicationId as taken at $timestamp (marked as dirty)")
        }
    }

    /**
     * Resets a medication's taken status (for testing)
     */
    suspend fun resetMedicationTaken(medicationId: Int) {
        withContext(Dispatchers.IO) {
            val medications = medicationDao.getAllMedications()
            val medication = medications.find { it.id == medicationId }
            medication?.let {
                medicationDao.update(it.copy(isTaken = false, lastTakenTimestamp = 0))
                android.util.Log.d("MedicationRepository", "Reset medication $medicationId taken status")
            }
        }
    }

    /**
     * Resets the taken status for medications that were taken on a previous day
     * Should be called on app start to handle day changes
     */
    suspend fun resetStaleStatuses() {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val medications = medicationDao.getAllMedications()

            medications.forEach { medication ->
                if (medication.isTaken && medication.lastTakenTimestamp > 0) {
                    // Check if the medication was taken on a different day
                    if (!isSameDay(medication.lastTakenTimestamp, now)) {
                        android.util.Log.d("MedicationRepository", "Resetting medication ${medication.id} - taken on previous day")
                        medicationDao.update(medication.copy(isTaken = false, lastTakenTimestamp = 0))
                    }
                }
            }
        }
    }

    /**
     * Checks if two timestamps are on the same calendar day
     */
    private fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
        val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = timestamp1 }
        val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = timestamp2 }

        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
               cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    /**
     * Uploads all dirty (modified) medications to the server
     * Clears dirty flags on successful upload
     */
    suspend fun syncDirtyMedications(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkMonitor.isNetworkAvailable()) {
                    android.util.Log.d("MedicationRepository", "No network - skipping sync")
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val dirtyMedications = medicationDao.getDirtyMedications()
                if (dirtyMedications.isEmpty()) {
                    android.util.Log.d("MedicationRepository", "No dirty medications to sync")
                    return@withContext Result.success(Unit)
                }

                android.util.Log.d("MedicationRepository", "Syncing ${dirtyMedications.size} modified medications...")

                // Upload ALL medications (server expects full list)
                val allMedications = medicationDao.getAllMedications()
                val cleanMedications = allMedications.map { it.copy(isDirty = false) }

                val response = apiService.uploadMedications(
                    authToken = MedicationApiService.getAuthToken(),
                    data = MedicationResponse(medications = cleanMedications)
                )

                if (response.isSuccessful) {
                    // Clear dirty flags for all medications
                    medicationDao.clearAllDirtyFlags()
                    android.util.Log.d("MedicationRepository", "✅ Sync successful - cleared dirty flags")
                    Result.success(Unit)
                } else {
                    val errorBody = response.errorBody()?.string()
                    android.util.Log.e("MedicationRepository", "Sync failed: ${response.code()} - $errorBody")
                    Result.failure(Exception("Upload failed: ${response.code()}"))
                }
            } catch (e: Exception) {
                android.util.Log.e("MedicationRepository", "Exception during sync", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Observe network connectivity
     */
    fun observeNetworkConnectivity(): Flow<Boolean> {
        return networkMonitor.observeNetworkConnectivity()
    }

    /**
     * Check if there are pending changes to upload
     */
    suspend fun hasPendingChanges(): Boolean {
        return withContext(Dispatchers.IO) {
            medicationDao.getDirtyMedications().isNotEmpty()
        }
    }
}
