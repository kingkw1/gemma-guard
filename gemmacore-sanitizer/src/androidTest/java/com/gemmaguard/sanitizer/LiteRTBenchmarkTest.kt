package com.gemmaguard.sanitizer

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class LiteRTBenchmarkTest {

    private lateinit var engine: LiteRTEngine

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        engine = LiteRTEngine(context)
        
        val filesDir = context.getExternalFilesDir(null)?.absolutePath
        val parentDir = context.getExternalFilesDir(null)?.parentFile?.absolutePath
        
        val possiblePaths = listOf(
            "$filesDir/gemma-4-E2B-it.litertlm",
            "$parentDir/gemma-4-E2B-it.litertlm",
            "$filesDir/gemma.tflite",
            "$parentDir/gemma.tflite",
            "/data/local/tmp/gemma-4-E2B-it.litertlm",
            "/data/local/tmp/gemma.tflite"
        )
        
        val modelPath = possiblePaths.firstOrNull { java.io.File(it).canRead() }
            ?: throw IllegalStateException("Could not find a READABLE model in any of: $possiblePaths")
            
        android.util.Log.i("[GEMMAGUARD_BENCHMARK]", "Successfully located readable model at: $modelPath")
        engine.loadModel(modelPath)
    }

    @Test
    fun benchmarkInference() {
        val transcript = "What the hell is this?"
        var output = ""
        
        val timeInMillis = measureTimeMillis {
            val (result, _) = engine.analyze(transcript)
            output = result.substringBefore("</end_of_turn>").substringBefore("<eos>").trim()
        }

        Log.i("[GEMMAGUARD_BENCHMARK]", "Execution Time: $timeInMillis ms")
        Log.i("[GEMMAGUARD_BENCHMARK]", "Raw Output: $output")
        
        assert(timeInMillis < 15000) { "Inference took too long: $timeInMillis ms" }
        assert(output.contains("hell|Profanity", ignoreCase = true)) { "Unexpected output: $output" }
    }
    
    @Test
    fun benchmarkInferenceClean() {
        val transcript = "This is a completely normal and clean sentence."
        var output = ""
        
        val timeInMillis = measureTimeMillis {
            val (result, _) = engine.analyze(transcript)
            output = result.substringBefore("</end_of_turn>").substringBefore("<eos>").trim()
        }

        Log.i("[GEMMAGUARD_BENCHMARK]", "Execution Time: $timeInMillis ms")
        Log.i("[GEMMAGUARD_BENCHMARK]", "Raw Output: $output")
        
        assert(timeInMillis < 15000) { "Inference took too long: $timeInMillis ms" }
        assert(output.contains("CLEAN", ignoreCase = true)) { "Expected CLEAN, but got: $output" }
    }

    @After
    fun tearDown() {
        engine.destroy()
    }
}
