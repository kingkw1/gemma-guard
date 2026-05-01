package com.gemmaguard.sanitizer

import android.content.Context

import com.google.mediapipe.tasks.genai.llminference.LlmInference

class LiteRTEngine(private val context: Context) {
    private var llmInference: LlmInference? = null

    fun loadModel(modelPath: String) {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(256)
            .build()
        llmInference = LlmInference.createFromOptions(context, options)
    }

    fun analyze(transcript: String): Pair<String, EngineMetrics> {
        val llm = llmInference ?: throw IllegalStateException("Model not loaded")
        val prompt = """
            <start_of_turn>user
            Find profanity in transcript. Format: CSV (start_ms,end_ms,word,category,severity,reasoning). If clean, output EXACTLY AND ONLY: CLEAN
            Transcript: $transcript<end_of_turn>
            <start_of_turn>model
        """.trimIndent()
        
        val startTime = System.currentTimeMillis()
        val result = llm.generateResponse(prompt)
        val endTime = System.currentTimeMillis()
        
        val totalTimeMs = endTime - startTime
        val estimatedTokens = result.length / 4f
        val tps = if (totalTimeMs > 0) (estimatedTokens / (totalTimeMs / 1000f)) else 0f
        
        val runtime = Runtime.getRuntime()
        val usedMemInMB = ((runtime.totalMemory() - runtime.freeMemory()) / 1048576L).toInt()

        val metrics = EngineMetrics(
            timeToFirstTokenMs = totalTimeMs, // Using total time as TTFT fallback for sync call
            totalInferenceTimeMs = totalTimeMs,
            tokensPerSecond = tps,
            memoryUsageMb = usedMemInMB
        )
        
        return Pair(result, metrics)
    }

    fun destroy() {
        llmInference?.close()
        llmInference = null
    }
}

data class EngineMetrics(
    val timeToFirstTokenMs: Long,
    val totalInferenceTimeMs: Long,
    val tokensPerSecond: Float,
    val memoryUsageMb: Int
)
