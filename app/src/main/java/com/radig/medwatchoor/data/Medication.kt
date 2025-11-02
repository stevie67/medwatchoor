package com.radig.medwatchoor.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * Data model for a medication
 * Also serves as Room database entity for offline storage
 */
@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey
    @SerializedName("id")
    val id: Int,

    @SerializedName("name")
    val name: String,

    @SerializedName("timeToTake")
    val timeToTake: String, // Time in HH:mm format (e.g., "08:00", "14:00")

    @SerializedName("notes")
    val notes: String? = null,

    // Track if medication has been taken today
    val isTaken: Boolean = false,

    // Timestamp of when medication was last taken (for greying out logic)
    val lastTakenTimestamp: Long = 0L,

    // Track if this medication has been modified locally and needs to be uploaded
    val isDirty: Boolean = false
)

/**
 * Wrapper for the JSON response containing the list of medications
 */
data class MedicationResponse(
    @SerializedName("medications")
    val medications: List<Medication>
)
