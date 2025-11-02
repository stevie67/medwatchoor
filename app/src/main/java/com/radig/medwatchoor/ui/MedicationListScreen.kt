package com.radig.medwatchoor.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import androidx.wear.compose.material.PositionIndicator
import com.radig.medwatchoor.R
import com.radig.medwatchoor.data.Medication
import com.radig.medwatchoor.viewmodel.MedicationUiState
import com.radig.medwatchoor.viewmodel.MedicationViewModel

/**
 * Main screen showing the list of medications
 */
@Composable
fun MedicationListScreen(
    viewModel: MedicationViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()
    val view = LocalView.current

    // Keep screen on while viewing medications
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
        }
    }

    Scaffold(
        timeText = {
            TimeText()
        },
        positionIndicator = {
            PositionIndicator(scalingLazyListState = listState)
        },
        modifier = modifier
    ) {
        when (val state = uiState) {
            is MedicationUiState.Loading -> LoadingScreen()
            is MedicationUiState.Success -> MedicationList(
                medications = state.medications,
                listState = listState,
                onMedicationTaken = { medicationId ->
                    viewModel.markMedicationAsTaken(medicationId)
                },
                onMedicationReset = { medicationId ->
                    viewModel.resetMedicationTaken(medicationId)
                }
            )
            is MedicationUiState.Error -> ErrorScreen(
                message = state.message,
                onRetry = { viewModel.loadMedications(forceRefresh = true) }
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.error_loading),
            style = MaterialTheme.typography.body1,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.caption2,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun MedicationList(
    medications: List<Medication>,
    listState: ScalingLazyListState,
    onMedicationTaken: (Int) -> Unit,
    onMedicationReset: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Fixed spacer to reserve space for TimeText
        Spacer(modifier = Modifier.height(40.dp))

        // Scrollable medication list
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 8.dp,
                end = 8.dp,
                top = 8.dp,
                bottom = 40.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            state = listState
        ) {
            items(medications) { medication ->
                MedicationItem(
                    medication = medication,
                    onMedicationTaken = onMedicationTaken,
                    onMedicationReset = onMedicationReset
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MedicationItem(
    medication: Medication,
    onMedicationTaken: (Int) -> Unit,
    onMedicationReset: (Int) -> Unit
) {
    val isTaken = medication.isTaken
    val textColor = if (isTaken) Color.Gray else Color.White

    Card(
        onClick = { /* Handled by combinedClickable */ },
        modifier = Modifier.fillMaxWidth(),
        enabled = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .combinedClickable(
                    onClick = {
                        if (!isTaken) {
                            onMedicationTaken(medication.id)
                        }
                    },
                    onLongClick = {
                        // Long press to reset (for testing)
                        if (isTaken) {
                            onMedicationReset(medication.id)
                        }
                    }
                )
        ) {
            // Medication name
            Text(
                text = medication.name,
                style = MaterialTheme.typography.title3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = textColor,
                textDecoration = if (isTaken) TextDecoration.LineThrough else TextDecoration.None
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Time to take
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Take at: ${medication.timeToTake}",
                    style = MaterialTheme.typography.body2,
                    color = textColor
                )

                if (isTaken) {
                    Text(
                        text = stringResource(R.string.taken),
                        style = MaterialTheme.typography.caption2,
                        color = Color.Green,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    CompactButton(
                        onClick = { onMedicationTaken(medication.id) }
                    ) {
                        Text(stringResource(R.string.confirm_taken))
                    }
                }
            }

            // Notes (if any)
            medication.notes?.let { notes ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = notes,
                    style = MaterialTheme.typography.caption2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = textColor
                )
            }
        }
    }
}
