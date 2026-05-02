package com.medisyncplus.ai

import android.util.Log

object InputProcessor {
    private const val MAX_NORMAL_SIZE = 2000
    private const val MAX_CHUNK_SIZE = 8000
    private const val CHUNK_TARGET_SIZE = 1200 // Aiming for 1000-1500 chars

    /**
     * Cleans noisy text (repeated symbols, spacing) and returns a normalized version.
     */
    fun preprocess(input: String): String {
        if (input.isBlank()) return ""
        
        return input
            .replace(Regex("[!?.]{4,}"), { it.value.take(3) }) // Reduce repeated punctuation
            .replace(Regex("\\s+"), " ") // Normalize spacing
            .trim()
    }

    fun isOversized(input: String): Boolean = input.length > MAX_NORMAL_SIZE

    fun isExtremelyOversized(input: String): Boolean = input.length > MAX_CHUNK_SIZE

    /**
     * Chunks input into segments of 1000-1500 characters.
     */
    fun chunkInput(input: String): List<String> {
        if (input.length <= MAX_NORMAL_SIZE) return listOf(input)
        
        val effectiveInput = if (input.length > MAX_CHUNK_SIZE) truncateInput(input) else input
        val chunks = mutableListOf<String>()
        var startIndex = 0
        
        while (startIndex < effectiveInput.length) {
            var endIndex = startIndex + CHUNK_TARGET_SIZE
            if (endIndex >= effectiveInput.length) {
                endIndex = effectiveInput.length
            } else {
                // Try to find a good breaking point (period or space)
                val lastPeriod = effectiveInput.lastIndexOf('.', endIndex)
                if (lastPeriod > startIndex + (CHUNK_TARGET_SIZE / 2)) {
                    endIndex = lastPeriod + 1
                } else {
                    val lastSpace = effectiveInput.lastIndexOf(' ', endIndex)
                    if (lastSpace > startIndex + (CHUNK_TARGET_SIZE / 2)) {
                        endIndex = lastSpace + 1
                    }
                }
            }
            chunks.add(effectiveInput.substring(startIndex, endIndex).trim())
            startIndex = endIndex
        }
        return chunks
    }

    fun truncateInput(input: String): String {
        return if (input.length > MAX_CHUNK_SIZE) {
            input.substring(0, MAX_CHUNK_SIZE)
        } else {
            input
        }
    }

    /**
     * Basic extraction of key signals. In a real app, this might use regex or a lightweight NER model.
     */
    fun extractSignals(input: String): Map<String, List<String>> {
        val lower = input.lowercase()
        val symptoms = listOf("pain", "fever", "cough", "dizzy", "swelling", "shortness of breath", "breathless", "palpitations")
        val medications = listOf("aspirin", "warfarin", "bisoprolol", "furosemide", "ramipril", "statin")
        
        return mapOf(
            "symptoms" to symptoms.filter { lower.contains(it) },
            "medications" to medications.filter { lower.contains(it) }
        )
    }

    data class ProcessingResult(
        val strategy: String,
        val originalLength: Int,
        val processedLength: Int,
        val isTruncated: Boolean = false
    )

    fun getProcessingStats(input: String): ProcessingResult {
        val len = input.length
        return when {
            len > MAX_CHUNK_SIZE -> ProcessingResult("truncated", len, MAX_CHUNK_SIZE, true)
            len > MAX_NORMAL_SIZE -> ProcessingResult("chunked", len, len, false)
            else -> ProcessingResult("normal", len, len, false)
        }
    }
}
