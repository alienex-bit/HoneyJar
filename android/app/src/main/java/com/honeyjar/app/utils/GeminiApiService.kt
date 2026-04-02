package com.honeyjar.app.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Thin wrapper around the Google Gemini API (Generative Language API).
 * No SDK dependency — plain HttpURLConnection keeps the APK lean.
 *
 * Usage:
 *   val result = GeminiApiService.sendMessage(apiKey, systemPrompt, messages)
 *   result.onSuccess { text -> ... }
 *         .onFailure { error -> ... }
 */
object GeminiApiService {

    private const val TAG = "HoneyJar-AI"
    private const val MODEL = "gemini-1.5-flash"
    private const val ENDPOINT_BASE = "https://generativelanguage.googleapis.com/v1/models"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 30_000

    /**
     * A single turn in the conversation history sent to the API.
     * Role must be "user" or "model".
     */
    data class Message(val role: String, val content: String)

    /**
     * Sends a request to the Gemini API.
     *
     * @param apiKey      The user's Google Gemini API key.
     * @param systemPrompt The system prompt describing the assistant's role and data context.
     * @param history     The full conversation history (user + model turns).
     * @return            Result wrapping the model's reply text, or an exception on failure.
     */
    suspend fun sendMessage(
        apiKey: String,
        systemPrompt: String,
        history: List<Message>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val urlString = "$ENDPOINT_BASE/$MODEL:generateContent?key=$apiKey"
            val body = buildRequestBody(systemPrompt, history)
            
            Log.d(TAG, "Request URL: $ENDPOINT_BASE/$MODEL:generateContent?key=***")
            Log.d(TAG, "Request Body: $body")

            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-goog-api-client", "gen-lang-client-0208688752")
                doOutput = true
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body) }

            val responseCode = conn.responseCode
            val responseText = if (responseCode == 200) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
            }

            if (responseCode != 200) {
                Log.e(TAG, "Gemini API error $responseCode: $responseText")
                return@withContext Result.failure(Exception(parseApiError(responseText, responseCode)))
            }

            Log.d(TAG, "Response: $responseText")
            val reply = parseResponseText(responseText)
            Result.success(reply)

        } catch (e: Exception) {
            Log.e(TAG, "Gemini request failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun buildRequestBody(systemPrompt: String, history: List<Message>): String {
        val root = JSONObject()

        // System Instruction (Canonical REST API: systemInstruction)
        if (systemPrompt.isNotBlank()) {
            val systemInstruction = JSONObject()
            val parts = JSONArray()
            parts.put(JSONObject().put("text", systemPrompt))
            systemInstruction.put("parts", parts)
            root.put("systemInstruction", systemInstruction)
        }

        // Contents (History)
        val contents = JSONArray()
        history.forEach { msg ->
            val turn = JSONObject()
            turn.put("role", if (msg.role == "assistant") "model" else msg.role)
            val parts = JSONArray()
            parts.put(JSONObject().put("text", msg.content))
            turn.put("parts", parts)
            contents.put(turn)
        }
        root.put("contents", contents)

        // Generation Config (Canonical REST API: generationConfig)
        val config = JSONObject()
        config.put("maxOutputTokens", 1024)
        config.put("temperature", 0.7)
        root.put("generationConfig", config)

        return root.toString()
    }

    private fun parseResponseText(json: String): String {
        val root = JSONObject(json)
        val candidates = root.getJSONArray("candidates")
        if (candidates.length() == 0) return ""
        
        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.getJSONObject("content")
        val parts = content.getJSONArray("parts")
        
        return (0 until parts.length())
            .map { parts.getJSONObject(it) }
            .filter { it.has("text") }
            .joinToString("") { it.getString("text") }
            .trim()
    }

    private fun parseApiError(json: String, code: Int): String {
        return try {
            val error = JSONObject(json).getJSONObject("error")
            val msg = error.getString("message")
            "Gemini API error ($code): $msg"
        } catch (_: Exception) {
            "Gemini API error ($code)"
        }
    }
}
