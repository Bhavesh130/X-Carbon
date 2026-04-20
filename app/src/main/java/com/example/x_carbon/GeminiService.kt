package com.example.x_carbon

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

object GeminiService {

    private const val TAG = "GeminiService"
    private const val MODEL_NAME = "gemini-2.0-flash"

    // Read API key from BuildConfig
    private val API_KEY: String = try {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isNotEmpty()) {
            val masked = key.take(4) + "..." + key.takeLast(4)
            Log.d(TAG, "Gemini API key loaded: $masked")
        } else {
            Log.e(TAG, "Gemini API key is EMPTY in BuildConfig")
        }
        key
    } catch (e: Exception) {
        Log.e(TAG, "BuildConfig access failed", e)
        ""
    }

    // Using the official SDK instead of manual HTTP calls to avoid URL/404 issues
    private val generativeModel by lazy {
        GenerativeModel(
            modelName = MODEL_NAME,
            apiKey = API_KEY,
            generationConfig = generationConfig {
                temperature = 0.7f
                topK = 40
                topP = 0.95f
                maxOutputTokens = 1024
            }
        )
    }

    private val responseCache = mutableMapOf<String, CachedResponse>()
    private data class CachedResponse(val answer: String, val timestamp: Long)

    private val offlineResponses = mapOf(
        Regex("carbon|footprint|emission|co2", RegexOption.IGNORE_CASE) to
                "Your carbon footprint depends on your lifestyle. To reduce it: use public transport, eat less meat, save energy at home, and recycle.",
        Regex("reduce|lower|decrease", RegexOption.IGNORE_CASE) to
                "Effective ways to reduce footprint:\n• Walk or bike\n• Use LED bulbs\n• Eat more plants\n• Reduce air travel\n• Recycle waste",
        Regex("hello|hi|hey", RegexOption.IGNORE_CASE) to
                "Hello! I'm your carbon footprint assistant. How can I help you today?"
    )

    suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        if (API_KEY.isBlank()) return@withContext getOfflineFallback(prompt)

        val cacheKey = prompt.take(100)
        responseCache[cacheKey]?.let {
            if (System.currentTimeMillis() - it.timestamp < 3600000) return@withContext it.answer
        }

        for (attempt in 1..2) {
            try {
                val response = generativeModel.generateContent(prompt)
                val text = response.text
                if (!text.isNullOrBlank()) {
                    responseCache[cacheKey] = CachedResponse(text, System.currentTimeMillis())
                    return@withContext text
                }
            } catch (e: Exception) {
                Log.w(TAG, "SDK attempt $attempt failed: ${e.message}")
                if (attempt < 2) delay(1000)
            }
        }
        getOfflineFallback(prompt)
    }

    private fun getOfflineFallback(prompt: String): String {
        for ((pattern, response) in offlineResponses) {
            if (pattern.containsMatchIn(prompt)) return response
        }
        return "I'm your carbon assistant! I can help you reduce your environmental impact. (Note: currently in offline mode)."
    }

    suspend fun askQuestion(userQuestion: String, context: String): String {
        val fullPrompt = """
            You are a helpful carbon footprint expert.
            Answer concisely and accurately. Do not use markdown or asterisks.
            Context: $context
            User Question: $userQuestion
        """.trimIndent()
        return generate(fullPrompt)
    }
}
