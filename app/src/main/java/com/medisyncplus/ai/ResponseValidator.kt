package com.medisyncplus.ai

import android.util.Log
import org.json.JSONObject

object ResponseValidator {
    private const val TAG = "ResponseValidator"
    private const val MIN_LENGTH = 10

    /**
     * Validates whether the AI response is usable and doesn't contain generic refusals or data-access claims.
     */
    fun isValid(response: String): Boolean {
        if (response.isBlank() || response.length < MIN_LENGTH) {
            Log.w(TAG, "Response failed: Too short or blank")
            return false
        }

        val responseLower = response.lowercase()
        
        // List of phrases that indicate the AI is ignoring the provided context or refusing access
        val refusals = listOf(
            "i am an ai", 
            "as an ai", 
            "knowledge cutoff",
            "cannot fulfill this request", 
            "information is not available",
            "access your medical records",
            "retrieve your care plan",
            "do not have access",
            "unable to find your information",
            "cannot retrieve",
            "not available at the moment",
            "access to your personal data",
            "clinical records are not accessible",
            "retrieve your profile",
            "don't have your specific",
            "don't have access to your"
        )

        if (refusals.any { responseLower.contains(it) }) {
            Log.w(TAG, "Response failed: Data access refusal or AI disclaimer detected")
            return false
        }

        return true
    }

    /**
     * Checks if the response is relevant to the input context.
     */
    fun isRelevant(response: String, context: String): Boolean {
        val contextKeywords = context.lowercase().split(Regex("\\s+"))
            .filter { it.length > 4 }
            .take(10)
        
        if (contextKeywords.isEmpty()) return true

        val responseLower = response.lowercase()
        val matches = contextKeywords.count { responseLower.contains(it) }
        
        return matches > 0 || responseLower.contains("symptom") || responseLower.contains("health")
    }

    /**
     * Ensures the response contains expected fields if it's JSON.
     */
    fun hasRequiredStructure(response: String, requiredFields: List<String>): Boolean {
        val json = parseJson(response) ?: return false
        return requiredFields.all { json.has(it) }
    }

    fun isHighRisk(input: String): Boolean {
        val highRiskKeywords = listOf("chest pain", "cannot breathe", "stroke", "suicide", "bleeding", "severe", "critical")
        return highRiskKeywords.any { input.lowercase().contains(it) }
    }

    /**
     * Helper to extract and parse JSON from Markdown-style responses.
     */
    fun parseJson(text: String): JSONObject? = try {
        val clean = text
            .replace(Regex("```json\\s*"), "")
            .replace(Regex("```\\s*"), "")
            .trim()
        val start = clean.indexOf('{')
        val end   = clean.lastIndexOf('}')
        if (start >= 0 && end > start) JSONObject(clean.substring(start, end + 1)) else null
    } catch (e: Exception) {
        Log.e(TAG, "JSON parse failed: ${e.message}")
        null
    }
}
