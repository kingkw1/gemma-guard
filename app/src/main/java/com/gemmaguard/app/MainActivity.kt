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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.gemmaguard.app.ui.HitlDashboardScreen
import com.gemmaguard.app.ui.MediaSelectionScreen
import com.gemmaguard.app.viewmodels.MainViewModel
import com.gemmaguard.app.viewmodels.TranscriptChunk
import com.gemmaguard.app.viewmodels.UiState
import com.gemmaguard.sanitizer.VttParser

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
                                    val chunks = loadTranscriptChunksFromAssets(assetBaseName)
                                    viewModel.processMedia(chunks)
                                }
                            )
                        }
                        is UiState.Processing -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Analyzing chunk ${s.currentChunk} of ${s.totalChunks}...")
                                }
                            }
                        }
                        is UiState.HitlDashboard -> {
                            HitlDashboardScreen(
                                items = s.items,
                                metrics = metrics,
                                onToggle = { viewModel.toggleCut(it) },
                                onApprove = { /* Execute FFmpeg — Phase 2 */ }
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

    /**
     * Loads a .vtt asset and converts it to TranscriptChunks for the neuro-symbolic pipeline.
     * VttParser is retained for pre-loaded demo assets and BYOF .vtt file support.
     * For dynamic media (Phase 3), SpeechToTextEngine will produce these chunks directly.
     */
    private fun loadTranscriptChunksFromAssets(baseName: String): List<TranscriptChunk> {
        return try {
            val vttContent = assets.open("$baseName.vtt").bufferedReader().use { it.readText() }
            val vttBlocks = VttParser.parse(vttContent)
            vttBlocks.map { block ->
                TranscriptChunk(
                    startMs = block.startMs,
                    endMs = block.endMs,
                    text = block.text
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
