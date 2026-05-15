package com.gemmaguard.sanitizer

/**
 * Senior Strategic Transcript Chunker.
 * Implements "Contextual Utterance Pairing".
 * Groups exactly 2 utterances at a time to provide enough context
 * for toxicity detection while fitting perfectly into the 32-token CPU hard-cap.
 */
object TranscriptChunker {

    data class TranscriptChunk(
        val startMs: Long,
        val endMs: Long,
        val text: String
    )

    /**
     * Slices transcript into context-aware pairs.
     */
    fun chunk(utterances: List<SttUtterance>): List<TranscriptChunk> {
        if (utterances.isEmpty()) return emptyList()

        val chunks = mutableListOf<TranscriptChunk>()
        
        for (i in utterances.indices) {
            val current = utterances[i]
            val prev = if (i > 0) utterances[i - 1] else null
            
            // Limit text length to ensure it fits in 32 tokens
            val text = if (prev != null) {
                "${prev.text} ${current.text}"
            } else {
                current.text
            }
            
            chunks.add(TranscriptChunk(
                startMs = prev?.startMs ?: current.startMs,
                endMs = current.endMs,
                text = text.take(100) // Hardware-Safety Truncation
            ))
        }

        return chunks
    }
}
