package com.example.mytodoapp.utils

import com.example.mytodoapp.BuildConfig
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.io.IOException

/**
 * AI Helper for Groq API integration.
 * Handles text rewriting in different styles using llama-3.3-70b-versatile.
 */

enum class RewriteMode {
    DEFAULT, PROFESSIONAL, CASUAL
}

// Data classes for Groq API
data class GroqRequest(
    val model: String = "llama-3.3-70b-versatile",
    val messages: List<GroqMessage>,
    val temperature: Double = 0.7
)

data class GroqMessage(
    val role: String,
    val content: String
)

data class GroqResponse(
    val choices: List<GroqChoice>
)

data class GroqChoice(
    val message: GroqMessage
)

// Retrofit Interface
interface GroqApiService {
    @POST("chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") authHeader: String,
        @Body request: GroqRequest
    ): Response<GroqResponse>
}

object AiHelper {
    private const val BASE_URL = "https://api.groq.com/openai/v1/"
    private val apiKey by lazy { BuildConfig.GROQ_API_KEY }

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val apiService by lazy { retrofit.create(GroqApiService::class.java) }

    /**
     * Rewrites the input text based on the selected mode.
     */
    suspend fun rewriteText(input: String, mode: RewriteMode): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext "Error: API Key is missing. Please add it to local.properties."
        }

        try {
            val messages = buildPrompt(mode, input)
            val request = GroqRequest(messages = messages)
            val response = apiService.chatCompletions("Bearer $apiKey", request)

            if (response.isSuccessful) {
                val content = response.body()?.choices?.firstOrNull()?.message?.content
                content?.trim() ?: "Error: Empty response from AI."
            } else {
                when (response.code()) {
                    401 -> "Error: Invalid API Key (Unauthorized)."
                    429 -> "Error: AI rate limit reached. Please try again later."
                    500 -> "Error: AI server is currently down."
                    else -> "Error: AI call failed with code ${response.code()}."
                }
            }
        } catch (e: IOException) {
            "Error: No internet connection. Please check your network."
        } catch (e: Exception) {
            "Error: ${e.localizedMessage ?: "An unexpected error occurred."}"
        }
    }

    /**
     * Builds a structured prompt based on the rewrite mode and user input.
     * Integrates user text and strictly enforces no-explanation rules.
     */
    private fun buildPrompt(mode: RewriteMode, input: String): List<GroqMessage> {
        val system = when (mode) {
            RewriteMode.DEFAULT -> """
                You are a writing assistant.
                
                Your task is to improve the user's text for grammar, clarity, and structure.
                
                CRITICAL RULES:
                - Preserve meaning.
                - Do NOT translate the text.
                - Keep the tone natural and neutral (not formal, not overly casual).
                - Keep the rewritten text similar in length to the input (slightly shorter or longer is fine, but not significantly longer).
                - Preserve the user's original wording exactly as given. If a term is abbreviated in the input,
                  keep it abbreviated; if it is written in full, keep it in full. Do not expand, shorten, or alter any abbreviations or word forms.
                - Do not add extra information or explanations.
                - Do NOT expand the content.
                - Do NOT add new features, examples, or suggestions.
                - Do NOT convert it into an explanation or essay.
                - Only rewrite or improve the given text.
                - Return ONLY the rewritten text.
                """.trimIndent()

            RewriteMode.PROFESSIONAL -> """
                You are a professional editor.
                
                Rewrite the user's text into a formal, clear, workplace-ready version.
                
                CRITICAL RULES:
                - Preserve meaning.
                - Convert to formal professional English.
                - If input is in another language, translate into professional English.
                - Keep the output concise and direct.
                - Do NOT make it longer than the original text (slightly shorter is preferred if possible).
                - Preserve the user's original wording exactly as given. If a term is abbreviated in the input,
                  keep it abbreviated; if it is written in full, keep it in full. Do not expand, shorten, or alter any abbreviations or word forms.
                - Do not add explanations, extra details, or filler content.
                - Do NOT expand the content.
                - Do NOT add new features, examples, or suggestions.
                - Do NOT convert it into an explanation or essay.
                - Only rewrite or improve the given text.
                - Return ONLY the rewritten text.
                """.trimIndent()

            RewriteMode.CASUAL -> """
                You are a friendly writing assistant.
                
                Rewrite the user's text into a natural, casual, conversational tone.
                
                CRITICAL RULES:
                - Preserve meaning.
                - Keep the same language as input.
                - Do NOT translate the text.
                - Keep output short and natural.
                - Do NOT make the text longer than the original (slightly shorter or same length preferred).
                - Preserve the user's original wording exactly as given. If a term is abbreviated in the input,
                  keep it abbreviated; if it is written in full, keep it in full. Do not expand, shorten, or alter any abbreviations or word forms.
                - Do not add explanations or extra content.
                - Do NOT expand the content.
                - Do NOT add new features, examples, or suggestions.
                - Do NOT convert it into an explanation or essay.
                - Only rewrite or improve the given text.
                - Return ONLY the rewritten text.
                """.trimIndent()
        }

        val user = """
            INPUT TEXT:
            $input

            OUTPUT:
            Rewrite the above text according to rules.
        """.trimIndent()

        return listOf(
            GroqMessage("system", system),
            GroqMessage("user", user)
        )
    }
}
