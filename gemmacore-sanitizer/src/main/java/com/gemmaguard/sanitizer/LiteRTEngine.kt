package com.gemmaguard.sanitizer

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference

class LiteRTEngine(private val context: Context) {
    private var llmInference: LlmInference? = null

    fun loadModel(modelPath: String) {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(1024)
            .build()
        llmInference = LlmInference.createFromOptions(context, options)
    }

    fun analyze(transcript: String): String {
        val llm = llmInference ?: throw IllegalStateException("Model not loaded")
        val prompt = """
            Analyze the following transcript and flag inappropriate elements as a JSON array of FlaggedItem objects (category, severity, reasoning).
            Transcript:
            $transcript
        """.trimIndent()
        return llm.generateResponse(prompt)
    }

    fun destroy() {
        llmInference?.close()
        llmInference = null
    }
}
