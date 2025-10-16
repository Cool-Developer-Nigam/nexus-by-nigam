
// API Service Interface
package com.nigdroid.journal

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}

// Request Data Classes
data class GeminiRequest(
    val contents: List<Content>
)

data class Content(
    val parts: List<Part>
)

data class Part(
    val text: String
)

// Response Data Classes
data class GeminiResponse(
    val candidates: List<Candidate>?,
    val error: ErrorResponse?
)

data class Candidate(
    val content: Content?,
    val finishReason: String?
)

data class ErrorResponse(
    val code: Int?,
    val message: String?,
    val status: String?
)
