package com.gemmaguard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.gemmaguard.app.ui.HitlDashboardScreen
import com.gemmaguard.app.viewmodels.MainViewModel
import com.gemmaguard.app.viewmodels.UiState

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by viewModel.uiState.collectAsState()
                    when (val s = state) {
                        is UiState.ProfileSelection -> {
                            com.gemmaguard.app.ui.ProfileSelectionScreen(
                                profiles = viewModel.defaultProfiles,
                                onProfileSelected = { viewModel.selectProfile(it) }
                            )
                        }
                        is UiState.MediaSelection -> {
                            viewModel.processMedia("dummy vtt content")
                        }
                        is UiState.Processing -> {
                            androidx.compose.material3.Text("Processing transcript via LiteRT...")
                        }
                        is UiState.HitlDashboard -> {
                            HitlDashboardScreen(
                                items = s.items,
                                onToggle = { viewModel.toggleCut(it) },
                                onApprove = { /* Execute FFmpeg */ }
                            )
                        }
                    }
                }
            }
        }
    }
}
