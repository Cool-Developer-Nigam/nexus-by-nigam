package com.nigdroid.journal

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class GeminiHelper {
    private val TAG = "GeminiHelper"
    private val apiService: GeminiApiService

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(GeminiApiService::class.java)
    }

    suspend fun sendMessage(userMessage: String): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "API Key: ${ApiConstants.GEMINI_API_KEY.take(10)}...")
                Log.d(TAG, "Sending message: $userMessage")

                val request = GeminiRequest(
                    contents = listOf(
                        Content(
                            parts = listOf(Part(text = userMessage))
                        )
                    )
                )

                // Use the correct model name - gemini-2.0-flash is the latest
                val model = "gemini-2.0-flash"

                Log.d(TAG, "Trying model: $model")

                val response = apiService.generateContent(
                    model = model,
                    apiKey = ApiConstants.GEMINI_API_KEY,
                    request = request
                )

                Log.d(TAG, "Response code: ${response.code()}")

                if (response.isSuccessful) {
                    val body = response.body()

                    if (body?.error != null) {
                        Log.e(TAG, "API Error: ${body.error?.message}")
                        return@withContext "API Error: ${body.error?.message}"
                    }

                    val responseText = body?.candidates?.firstOrNull()
                        ?.content?.parts?.firstOrNull()?.text

                    if (responseText != null) {
                        Log.d(TAG, "Success with model: $model")
                        Log.d(TAG, "Response: $responseText")
                        return@withContext responseText
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Error with model $model: ${response.code()} - $errorBody")
                    return@withContext "Error: ${response.code()} - Please check your API key"
                }

                return@withContext "Sorry, couldn't get a response from Gemini."

            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}", e)
                e.printStackTrace()
                return@withContext "Error: ${e.message}"
            }
        }
    }
}
