package com.gemmaguard.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                    
                    val pickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument(),
                        onResult = { uri ->
                            uri?.let { viewModel.processSelectedVideo(it) }
                        }
                    )

                    when (val s = state) {
                        is UiState.ProfileSelection -> {
                            com.gemmaguard.app.ui.ProfileSelectionScreen(
                                profiles = viewModel.defaultProfiles,
                                onProfileSelected = { viewModel.selectProfile(it) }
                            )
                        }
                        is UiState.MediaSelection -> {
                            MediaSelectionScreen(
                                onPickMedia = { pickerLauncher.launch(arrayOf("video/mp4")) }
                            )
                        }
                        is UiState.Processing -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(s.status)
                                    if (s.total > 0) {
                                        Text("${s.current} / ${s.total}", style = MaterialTheme.typography.labelSmall)
                                    }
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
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = { openSanitizedVideo(s.outputPath) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("▶ Play Sanitized Video")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { viewModel.reset() },
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
                                Button(onClick = { viewModel.reset() }) {
                                    Text("Try Again")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openSanitizedVideo(videoPath: String) {
        try {
            val file = File(videoPath)
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Play sanitized video"))
        } catch (e: Exception) {
            android.util.Log.e("GemmaGuard", "Failed to open video", e)
        }
    }
}
