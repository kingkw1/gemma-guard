package com.gemmaguard.sanitizer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import android.util.Log

@RunWith(AndroidJUnit4::class)
class GpuSmokeTest {

    @Test
    fun testGpuBinding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = LiteRTEngine(context)
        
        val modelPath = "/storage/emulated/0/Android/data/com.gemmaguard.app/files/gemma-4-E2B-it.litertlm"
        
        Log.i("GpuSmokeTest", "Testing fixed model path: $modelPath")
        if (!File(modelPath).exists()) {
             Log.e("GpuSmokeTest", "Model missing at $modelPath")
             return
        }
        
        engine.loadModel(modelPath)

        val (result, metrics) = engine.analyze("Testing with the app's verified model file.")
        Log.i("GpuSmokeTest", "Success! Result: $result")
        Log.i("GpuSmokeTest", "Time: ${metrics.totalInferenceTimeMs}ms")
        
        engine.destroy()
    }
}
