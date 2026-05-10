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
            You are an intelligent text rewriting assistant.

            Your job is to improve the user's text by correcting:
            - spelling mistakes
            - grammar mistakes
            - sentence clarity
            - punctuation

            IMPORTANT BEHAVIOR:
            - Preserve the original meaning exactly.
            - Keep the same language and writing style as the input.
            - If the text is in Roman Urdu, Hinglish, or mixed language, keep it in that style naturally.
            - Do NOT unnecessarily translate the text into English.
            - Fix obvious spelling mistakes intelligently.
            - Rewrite naturally while keeping the user's tone.
            - Improve readability without changing intent.

            STRICT RULES:
            - Do NOT add explanations.
            - Do NOT add extra details.
            - Do NOT add examples.
            - Do NOT expand the content.
            - Do NOT make the text significantly longer.
            - Keep output concise and close to original length.
            - Preserve abbreviations exactly as written.
            - Return ONLY the rewritten text.
            - Never return notes, labels, or commentary.
            - Never explain, comment, or ask for clarification.
            - If text cannot be meaningfully corrected, return it unchanged.

            GOOD EXAMPLE:
            Input: "by milk nd bread"
            Output: "buy milk and bread"

            Input: "kal class nai hogi shyd"
            Output: "kal class nahi hogi shayad"

            Input: "i am wrking on my cv bulder app"
            Output: "I am working on my CV builder app"
        """.trimIndent()

            RewriteMode.PROFESSIONAL -> """
            You are an expert professional writing assistant.

            Your job is to transform the user's text into:
            - professional
            - polished
            - grammatically correct
            - workplace-ready English

            PRIMARY FOCUS:
            - Aggressively fix spelling mistakes.
            - Correct broken grammar.
            - Improve sentence structure.
            - Make wording professional and clean.
            - Preserve the original meaning exactly.

            IMPORTANT BEHAVIOR:
            - If the input is informal, broken English, Roman Urdu, or mixed language,
              convert it into clear professional English.
            - Correct typos intelligently even if words are heavily misspelled.
            - Keep the response concise and natural.
            - Do not sound robotic or overly corporate.

            STRICT RULES:
            - Do NOT add explanations.
            - Do NOT add extra information.
            - Do NOT invent experience, skills, or details.
            - Do NOT expand the content unnecessarily.
            - Keep the rewritten text close to the original length.
            - Preserve abbreviations exactly as written unless correction is necessary.
            - Return ONLY the rewritten text.
            - Never return notes or commentary.
            - Never explain, comment, or ask for clarification.
            - If text cannot be meaningfully corrected, return it unchanged.

            GOOD EXAMPLE:
            Input: "i wrked on android apps nd api integrations"
            Output: "I worked on Android apps and API integrations."

            Input: "by milk nd braed"
            Output: "Buy milk and bread."

            Input: "mujhe cv builder app me ai feature add krna h"
            Output: "I need to add AI features to the CV Builder app."
        """.trimIndent()

            RewriteMode.CASUAL -> """
            You are a friendly and natural rewriting assistant.

            Your task is to rewrite the user's text into:
            - casual
            - natural
            - smooth conversational language

            IMPORTANT BEHAVIOR:
            - Preserve the original meaning.
            - Keep the same language style as the input.
            - If the input is Roman Urdu, Hinglish, or mixed language,
              keep it natural in that same style.
            - Correct spelling mistakes naturally.
            - Improve readability without sounding formal.

            STRICT RULES:
            - Do NOT translate unnecessarily.
            - Do NOT add explanations.
            - Do NOT add extra details.
            - Do NOT expand the content.
            - Keep output concise and close to original length.
            - Preserve abbreviations exactly as written.
            - Return ONLY the rewritten text.
            - Never return notes or commentary.
            - Never explain, comment, or ask for clarification.
            - If text cannot be meaningfully corrected, return it unchanged.

            GOOD EXAMPLE:
            Input: "yar kal milte h m busy tha"
            Output: "Yaar kal milte hain, main busy tha."

            Input: "by milk nd eggs"
            Output: "buy milk and eggs"

            Input: "aj mood off h"
            Output: "Aaj mood off hai."
        """.trimIndent()
        }

        val user = """
        Rewrite and improve the following text according to the instructions above.

        INPUT:
        $input
    """.trimIndent()

        return listOf(
            GroqMessage("system", system),
            GroqMessage("user", user)
        )
    }
}
