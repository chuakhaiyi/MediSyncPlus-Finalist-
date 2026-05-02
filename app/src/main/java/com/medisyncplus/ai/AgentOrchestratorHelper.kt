package com.medisyncplus.ai

import android.util.Log
import com.google.gson.Gson
import com.medisyncplus.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

object AgentOrchestratorHelper {
    private const val TAG = "AgentHelper"
    private const val LS_TAG = "LangSmith"
    private const val MAX_RETRIES = 2
    private val client = OkHttpClient()

    suspend fun runAgentWithTools(
        llmService: LlmApiService,
        toolRegistry: AgentToolRegistry,
        gson: Gson,
        systemPrompt: String,
        userMessage: String,
        maxToolRounds: Int = 3,
        authHeader: String = "",
        model: String = "",
        requiredJsonFields: List<String> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        var retryCount = 0
        var lastResponse = ""
        val runId = UUID.randomUUID().toString()
        
        val effectiveAuth = authHeader.ifEmpty {
            try { "Bearer ${BuildConfig.LLM_API_KEY}" } catch (e: Exception) { "" }
        }
        val effectiveModel = model.ifEmpty {
            try { BuildConfig.LLM_MODEL } catch (e: Exception) { "llama-3.3-70b-versatile" }
        }

        // 1. Start LangSmith Trace
        performTrace(runId, "AgentSession", JSONObject().apply {
            put("system_prompt", systemPrompt)
            put("user_message", userMessage)
            put("model", effectiveModel)
        })

        while (retryCount <= MAX_RETRIES) {
            val messages = mutableListOf(
                LlmMessage("system", systemPrompt),
                LlmMessage("user", if (retryCount > 0) "Previous response invalid. Retry $retryCount.\n\n$userMessage" else userMessage)
            )

            var round = 0
            while (round < maxToolRounds) {
                try {
                    val response = llmService.complete(
                        effectiveAuth,
                        LlmRequest(model = effectiveModel, messages = messages, maxTokens = 1500, temperature = 0.3 + (retryCount * 0.1))
                    )
                    lastResponse = response.choices.firstOrNull()?.message?.content ?: ""
                    Log.d(TAG, "Round $round response received.")
                } catch (e: Exception) {
                    Log.e(TAG, "LLM failed: ${e.message}")
                    updateTrace(runId, JSONObject().apply { put("error", e.message) }, "error")
                    break 
                }

                val toolCalls = parseToolCalls(lastResponse)
                if (toolCalls.isEmpty()) break

                val toolResults = StringBuilder().appendLine("TOOL_RESULTS:")
                for (call in toolCalls) {
                    val result = toolRegistry.dispatch(call)
                    toolResults.appendLine("${call.name}: ${if (result.success) gson.toJson(result.data) else "ERR: ${result.error}"}")
                }
                messages.add(LlmMessage("assistant", lastResponse))
                messages.add(LlmMessage("user", toolResults.toString() + "\n\nProvide final response."))
                round++
            }

            if (ResponseValidator.isValid(lastResponse)) {
                updateTrace(runId, JSONObject().apply { put("output", lastResponse) }, "success")
                return@withContext lastResponse
            }
            retryCount++
        }

        updateTrace(runId, JSONObject().apply { put("output", "FALLBACK_TRIGGERED") }, "error")
        return@withContext "I'm having trouble processing that right now. Please contact your care team if urgent."
    }

    /**
     * Public method to allow simple traces for connection tests
     */
    fun traceSimpleRun(runName: String, inputs: Map<String, Any>, output: String) {
        val runId = UUID.randomUUID().toString()
        performTrace(runId, runName, JSONObject(inputs))
        updateTrace(runId, JSONObject().apply { put("output", output) }, "success")
    }

    private fun performTrace(runId: String, name: String, inputs: JSONObject) {
        val apiKey = BuildConfig.LANGSMITH_API_KEY
        val project = BuildConfig.LANGSMITH_PROJECT
        
        if (apiKey.isBlank() || !apiKey.startsWith("lsv2_pt_")) {
            Log.w(LS_TAG, "Tracing skipped: Invalid API Key. Current key: '$apiKey'")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val body = JSONObject().apply {
                    put("id", runId)
                    put("name", name)
                    put("run_type", "chain")
                    put("inputs", inputs)
                    put("project_name", project)
                    put("start_time", Instant.now().toString())
                }.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("https://api.smith.langchain.com/runs")
                    .addHeader("x-api-key", apiKey)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) Log.i(LS_TAG, "Trace Started: $runId")
                    else Log.w(LS_TAG, "Trace Start Failed: ${resp.code} ${resp.body?.string()}")
                }
            } catch (e: Exception) { Log.e(LS_TAG, "Trace Exception: ${e.message}") }
        }
    }

    private fun updateTrace(runId: String, outputs: JSONObject, status: String) {
        val apiKey = BuildConfig.LANGSMITH_API_KEY
        if (apiKey.isBlank() || !apiKey.startsWith("lsv2_pt_")) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val body = JSONObject().apply {
                    put("outputs", outputs)
                    put("end_time", Instant.now().toString())
                }.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("https://api.smith.langchain.com/runs/$runId")
                    .addHeader("x-api-key", apiKey)
                    .patch(body)
                    .build()

                client.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) Log.i(LS_TAG, "Trace Updated ($status): $runId")
                    else Log.w(LS_TAG, "Trace Update Failed: ${resp.code}")
                }
            } catch (e: Exception) { Log.e(LS_TAG, "Trace Update Exception: ${e.message}") }
        }
    }

    fun parseToolCalls(text: String): List<ToolCall> {
        val calls = mutableListOf<ToolCall>()
        val regex = Regex("""TOOL_CALL:\s*(\{.*\})""")
        for (match in regex.findAll(text)) {
            try {
                val json = JSONObject(match.groupValues[1])
                val name = json.getString("name")
                val params = if (json.has("parameters")) {
                    val paramsObj = json.getJSONObject("parameters")
                    val map = mutableMapOf<String, Any?>()
                    for (key in paramsObj.keys()) { map[key] = paramsObj.get(key) }
                    map
                } else emptyMap()
                calls.add(ToolCall(name, params))
            } catch (e: Exception) { /* ignore */ }
        }
        return calls
    }

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
