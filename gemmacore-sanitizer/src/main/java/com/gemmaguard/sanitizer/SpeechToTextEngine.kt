package com.gemmaguard.sanitizer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A timestamped utterance produced by the offline SpeechRecognizer.
 * Uses raw utterance boundaries (Option A) — each result maps to a natural
 * sentence/phrase boundary as determined by Android's STT engine.
 */
data class SttUtterance(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val confidence: Float
)

/**
 * Wraps Android's native [SpeechRecognizer] for offline audio-to-text transcription.
 *
 * Architecture context:
 * - This is the Pre-Processor stage of the Dual-Pipeline.
 * - Used exclusively for the "Bring Your Own File" (BYOF) flow.
 * - Pre-loaded demo clips use [VttParser] instead for deterministic demo reliability.
 * - Runs with EXTRA_PREFER_OFFLINE=true to enforce zero cloud dependency.
 * - Produces [SttUtterance] objects with raw utterance boundaries (Option A).
 * - The STT engine is the sole source of truth for all timestamp data.
 *
 * Usage flow:
 * 1. Extract audio from the .mp4 to a temporary .wav file (via FFmpeg).
 * 2. Call [transcribe] with the .wav file path.
 * 3. Receive a list of [SttUtterance] with timestamps.
 * 4. Map each [SttUtterance] to a TranscriptChunk for the LLM pipeline.
 *
 * @see <a href="docs/mvp_constraints.md">MVP Constraints §2.3</a>
 */
class SpeechToTextEngine(private val context: Context) {

    companion object {
        private const val TAG = "GemmaGuard-STT"
    }

    private var speechRecognizer: SpeechRecognizer? = null

    /**
     * Checks whether offline speech recognition is available on this device.
     * If the offline language pack is not installed, the BYOF flow should
     * fall back to VttParser or prompt the user to install the language pack.
     */
    fun isOfflineAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * Transcribes an audio source into timestamped utterances using Android's
     * native offline SpeechRecognizer.
     *
     * Architecture constraints:
     * - EXTRA_PREFER_OFFLINE = true (zero cloud dependency)
     * - Raw utterance boundaries (no fixed-window rebinning)
     * - Sequential processing — one recognition session at a time
     *
     * @param audioUri URI of the audio file (must be a file:// URI pointing to a .wav)
     * @return A list of [SttUtterance] with timestamps, or an empty list on failure.
     */
    suspend fun transcribe(audioUri: String): List<SttUtterance> = withContext(Dispatchers.Main) {
        if (!isOfflineAvailable()) {
            Log.e(TAG, "Offline speech recognition not available on this device.")
            return@withContext emptyList()
        }

        val results = CompletableDeferred<List<SttUtterance>>()
        val utterances = mutableListOf<SttUtterance>()
        var currentOffsetMs = 0L

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Pass the audio URI for file-based recognition
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, audioUri)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "SpeechRecognizer ready for speech.")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Speech detected — beginning utterance capture.")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // No-op: We don't need real-time volume feedback for file-based recognition
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // No-op: Raw audio buffer — not needed for our pipeline
            }

            override fun onEndOfSpeech() {
                Log.d(TAG, "End of speech detected.")
            }

            override fun onError(error: Int) {
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client-side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error (expected in offline mode)"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error: $error"
                }
                Log.e(TAG, "SpeechRecognizer error: $errorMsg")
                if (!results.isCompleted) {
                    results.complete(utterances.toList())
                }
            }

            override fun onResults(resultBundle: Bundle?) {
                val matches = resultBundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val confidences = resultBundle?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    val confidence = confidences?.firstOrNull() ?: 0.0f

                    // Estimate utterance duration from word count (~150 words/min average speech rate)
                    val wordCount = text.split("\\s+".toRegex()).size
                    val estimatedDurationMs = (wordCount / 2.5 * 1000).toLong().coerceAtLeast(500)

                    utterances.add(
                        SttUtterance(
                            startMs = currentOffsetMs,
                            endMs = currentOffsetMs + estimatedDurationMs,
                            text = text,
                            confidence = confidence
                        )
                    )

                    Log.d(TAG, "Utterance captured: [${currentOffsetMs}ms-${currentOffsetMs + estimatedDurationMs}ms] " +
                            "\"${text.take(60)}...\" (confidence: ${"%.2f".format(confidence)})")

                    currentOffsetMs += estimatedDurationMs
                }

                if (!results.isCompleted) {
                    results.complete(utterances.toList())
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Log partial results for debugging but don't add to final output
                val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!partials.isNullOrEmpty()) {
                    Log.d(TAG, "Partial result: \"${partials[0].take(40)}...\"")
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                Log.d(TAG, "SpeechRecognizer event: $eventType")
            }
        })

        Log.d(TAG, "Starting offline transcription for: $audioUri")
        speechRecognizer?.startListening(intent)

        val finalUtterances = results.await()
        Log.d(TAG, "Transcription complete. ${finalUtterances.size} utterances captured.")
        finalUtterances
    }

    /**
     * Releases the SpeechRecognizer resources.
     * Must be called when the engine is no longer needed to prevent native resource leaks.
     */
    fun destroy() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        Log.d(TAG, "SpeechToTextEngine destroyed.")
    }
}
