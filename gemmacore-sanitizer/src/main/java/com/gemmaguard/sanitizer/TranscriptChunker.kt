package com.gemmaguard.sanitizer

/**
 * Senior Strategic Transcript Chunker.
 * Implements strict 15s windows with ZERO overlap for CPU efficiency.
 */
object TranscriptChunker {

    data class TranscriptChunk(
        val startMs: Long,
        val endMs: Long,
        val text: String
    )

    /**
     * Slices transcript into strict 15-second windows.
     * ZERO overlap. 120s video = exactly 8 chunks.
     */
    fun chunk(utterances: List<SttUtterance>): List<TranscriptChunk> {
        if (utterances.isEmpty()) return emptyList()

        val chunks = mutableListOf<TranscriptChunk>()
        val maxEndTime = utterances.last().endMs
        
        var startTime = 0L
        while (startTime < maxEndTime) {
            val endTime = startTime + 15000
            
            // Find utterances that overlap with this strict window
            val windowUtterances = utterances.filter { utterance ->
                utterance.startMs < endTime && utterance.endMs >= startTime
            }
            
            if (windowUtterances.isNotEmpty()) {
                val combinedText = windowUtterances.joinToString(" ") { it.text.trim() }
                chunks.add(TranscriptChunk(
                    startMs = startTime,
                    endMs = endTime,
                    text = combinedText
                ))
            }
            
            // Step by exactly 15s for zero overlap
            startTime += 15000 
        }

        return chunks
    }
}
