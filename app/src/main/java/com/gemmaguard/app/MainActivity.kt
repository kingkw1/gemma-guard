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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.gemmaguard.app.ui.HitlDashboardScreen
import com.gemmaguard.app.ui.MediaSelectionScreen
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
                    val metrics by viewModel.inferenceMetrics.collectAsState()
                    when (val s = state) {
                        is UiState.ProfileSelection -> {
                            com.gemmaguard.app.ui.ProfileSelectionScreen(
                                profiles = viewModel.defaultProfiles,
                                onProfileSelected = { viewModel.selectProfile(it) }
                            )
                        }
                        is UiState.MediaSelection -> {
                            MediaSelectionScreen(
                                onMediaSelected = { assetBaseName ->
                                    val vttContent = loadVttFromAssets(assetBaseName)
                                    viewModel.processMedia(vttContent)
                                }
                            )
                        }
                        is UiState.Processing -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Processing transcript via LiteRT...")
                            }
                        }
                        is UiState.HitlDashboard -> {
                            HitlDashboardScreen(
                                items = s.items,
                                metrics = metrics,
                                onToggle = { viewModel.toggleCut(it) },
                                onApprove = { /* Execute FFmpeg */ }
                            )
                        }
                        is UiState.Error -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { viewModel.resetToProfileSelection() }) {
                                    Text("Try Again")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadVttFromAssets(baseName: String): String {
        return try {
            assets.open("$baseName.vtt").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "Error loading transcript"
        }
    }
}
