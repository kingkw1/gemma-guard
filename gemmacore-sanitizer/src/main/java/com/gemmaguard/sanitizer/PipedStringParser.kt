package com.gemmaguard.sanitizer

import android.util.Log

/**
 * Deterministic parser for Gemma 4 E2B's hyper-minimal piped output format.
 *
 * Architecture context:
 * - The LLM acts as a pure semantic classifier, NOT a structural data generator.
 * - Output format: "word|category|severity" or "CLEAN"
 * - The LLM does NOT generate timestamps (STT owns those) or reasoning (Kotlin auto-populates).
 * - JSON is banned. This parser replaces all JSON serialization/deserialization.
 *
 * @see <a href="docs/data_schema.md">Data Schema §3 — Gemma 4 Output Schema</a>
 */
object PipedStringParser {

    private const val TAG = "GemmaGuard-Parser"

    /** The only valid categories the LLM may return. */
    val VALID_CATEGORIES = setOf("Profanity", "Violence", "Thematic", "Mean Language")

    /** Valid severity range (1 = Mildest, 5 = Most Severe). */
    private val SEVERITY_RANGE = 1..5

    /**
     * Parses a single piped-string result from Gemma's inference output.
     *
     * @param rawOutput The raw string returned by [LiteRTEngine.analyze], already stripped
     *                  of the `<end_of_turn>` token.
     * @return A [ParsedFlag] if the content is flagged, or `null` if CLEAN.
     */
    fun parse(rawOutput: String): ParsedFlag? {
        val cleaned = rawOutput.trim()

        // CLEAN means no flagged content in this chunk
        if (cleaned.equals("CLEAN", ignoreCase = true)) {
            return null
        }

        // Guard against empty output
        if (cleaned.isEmpty()) {
            Log.w(TAG, "Empty LLM output — treating as CLEAN.")
            return null
        }

        val parts = cleaned.split("|")
        if (parts.size != 3) {
            Log.w(TAG, "Malformed piped output (expected 3 fields, got ${parts.size}): \"$cleaned\"")
            return ParsedFlag(
                flaggedText = cleaned,
                category = "Thematic",
                severity = 5,
                isMalformed = true
            )
        }

        val word = parts[0].trim()
        val rawCategory = parts[1].trim()
        val rawSeverity = parts[2].trim()

        // Validate category — fall back to Thematic if the LLM hallucinates a category
        val category = if (rawCategory in VALID_CATEGORIES) rawCategory else {
            Log.w(TAG, "Unknown category \"$rawCategory\" — falling back to Thematic.")
            "Thematic"
        }

        // Validate and clamp severity — default to max (5) if unparseable
        val severity = rawSeverity.toIntOrNull()?.coerceIn(SEVERITY_RANGE) ?: run {
            Log.w(TAG, "Unparseable severity \"$rawSeverity\" — defaulting to 5.")
            5
        }

        return ParsedFlag(
            flaggedText = word,
            category = category,
            severity = severity,
            isMalformed = false
        )
    }

    /**
     * Generates a human-readable reasoning string from the category.
     * The LLM does not generate reasoning (hyper-minimal prompt to save compute).
     * Kotlin auto-populates this field.
     */
    fun generateReasoning(flag: ParsedFlag): String {
        return if (flag.isMalformed) {
            "Flagged by local AI (malformed LLM output — manual review required)"
        } else {
            "Flagged by local AI for ${flag.category}"
        }
    }
}

/**
 * The parsed result of a single Gemma piped-string output.
 * This is a pure semantic classification — no timestamps, no reasoning.
 * Timestamps are owned by the STT engine; reasoning is auto-populated by Kotlin.
 */
data class ParsedFlag(
    val flaggedText: String,
    val category: String,
    val severity: Int,
    val isMalformed: Boolean
)
