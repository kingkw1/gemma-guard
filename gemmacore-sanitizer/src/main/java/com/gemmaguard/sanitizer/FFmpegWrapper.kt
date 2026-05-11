package com.gemmaguard.sanitizer

import android.content.Context
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Represents a timestamp window to mute in the output video.
 * Constructed by the Kotlin structural engine from STT timestamps + LLM semantic flags.
 */
data class FlaggedTimestamp(
    val startMs: Long,
    val endMs: Long
)

/**
 * Wraps FFmpeg-Kit to execute block-level audio muting on flagged timestamp windows.
 *
 * Architecture constraints:
 * - No TTS overdubbing. MVP uses silent muting only (mvp_constraints.md §5).
 * - Zero cloud dependencies. All execution is local via embedded FFmpeg binary.
 * - Video stream is copied untouched (-c:v copy) to avoid re-encoding latency.
 * - Only the audio stream is re-encoded (-c:a aac) because audio filters require decoding.
 */
class FFmpegWrapper(private val context: Context) {

    companion object {
        private const val TAG = "GemmaGuard-FFmpeg"
    }

    /**
     * Executes block-level audio muting on the input video at the specified timestamp windows.
     *
     * Pipeline:
     * 1. Build a chained volume=0 audio filter for each [FlaggedTimestamp] window
     * 2. Copy the video stream untouched (-c:v copy)
     * 3. Re-encode the audio with the muting filter applied (-c:a aac)
     * 4. Mux into the output .mp4 container
     *
     * @param inputVideo The source .mp4 file (must be an absolute file path)
     * @param outputVideo The destination file for the sanitized copy
     * @param flaggedTimestamps The list of timestamp windows to mute
     * @return true if the FFmpeg execution completed successfully
     */
    suspend fun executeSanitization(
        inputVideo: File,
        outputVideo: File,
        flaggedTimestamps: List<FlaggedTimestamp>
    ): Boolean = withContext(Dispatchers.IO) {
        if (!inputVideo.exists()) {
            Log.e(TAG, "Input video does not exist: ${inputVideo.absolutePath}")
            return@withContext false
        }

        if (flaggedTimestamps.isEmpty()) {
            Log.d(TAG, "No flagged timestamps — copying input directly.")
            inputVideo.copyTo(outputVideo, overwrite = true)
            return@withContext true
        }

        // Ensure output directory exists
        outputVideo.parentFile?.mkdirs()

        // Delete any existing output to prevent FFmpeg's overwrite prompt
        if (outputVideo.exists()) {
            outputVideo.delete()
        }

        val filterChain = buildMuteFilterChain(flaggedTimestamps)
        Log.d(TAG, "FFmpeg muting filter chain: $filterChain")

        // Build the full FFmpeg command:
        // -i input.mp4                     → Input file
        // -af "volume=enable=...volume=0"  → Audio filter: mute flagged windows
        // -c:v copy                        → Copy video stream (no re-encoding)
        // -c:a aac                         → Re-encode audio (required for filter application)
        // -y                               → Overwrite output without asking
        val command = "-i \"${inputVideo.absolutePath}\" " +
                "-af \"$filterChain\" " +
                "-c:v copy " +
                "-c:a aac " +
                "-y \"${outputVideo.absolutePath}\""

        Log.d(TAG, "Executing FFmpeg command: $command")
        val startTime = System.currentTimeMillis()

        val session = FFmpegKit.execute(command)
        val returnCode = session.returnCode

        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "FFmpeg completed in ${elapsed}ms. Return code: $returnCode")

        if (ReturnCode.isSuccess(returnCode)) {
            val outputSize = outputVideo.length()
            Log.d(TAG, "Sanitized output: ${outputVideo.absolutePath} (${outputSize / 1024} KB)")
            true
        } else {
            val errorLog = session.failStackTrace ?: "No error details available"
            Log.e(TAG, "FFmpeg failed. Return code: $returnCode")
            Log.e(TAG, "FFmpeg error log: $errorLog")
            false
        }
    }

    /**
     * Extracts audio from a video file into a .wav for the SpeechToTextEngine.
     * Used in the BYOF flow: mp4 → wav → SpeechRecognizer → TranscriptChunks.
     *
     * @param inputVideo The source .mp4 file
     * @param outputWav The destination .wav file (PCM 16-bit, 16kHz mono — optimal for SpeechRecognizer)
     * @return true if extraction succeeded
     */
    suspend fun extractAudioForStt(
        inputVideo: File,
        outputWav: File
    ): Boolean = withContext(Dispatchers.IO) {
        if (!inputVideo.exists()) {
            Log.e(TAG, "Input video does not exist: ${inputVideo.absolutePath}")
            return@withContext false
        }

        outputWav.parentFile?.mkdirs()
        if (outputWav.exists()) {
            outputWav.delete()
        }

        // Extract audio as PCM 16-bit, 16kHz mono WAV — optimal for Android SpeechRecognizer
        val command = "-i \"${inputVideo.absolutePath}\" " +
                "-vn " +                          // No video
                "-acodec pcm_s16le " +             // PCM 16-bit little-endian
                "-ar 16000 " +                     // 16kHz sample rate
                "-ac 1 " +                         // Mono
                "-y \"${outputWav.absolutePath}\""

        Log.d(TAG, "Extracting audio for STT: $command")
        val startTime = System.currentTimeMillis()

        val session = FFmpegKit.execute(command)
        val returnCode = session.returnCode

        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "Audio extraction completed in ${elapsed}ms. Return code: $returnCode")

        if (ReturnCode.isSuccess(returnCode)) {
            Log.d(TAG, "Audio extracted: ${outputWav.absolutePath} (${outputWav.length() / 1024} KB)")
            true
        } else {
            Log.e(TAG, "Audio extraction failed. Return code: $returnCode")
            false
        }
    }

    /**
     * Builds the FFmpeg audio filter chain for block-level muting.
     *
     * For each flagged window, generates a volume=0 enable expression:
     * `volume=enable='between(t,start_sec,end_sec)':volume=0`
     *
     * Multiple windows are chained as sequential audio filters separated by commas.
     * FFmpeg applies them left-to-right in a single filter graph pass.
     *
     * Example output for 2 flagged windows:
     * `volume=enable='between(t,2.5,4.1)':volume=0,volume=enable='between(t,12.0,14.3)':volume=0`
     */
    internal fun buildMuteFilterChain(flaggedTimestamps: List<FlaggedTimestamp>): String {
        return flaggedTimestamps.joinToString(",") { ts ->
            val startSec = "%.3f".format(ts.startMs / 1000.0)
            val endSec = "%.3f".format(ts.endMs / 1000.0)
            "volume=enable='between(t,$startSec,$endSec)':volume=0"
        }
    }

    fun destroy() {
        // FFmpeg-Kit manages its own native resources per-session.
        // No persistent state to release.
    }
}
