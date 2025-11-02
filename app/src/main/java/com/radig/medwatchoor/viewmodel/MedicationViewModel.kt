package com.radig.medwatchoor.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.radig.medwatchoor.data.Medication
import com.radig.medwatchoor.data.MedicationRepository
import com.radig.medwatchoor.notifications.MedicationCheckWorker
import com.radig.medwatchoor.notifications.MedicationScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * UI state for the medication list screen
 */
sealed class MedicationUiState {
    object Loading : MedicationUiState()
    data class Success(val medications: List<Medication>) : MedicationUiState()
    data class Error(val message: String) : MedicationUiState()
}

/**
 * ViewModel for managing medication data and UI state
 * Implements offline-first with automatic sync
 */
class MedicationViewModel(
    private val repository: MedicationRepository,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<MedicationUiState>(MedicationUiState.Loading)
    val uiState: StateFlow<MedicationUiState> = _uiState.asStateFlow()

    private var syncJob: Job? = null
    private val syncDelayMs = 3000L // Wait 3 seconds after last change before syncing
    private val medicationScheduler = MedicationScheduler(context)

    init {
        // Reset any medications that were taken on previous days
        viewModelScope.launch {
            repository.resetStaleStatuses()
        }

        // Observe medications from local database (offline-first)
        viewModelScope.launch {
            repository.getMedicationsFlow().collect { medications ->
                if (medications.isNotEmpty()) {
                    _uiState.value = MedicationUiState.Success(medications)
                    // Schedule notifications for all medications
                    scheduleMedicationNotifications(medications)
                }
            }
        }

        // Try to refresh from server
        loadMedications()

        // Monitor network connectivity and auto-sync when online
        observeNetworkAndSync()

        // Setup periodic worker for midnight reset
        setupPeriodicWorker()
    }

    /**
     * Schedules notifications for all medications
     */
    private fun scheduleMedicationNotifications(medications: List<Medication>) {
        viewModelScope.launch {
            try {
                medicationScheduler.scheduleAllMedications(medications)
                android.util.Log.d("MedicationViewModel", "Scheduled notifications for ${medications.size} medications")
            } catch (e: Exception) {
                android.util.Log.e("MedicationViewModel", "Failed to schedule notifications", e)
            }
        }
    }

    /**
     * Sets up periodic worker to check midnight transition and reset statuses
     */
    private fun setupPeriodicWorker() {
        val workRequest = PeriodicWorkRequestBuilder<MedicationCheckWorker>(
            1, TimeUnit.HOURS // Check every hour
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MedicationCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
        android.util.Log.d("MedicationViewModel", "Setup periodic worker for midnight reset")
    }

    /**
     * Loads medications from the server and updates local database
     * Falls back to cached data if offline
     */
    fun loadMedications(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            repository.refreshMedications(forceRefresh).fold(
                onSuccess = { medications ->
                    // Data is automatically updated via Flow, but we can update state immediately
                    _uiState.value = MedicationUiState.Success(medications)
                },
                onFailure = { exception ->
                    // Only show error if we have no data at all
                    if (_uiState.value is MedicationUiState.Loading) {
                        _uiState.value = MedicationUiState.Error(
                            exception.message ?: "Unable to load medications"
                        )
                    }
                    // Otherwise, we're showing cached data and that's fine
                }
            )
        }
    }

    /**
     * Marks a medication as taken
     * Schedules a sync to the server after a delay
     */
    fun markMedicationAsTaken(medicationId: Int) {
        viewModelScope.launch {
            repository.markMedicationAsTaken(medicationId)
            // UI updates automatically via Flow

            // Schedule sync after delay (works offline too - will sync when online)
            scheduleSync()
        }
    }

    /**
     * Resets a medication's taken status (for testing)
     */
    fun resetMedicationTaken(medicationId: Int) {
        viewModelScope.launch {
            repository.resetMedicationTaken(medicationId)
            // UI updates automatically via Flow
        }
    }

    /**
     * Schedules a sync to the server after a delay
     * Cancels any pending sync to batch multiple changes
     */
    private fun scheduleSync() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            delay(syncDelayMs)
            syncToServer()
        }
    }

    /**
     * Syncs dirty (modified) medications to the server
     */
    private suspend fun syncToServer() {
        repository.syncDirtyMedications().fold(
            onSuccess = {
                android.util.Log.d("MedicationViewModel", "✅ Sync successful!")
            },
            onFailure = { exception ->
                android.util.Log.d("MedicationViewModel", "Sync skipped or failed: ${exception.message}")
                // Not an error - just means we're offline or no changes to sync
            }
        )
    }

    /**
     * Observes network connectivity and syncs when network becomes available
     */
    private fun observeNetworkAndSync() {
        viewModelScope.launch {
            repository.observeNetworkConnectivity().collect { isConnected ->
                if (isConnected) {
                    android.util.Log.d("MedicationViewModel", "Network connected - checking for pending changes...")

                    // Small delay to ensure network is stable
                    delay(1000)

                    // Try to sync any pending changes
                    if (repository.hasPendingChanges()) {
                        android.util.Log.d("MedicationViewModel", "Found pending changes - syncing...")
                        syncToServer()
                    }

                    // Also try to refresh from server
                    loadMedications(forceRefresh = true)
                }
            }
        }
    }
}

/**
 * Factory for creating MedicationViewModel with context
 */
class MedicationViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MedicationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MedicationViewModel(MedicationRepository(context), context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
