package com.radig.medwatchoor.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Medication database operations
 */
@Dao
interface MedicationDao {
    /**
     * Get all medications as a Flow for reactive updates
     */
    @Query("SELECT * FROM medications ORDER BY timeToTake ASC")
    fun getAllMedicationsFlow(): Flow<List<Medication>>

    /**
     * Get all medications (one-time fetch)
     */
    @Query("SELECT * FROM medications ORDER BY timeToTake ASC")
    suspend fun getAllMedications(): List<Medication>

    /**
     * Get all medications that have been modified locally and need to be synced
     */
    @Query("SELECT * FROM medications WHERE isDirty = 1")
    suspend fun getDirtyMedications(): List<Medication>

    /**
     * Insert or replace medications (used when fetching from server)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(medications: List<Medication>)

    /**
     * Update a single medication
     */
    @Update
    suspend fun update(medication: Medication)

    /**
     * Mark medication as taken and record timestamp
     */
    @Query("UPDATE medications SET isTaken = 1, lastTakenTimestamp = :timestamp, isDirty = 1 WHERE id = :medicationId")
    suspend fun markAsTaken(medicationId: Int, timestamp: Long)

    /**
     * Reset taken status for all medications (called daily or when time passes)
     */
    @Query("UPDATE medications SET isTaken = 0")
    suspend fun resetAllTakenStatus()

    /**
     * Clear dirty flag for a medication (after successful upload)
     */
    @Query("UPDATE medications SET isDirty = 0 WHERE id = :medicationId")
    suspend fun clearDirtyFlag(medicationId: Int)

    /**
     * Clear dirty flag for all medications (after successful upload)
     */
    @Query("UPDATE medications SET isDirty = 0")
    suspend fun clearAllDirtyFlags()

    /**
     * Delete all medications (for testing or reset)
     */
    @Query("DELETE FROM medications")
    suspend fun deleteAll()
}
