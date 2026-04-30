package com.gemmaguard.sanitizer

class LiteRTEngine {
    private var engineHandle: Long = 0

    init {
        System.loadLibrary("gemmacore_sanitizer")
    }

    fun loadModel(modelPath: String) {
        engineHandle = createEngine(modelPath)
    }

    fun analyze(transcript: String): String {
        if (engineHandle == 0L) throw IllegalStateException("Model not loaded")
        return analyzeTranscript(engineHandle, transcript)
    }

    fun destroy() {
        if (engineHandle != 0L) {
            destroyEngine(engineHandle)
            engineHandle = 0
        }
    }

    private external fun createEngine(modelPath: String): Long
    private external fun analyzeTranscript(engineHandle: Long, transcript: String): String
    private external fun destroyEngine(engineHandle: Long)
}
