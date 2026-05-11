package com.gemmaguard.app.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gemmaguard.app.models.*
import com.gemmaguard.sanitizer.LiteRTEngine
import com.gemmaguard.sanitizer.PipedStringParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class UiState {
    object ProfileSelection : UiState()
    object MediaSelection : UiState()
    data class Processing(val currentChunk: Int = 0, val totalChunks: Int = 0) : UiState()
    data class HitlDashboard(val items: List<HitlItem>) : UiState()
    data class Error(val message: String) : UiState()
}

data class HitlItem(
    val flaggedData: FlaggedItem,
    var isCheckedForCut: Boolean
)

/**
 * Represents a timestamped text chunk produced by the STT engine.
 * The STT engine is the sole source of truth for all timestamp data.
 */
data class TranscriptChunk(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "GemmaGuard"
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.ProfileSelection)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _selectedProfile = MutableStateFlow<UserProfile?>(null)
    val selectedProfile: StateFlow<UserProfile?> = _selectedProfile.asStateFlow()

    private val _inferenceMetrics = MutableStateFlow<InferenceMetrics?>(null)
    val inferenceMetrics: StateFlow<InferenceMetrics?> = _inferenceMetrics.asStateFlow()
    
    private val liteRTEngine = LiteRTEngine(application)

    init {
        try {
            Log.d(TAG, "Initializing LiteRT Engine and loading model...")
            // Load the model from the app's external files directory (no permissions required)
            val modelPath = application.getExternalFilesDir(null)?.absolutePath + "/gemma-4-E2B-it.litertlm"
            Log.d(TAG, "Model path resolved to: $modelPath")
            liteRTEngine.loadModel(modelPath)
            Log.d(TAG, "Model successfully loaded into memory!")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
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

    /**
     * Processes media by running each STT-generated transcript chunk through Gemma sequentially.
     *
     * Architecture (Neuro-Symbolic Pipeline):
     * 1. STT engine provides [TranscriptChunk] list (text + timestamps).
     * 2. Each chunk is fed synchronously to Gemma via [LiteRTEngine.analyze].
     * 3. Gemma returns a hyper-minimal piped string: "word|category|severity" or "CLEAN".
     * 4. [PipedStringParser] extracts the semantic classification.
     * 5. Kotlin merges the parsed flag with the STT-provided timestamps
     *    to construct [FlaggedItem] state objects.
     * 6. Items are auto-flagged against the active [ToleranceConfig].
     *
     * CRITICAL: Inference calls are sequential (synchronous generateResponse).
     * DO NOT use generateResponseAsync() — it orphans native XNNPACK threads → OOM.
     */
    fun processMedia(chunks: List<TranscriptChunk>) {
        Log.d(TAG, "Starting media processing. ${chunks.size} chunks to analyze.")
        _uiState.value = UiState.Processing(currentChunk = 0, totalChunks = chunks.size)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allFlaggedItems = mutableListOf<FlaggedItem>()

                for ((index, chunk) in chunks.withIndex()) {
                    _uiState.value = UiState.Processing(
                        currentChunk = index + 1,
                        totalChunks = chunks.size
                    )

                    Log.d(TAG, "Analyzing chunk ${index + 1}/${chunks.size}: \"${chunk.text.take(50)}...\"")
                    val (pipedResult, engineMetrics) = liteRTEngine.analyze(chunk.text)
                    Log.d(TAG, "Chunk ${index + 1} inference complete. Raw piped output: \"$pipedResult\"")

                    // Update metrics with the latest inference run
                    _inferenceMetrics.value = InferenceMetrics(
                        timeToFirstTokenMs = engineMetrics.timeToFirstTokenMs,
                        totalInferenceTimeMs = engineMetrics.totalInferenceTimeMs,
                        tokensPerSecond = engineMetrics.tokensPerSecond,
                        memoryUsageMb = engineMetrics.memoryUsageMb
                    )

                    // Parse the piped string via the dedicated parser in :gemmacore-sanitizer
                    val parsedFlag = PipedStringParser.parse(pipedResult)
                    if (parsedFlag != null) {
                        // Merge semantic classification with STT-provided timestamps
                        allFlaggedItems.add(
                            FlaggedItem(
                                timestampStartMs = chunk.startMs,
                                timestampEndMs = chunk.endMs,
                                text = parsedFlag.flaggedText,
                                category = parsedFlag.category,
                                severity = parsedFlag.severity,
                                reasoning = PipedStringParser.generateReasoning(parsedFlag)
                            )
                        )
                    } else {
                        Log.d(TAG, "Chunk [${chunk.startMs}-${chunk.endMs}ms] is CLEAN.")
                    }
                }

                Log.d(TAG, "All chunks processed. ${allFlaggedItems.size} items flagged.")

                // Auto-flag items against the active ToleranceConfig
                val profile = _selectedProfile.value
                val hitlItems = allFlaggedItems.map { item ->
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
                Log.e(TAG, "Error during inference or piped-string parsing", e)
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
