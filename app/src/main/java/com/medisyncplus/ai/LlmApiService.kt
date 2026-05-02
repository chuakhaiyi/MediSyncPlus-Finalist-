package com.medisyncplus.ai

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

// ─── Backend AI Interaction models ──────────────────────────────────────────
data class AiInteractRequest(
    val userInput: String,
    val patientId: String? = null,
    val role: String? = "PATIENT"
)

// ─── Request / Response models (Legacy/Raw LLM) ──────────────────────────────
data class LlmMessage(
    val role: String,   // "system" | "user" | "assistant"
    val content: String
)

data class LlmRequest(
    val model: String,
    val messages: List<LlmMessage>,
    @SerializedName("max_tokens") val maxTokens: Int = 10000,
    val temperature: Double = 0.3,
    val stream: Boolean = false,
    @SerializedName("response_format") val responseFormat: Map<String, String>? = null
)

data class LlmChoice(
    val index: Int,
    val message: LlmMessage,
    @SerializedName("finish_reason") val finishReason: String?
)

data class LlmUsage(
    @SerializedName("prompt_tokens") val promptTokens: Int,
    @SerializedName("completion_tokens") val completionTokens: Int,
    @SerializedName("total_tokens") val totalTokens: Int
)

data class LlmResponse(
    val id: String?,
    val `object`: String?,
    val model: String?,
    val choices: List<LlmChoice>,
    val usage: LlmUsage?
)

// ─── Retrofit service ─────────────────────────────────────────────────────────
interface LlmApiService {
    
    /**
     * Routes request to the Multi-Agent Backend Orchestrator.
     * Use this for all patient-facing AI features.
     */
    @POST("api/ai/interact")
    suspend fun interact(
        @Header("Authorization") authHeader: String,
        @Body request: AiInteractRequest
    ): Map<String, Any?>

    /**
     * Legacy raw LLM call (e.g. for checklist generation if not yet ported to backend).
     */
    @Headers("Accept-Language: en-US,en")
    @POST("chat/completions")
    suspend fun complete(
        @Header("Authorization") authHeader: String,
        @Body request: LlmRequest
    ): LlmResponse
}
