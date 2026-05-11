package com.gemmaguard.app.models

data class UserProfile(
    val id: String,
    val name: String,
    val age: Int,
    val toleranceConfig: ToleranceConfig
)

data class ToleranceConfig(
    val maxProfanitySeverity: Int,
    val maxViolenceSeverity: Int,
    val maxThematicSeverity: Int,
    val blockMeanLanguage: Boolean
)

data class InferenceMetrics(
    val timeToFirstTokenMs: Long,
    val totalInferenceTimeMs: Long,
    val tokensPerSecond: Float,
    val memoryUsageMb: Int
)

data class FlaggedItem(
    val timestampStartMs: Long,
    val timestampEndMs: Long,
    val text: String,
    val category: String,
    val severity: Int,
    val reasoning: String
)
