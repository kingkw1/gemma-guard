package com.gemmaguard.app.viewmodels

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gemmaguard.app.models.*
import com.gemmaguard.sanitizer.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class UiState {
    object ProfileSelection : UiState()
    object MediaSelection : UiState()
    data class Processing(val status: String, val current: Int = 0, val total: Int = 0) : UiState()
    data class HitlDashboard(
        val items: List<HitlItem>,
        val videoFilePath: String
    ) : UiState()
    data class Muting(val status: String = "Executing FFmpeg...") : UiState()
    data class MutingComplete(val outputPath: String) : UiState()
    data class Error(val message: String) : UiState()
}

data class HitlItem(
    val flaggedData: FlaggedItem,
    var isCheckedForCut: Boolean
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "GemmaGuard-VM"
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.ProfileSelection)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _selectedProfile = MutableStateFlow<UserProfile?>(null)
    val selectedProfile: StateFlow<UserProfile?> = _selectedProfile.asStateFlow()

    private val _inferenceMetrics = MutableStateFlow<InferenceMetrics?>(null)
    val inferenceMetrics: StateFlow<InferenceMetrics?> = _inferenceMetrics.asStateFlow()

    private val liteRTEngine = LiteRTEngine(getApplication())
    private val ffmpegWrapper = FFmpegWrapper(getApplication())
    private val sttEngine = SpeechToTextEngine(getApplication())
    private val fileResolver = FileResolver(getApplication())

    private var currentVideoFile: File? = null

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
     * Executes the full BYOF Pipeline:
     * 1. Pre-load Gemma model.
     * 2. Resolve URI and Extract Audio.
     * 3. Transcribe Audio (or use sidecar).
     * 4. Group Utterances into 15s Windows.
     * 5. Contextual analysis via LLM.
     */
    fun processSelectedVideo(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val modelPath = File(getApplication<Application>().getExternalFilesDir(null), "gemma-4-E2B-it.litertlm").absolutePath
                // Pre-trigger model loading on background thread (prefers OpenCL on Adreno)
                liteRTEngine.loadModel(modelPath)

                // 1. Resolve URI
                _uiState.value = UiState.Processing("Copying video to cache...")
                val videoFile = fileResolver.resolveContentUri(uri)
                currentVideoFile = videoFile

                // 3. Transcription
                val baseName = videoFile.nameWithoutExtension
                var utterances: List<SttUtterance> = emptyList()
                
                val localVtt = File(videoFile.parent, "${baseName}_transcript.vtt")
                if (localVtt.exists()) {
                    _uiState.value = UiState.Processing("Using local sidecar transcript...")
                    utterances = VttParser.parse(localVtt.readText()).map { 
                        SttUtterance(it.startMs, it.endMs, it.text, 1.0f)
                    }
                } else {
                    _uiState.value = UiState.Processing("Extracting audio for STT...")
                    val audioFile = File(getApplication<Application>().cacheDir, "temp_audio.wav")
                    val extractSuccess = ffmpegWrapper.extractAudioForStt(videoFile, audioFile)
                    
                    if (extractSuccess) {
                        _uiState.value = UiState.Processing("Transcribing audio (offline)...")
                        utterances = sttEngine.transcribe(audioFile.absolutePath)
                        sttEngine.destroy() // Free memory for Gemma
                    }
                }

                if (utterances.isEmpty()) {
                    throw Exception("No speech detected.")
                }

                // Group utterances into strict 15-second windows (RESTORED CONTEXT)
                val chunks = TranscriptChunker.chunk(utterances)

                // 4. Gemma Loop (CRITICAL: Every chunk must be analyzed)
                val flaggedItems = mutableListOf<FlaggedItem>()
                val profile = _selectedProfile.value
                
                // Initialize HitlDashboard state to start streaming
                _uiState.value = UiState.HitlDashboard(emptyList(), videoFile.absolutePath)

                for ((index, chunk) in chunks.withIndex()) {
                    val status = "Analyzing chunk ${index + 1}/${chunks.size}"
                    // We don't overwrite HitlDashboard with Processing here, we let the UI handle the streaming state
                    Log.d(TAG, "$status: \"${chunk.text.take(30)}...\"")
                    
                    val (pipedResult, metrics) = liteRTEngine.analyze(chunk.text)
                    _inferenceMetrics.value = InferenceMetrics(
                        timeToFirstTokenMs = metrics.timeToFirstTokenMs,
                        totalInferenceTimeMs = metrics.totalInferenceTimeMs,
                        tokensPerSecond = metrics.tokensPerSecond,
                        memoryUsageMb = metrics.memoryUsageMb,
                        currentChunkIndex = index + 1,
                        totalChunks = chunks.size
                    )

                    val parsed = PipedStringParser.parse(pipedResult)
                    if (parsed != null) {
                        val flaggedItem = FlaggedItem(
                            timestampStartMs = chunk.startMs,
                            timestampEndMs = chunk.endMs,
                            text = parsed.flaggedText,
                            category = parsed.category,
                            severity = parsed.severity,
                            reasoning = PipedStringParser.generateReasoning(parsed)
                        )
                        flaggedItems.add(flaggedItem)
                        
                        // Determine if it should be cut based on profile
                        val shouldCut = profile != null && (
                            (flaggedItem.category == "Profanity" && flaggedItem.severity > profile.toleranceConfig.maxProfanitySeverity) ||
                            (flaggedItem.category == "Violence" && flaggedItem.severity > profile.toleranceConfig.maxViolenceSeverity) ||
                            (flaggedItem.category == "Thematic" && flaggedItem.severity > profile.toleranceConfig.maxThematicSeverity) ||
                            (flaggedItem.category == "Mean Language" && profile.toleranceConfig.blockMeanLanguage)
                        )
                        
                        val newHitlItem = HitlItem(flaggedItem, shouldCut)
                        
                        // Stream to UI immediately
                        val currentState = _uiState.value
                        if (currentState is UiState.HitlDashboard) {
                            val updatedItems = currentState.items + newHitlItem
                            _uiState.value = UiState.HitlDashboard(updatedItems, videoFile.absolutePath)
                        }
                    }
                }
                Log.i(TAG, "Gemma Inference Complete. Total chunks processed: ${chunks.size}")

            } catch (e: Exception) {
                Log.e(TAG, "Pipeline failed", e)
                _uiState.value = UiState.Error("Processing failed: ${e.message}")
            }
        }
    }

    fun toggleCut(item: HitlItem) {
        val currentState = _uiState.value
        if (currentState is UiState.HitlDashboard) {
            val updatedItems = currentState.items.map {
                if (it == item) it.copy(isCheckedForCut = !it.isCheckedForCut) else it
            }
            _uiState.value = UiState.HitlDashboard(updatedItems, currentState.videoFilePath)
        }
    }

    fun executeMuting() {
        val currentState = _uiState.value
        if (currentState !is UiState.HitlDashboard) return

        val inputFile = currentVideoFile ?: return
        val checkedItems = currentState.items.filter { it.isCheckedForCut }
        val flaggedTimestamps = checkedItems.map { FlaggedTimestamp(it.flaggedData.timestampStartMs, it.flaggedData.timestampEndMs) }

        _uiState.value = UiState.Muting()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val outputFile = File(getApplication<Application>().getExternalFilesDir(null), "sanitized_${System.currentTimeMillis()}.mp4")
                val success = ffmpegWrapper.executeSanitization(inputFile, outputFile, flaggedTimestamps)
                if (success) {
                    _uiState.value = UiState.MutingComplete(outputFile.absolutePath)
                } else {
                    _uiState.value = UiState.Error("Muting failed")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Muting error: ${e.message}")
            }
        }
    }

    fun reset() {
        _uiState.value = UiState.ProfileSelection
        _selectedProfile.value = null
        currentVideoFile = null
    }

    override fun onCleared() {
        liteRTEngine.destroy()
        sttEngine.destroy()
        ffmpegWrapper.destroy()
    }
}
