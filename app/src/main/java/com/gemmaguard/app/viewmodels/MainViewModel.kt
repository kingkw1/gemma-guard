package com.gemmaguard.app.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gemmaguard.app.models.*
import com.gemmaguard.sanitizer.LiteRTEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.ProfileSelection)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _selectedProfile = MutableStateFlow<UserProfile?>(null)
    val selectedProfile: StateFlow<UserProfile?> = _selectedProfile.asStateFlow()

    private val _inferenceMetrics = MutableStateFlow<InferenceMetrics?>(null)
    val inferenceMetrics: StateFlow<InferenceMetrics?> = _inferenceMetrics.asStateFlow()
    
    private val liteRTEngine = LiteRTEngine()

    init {
        try {
            liteRTEngine.loadModel("dummy_model_path.tflite")
        } catch (e: Exception) {
            // Ignored for MVP setup
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
        _uiState.value = UiState.Processing
        viewModelScope.launch {
            try {
                _inferenceMetrics.value = InferenceMetrics(120, 1500, 20f, 400)
                
                val jsonResult = liteRTEngine.analyze(vttContent)
                val items = Json.decodeFromString<List<FlaggedItem>>(jsonResult)
                
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
            } catch (e: Exception) {
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
