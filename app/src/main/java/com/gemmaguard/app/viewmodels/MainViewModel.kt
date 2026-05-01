package com.gemmaguard.app.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gemmaguard.app.models.*
import com.gemmaguard.sanitizer.LiteRTEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

sealed class UiState {
    object ProfileSelection : UiState()
    object MediaSelection : UiState()
    object Processing : UiState()
    data class HitlDashboard(val items: List<HitlItem>) : UiState()
    data class Error(val message: String) : UiState()
}

data class HitlItem(
    val flaggedData: FlaggedItem,
    var isCheckedForCut: Boolean
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<UiState>(UiState.ProfileSelection)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _selectedProfile = MutableStateFlow<UserProfile?>(null)
    val selectedProfile: StateFlow<UserProfile?> = _selectedProfile.asStateFlow()

    private val _inferenceMetrics = MutableStateFlow<InferenceMetrics?>(null)
    val inferenceMetrics: StateFlow<InferenceMetrics?> = _inferenceMetrics.asStateFlow()
    
    private val liteRTEngine = LiteRTEngine(application)

    init {
        try {
            Log.d("GemmaGuard", "Initializing LiteRT Engine and loading model...")
            // Load the model from the app's external files directory (no permissions required)
            val modelPath = application.getExternalFilesDir(null)?.absolutePath + "/gemma-4-E2B-it.litertlm"
            Log.d("GemmaGuard", "Model path resolved to: $modelPath")
            liteRTEngine.loadModel(modelPath)
            Log.d("GemmaGuard", "Model successfully loaded into memory!")
        } catch (e: Exception) {
            Log.e("GemmaGuard", "Failed to load model", e)
            _uiState.value = UiState.Error("Failed to load model: ${e.message}")
        }
    }

    val defaultProfiles = listOf(
        UserProfile("p1", "Age 4 (Strict)", 4, ToleranceConfig(0, 0, 0, true)),
        UserProfile("p2", "Age 6 (Moderate)", 6, ToleranceConfig(1, 1, 1, true)),
        UserProfile("p3", "Age 7 (Mild)", 7, ToleranceConfig(2, 2, 2, false))
    )

    fun selectProfile(profile: UserProfile) {
        _selectedProfile.value = profile
        _uiState.value = UiState.MediaSelection
    }

    fun processMedia(vttContent: String) {
        Log.d("GemmaGuard", "Starting media processing. Launching IO coroutine...")
        _uiState.value = UiState.Processing
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("GemmaGuard", "Calling liteRTEngine.analyze() with transcript of length ${vttContent.length}")
                val (jsonResult, engineMetrics) = liteRTEngine.analyze(vttContent)
                Log.d("GemmaGuard", "Inference complete! Raw result: $jsonResult")
                
                _inferenceMetrics.value = InferenceMetrics(
                    timeToFirstTokenMs = engineMetrics.timeToFirstTokenMs,
                    totalInferenceTimeMs = engineMetrics.totalInferenceTimeMs,
                    tokensPerSecond = engineMetrics.tokensPerSecond,
                    memoryUsageMb = engineMetrics.memoryUsageMb
                )
                
                val items = mutableListOf<FlaggedItem>()
                val lines = jsonResult.lines()
                for (line in lines) {
                    val cleanLine = line.trim()
                    if (cleanLine.contains("CLEAN", ignoreCase = true) || cleanLine.startsWith("start_ms") || cleanLine.isEmpty()) continue
                    
                    val parts = cleanLine.split(",")
                    if (parts.size >= 6) {
                        try {
                            items.add(
                                FlaggedItem(
                                    timestamp_start = parts[0].toDoubleOrNull()?.toLong() ?: 0L,
                                    timestamp_end = parts[1].toDoubleOrNull()?.toLong() ?: 0L,
                                    text = parts[2].trim('"'),
                                    category = parts[3].trim('"'),
                                    severity = parts[4].toIntOrNull() ?: 5,
                                    reasoning = parts[5].trim('"')
                                )
                            )
                        } catch (e: Exception) {
                            Log.w("GemmaGuard", "Failed to parse CSV line: ${cleanLine}")
                        }
                    }
                }
                
                val profile = _selectedProfile.value
                val hitlItems = items.map { item ->
                    val shouldCut = profile != null && (
                        (item.category == "Profanity" && item.severity > profile.toleranceConfig.maxProfanitySeverity) ||
                        (item.category == "Violence" && item.severity > profile.toleranceConfig.maxViolenceSeverity) ||
                        (item.category == "Thematic" && item.severity > profile.toleranceConfig.maxThematicSeverity) ||
                        (item.category == "Mean Language" && profile.toleranceConfig.blockMeanLanguage)
                    )
                    HitlItem(item, shouldCut)
                }
                _uiState.value = UiState.HitlDashboard(hitlItems)
            } catch (e: Throwable) {
                Log.e("GemmaGuard", "Error during inference or JSON parsing", e)
                _uiState.value = UiState.Error(e.message ?: "Unknown error occurred during processing.")
            }
        }
    }

    fun toggleCut(item: HitlItem) {
        val currentState = _uiState.value
        if (currentState is UiState.HitlDashboard) {
            val updatedItems = currentState.items.map {
                if (it == item) it.copy(isCheckedForCut = !it.isCheckedForCut) else it
            }
            _uiState.value = UiState.HitlDashboard(updatedItems)
        }
    }

    fun resetToProfileSelection() {
        _uiState.value = UiState.ProfileSelection
        _selectedProfile.value = null
    }

    override fun onCleared() {
        super.onCleared()
        liteRTEngine.destroy()
    }
}
