package com.radig.medwatchoor.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

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
     */
    fun getMedicationsFlow(): Flow<List<Medication>> {
        return medicationDao.getAllMedicationsFlow()
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

                // Clear isDirty flag for server data (server is source of truth)
                val cleanMedications = response.medications.map { it.copy(isDirty = false) }

                // Save to local database
                medicationDao.insertAll(cleanMedications)
                android.util.Log.d("MedicationRepository", "Saved ${cleanMedications.size} medications to local database")

                Result.success(cleanMedications)
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
