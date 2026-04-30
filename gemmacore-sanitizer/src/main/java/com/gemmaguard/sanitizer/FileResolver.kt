package com.gemmaguard.sanitizer

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

class FileResolver(private val context: Context) {
    fun resolveAsset(assetName: String): File {
        val cacheDir = context.cacheDir
        val outFile = File(cacheDir, assetName)
        if (!outFile.exists()) {
            context.assets.open(assetName).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return outFile
    }

    fun resolveContentUri(uri: Uri, tempFileName: String): File {
        val cacheDir = context.cacheDir
        val outFile = File(cacheDir, tempFileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("Could not open input stream for URI: $uri")
        return outFile
    }
}
