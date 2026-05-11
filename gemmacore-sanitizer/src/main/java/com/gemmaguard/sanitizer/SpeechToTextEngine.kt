package com.gemmaguard.sanitizer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class SttUtterance(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val confidence: Float
)

class SpeechToTextEngine(private val context: Context) {
    companion object {
        private const val TAG = "GemmaGuard-STT"
    }

    private var voskModel: Model? = null

    fun isOfflineAvailable(): Boolean {
        return true // Vosk is bundled
    }

    private suspend fun initializeModel(): Model = withContext(Dispatchers.IO) {
        if (voskModel != null) return@withContext voskModel!!
        
        val modelDir = File(context.filesDir, "vosk-model")
        if (!modelDir.exists()) {
            modelDir.mkdirs()
            copyAssetFolder("model", modelDir)
        }
        
        Log.d(TAG, "Loading Vosk model from ${modelDir.absolutePath}")
        val model = Model(modelDir.absolutePath)
        voskModel = model
        model
    }

    private fun copyAssetFolder(srcName: String, dstFile: File) {
        val assets = context.assets.list(srcName) ?: return
        if (assets.isEmpty()) {
            // It's a file
            context.assets.open(srcName).use { input ->
                FileOutputStream(dstFile).use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            // It's a directory
            if (!dstFile.exists()) dstFile.mkdirs()
            for (asset in assets) {
                copyAssetFolder("$srcName/$asset", File(dstFile, asset))
            }
        }
    }

    suspend fun transcribe(audioUri: String): List<SttUtterance> = withContext(Dispatchers.IO) {
        val model = initializeModel()
        val utterances = mutableListOf<SttUtterance>()
        
        val audioFile = File(audioUri)
        if (!audioFile.exists()) {
            Log.e(TAG, "Audio file not found: $audioUri")
            return@withContext emptyList()
        }

        // WAV header is 44 bytes, but Vosk handles it reasonably if we just pass everything.
        // For strictness, skipping 44 bytes is better.
        val recognizer = Recognizer(model, 16000f)
        recognizer.setWords(true)

        FileInputStream(audioFile).use { fis ->
            // Skip WAV header
            fis.skip(44)
            
            val buffer = ByteArray(4096)
            var bytesRead: Int
            
            while (fis.read(buffer).also { bytesRead = it } >= 0) {
                if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                    val resultJson = recognizer.result
                    parseResultJson(resultJson, utterances)
                }
            }
            
            // Process final chunk
            val finalJson = recognizer.finalResult
            parseResultJson(finalJson, utterances)
        }

        recognizer.close()
        Log.d(TAG, "Transcription complete. ${utterances.size} utterances captured.")
        utterances
    }

    private fun parseResultJson(jsonStr: String, utterances: MutableList<SttUtterance>) {
        if (jsonStr.isBlank()) return
        try {
            val root = JSONObject(jsonStr)
            val text = root.optString("text", "")
            if (text.isNotBlank() && root.has("result")) {
                val wordsArray = root.getJSONArray("result")
                if (wordsArray.length() > 0) {
                    val firstWord = wordsArray.getJSONObject(0)
                    val lastWord = wordsArray.getJSONObject(wordsArray.length() - 1)
                    
                    val startMs = (firstWord.getDouble("start") * 1000).toLong()
                    val endMs = (lastWord.getDouble("end") * 1000).toLong()
                    
                    utterances.add(
                        SttUtterance(
                            startMs = startMs,
                            endMs = endMs,
                            text = text,
                            confidence = 1.0f // Vosk gives conf per word, we'll just set 1.0
                        )
                    )
                    Log.d(TAG, "Utterance: [$startMs-$endMs] \"$text\"")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Vosk JSON", e)
        }
    }

    fun destroy() {
        voskModel?.close()
        voskModel = null
        Log.d(TAG, "SpeechToTextEngine destroyed.")
    }
}
