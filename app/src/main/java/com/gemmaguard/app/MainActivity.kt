package com.gemmaguard.app

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.gemmaguard.app.ui.HitlDashboardScreen
import com.gemmaguard.app.ui.MediaSelectionScreen
import com.gemmaguard.app.viewmodels.MainViewModel
import com.gemmaguard.app.viewmodels.UiState
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
                                discoveredMedia = s.discoveredMedia,
                                onMediaSelected = { media ->
                                    viewModel.processDiscoveredMedia(media)
                                },
                                onRefresh = { viewModel.scanForMedia() }
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
                                    File(s.outputPath).name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    s.outputPath,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = { openSanitizedVideo(s.outputPath) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("▶ Play Sanitized Video")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { openOutputFolder() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("📂 Open Output Folder")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { viewModel.resetToProfileSelection() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
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
     * Opens the sanitized video file using an external video player.
     * Uses FileProvider for Android N+ security requirements.
     */
    private fun openSanitizedVideo(videoPath: String) {
        try {
            val file = File(videoPath)
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Play sanitized video"))
        } catch (e: Exception) {
            android.util.Log.e("GemmaGuard", "Failed to open video", e)
        }
    }

    /**
     * Opens the output folder in the device's file manager.
     */
    private fun openOutputFolder() {
        try {
            val outputDir = viewModel.outputDir
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                outputDir
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // Fallback: try opening the directory with a generic file manager
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                // Alternative: open the first file in the folder
                val files = outputDir.listFiles()
                if (files != null && files.isNotEmpty()) {
                    openSanitizedVideo(files.last().absolutePath)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GemmaGuard", "Failed to open output folder", e)
            // Fallback: just list files via a toast or log
            val files = viewModel.outputDir.listFiles()?.joinToString("\n") { it.name } ?: "empty"
            android.util.Log.d("GemmaGuard", "Output folder contents:\n$files")
        }
    }
}
