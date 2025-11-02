package com.radig.medwatchoor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radig.medwatchoor.ui.MedicationListScreen
import com.radig.medwatchoor.viewmodel.MedicationViewModel
import com.radig.medwatchoor.viewmodel.MedicationViewModelFactory

/**
 * Main activity for the MedWatchoor watch app
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MedWatchoorApp()
        }
    }
}

/**
 * Main app composable
 */
@Composable
fun MedWatchoorApp() {
    val context = LocalContext.current
    val viewModel: MedicationViewModel = viewModel(
        factory = MedicationViewModelFactory(context)
    )

    MedicationListScreen(viewModel = viewModel)
}
