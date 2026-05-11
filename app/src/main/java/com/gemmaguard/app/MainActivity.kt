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
import java.io.File

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
                                onMediaSelected = { vttAssetName, videoFileName ->
                                    val chunks = loadTranscriptChunksFromAssets(vttAssetName)
                                    val videoPath = resolveVideoFilePath(videoFileName)
                                    viewModel.processMedia(chunks, videoPath)
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
                                videoFilePath = s.videoFilePath,
                                onToggle = { viewModel.toggleCut(it) },
                                onApprove = { viewModel.executeMuting() }
                            )
                        }
                        is UiState.Muting -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(s.status)
                                }
                            }
                        }
                        is UiState.MutingComplete -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("✅ Sanitization Complete!", style = MaterialTheme.typography.headlineMedium)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Output saved to:", style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    s.outputPath,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(onClick = { viewModel.resetToProfileSelection() }) {
                                    Text("Process Another Video")
                                }
                            }
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

    /**
     * Resolves a video filename to its absolute path in the app's external files directory.
     * Videos are pushed here via: adb push file.mp4 /storage/emulated/0/Android/data/com.gemmaguard.app/files/
     */
    private fun resolveVideoFilePath(videoFileName: String): String {
        val externalDir = getExternalFilesDir(null)
        return File(externalDir, videoFileName).absolutePath
    }
}
