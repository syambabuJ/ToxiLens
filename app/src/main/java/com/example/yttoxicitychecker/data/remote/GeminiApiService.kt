package com.example.yttoxicitychecker.data.remote

import retrofit2.http.*

interface GeminiApiService {
    @POST("v1beta/models/gemini-pro:generateContent")
    suspend fun analyzeText(
        @Query("key") key: String = ApiConstants.GEMINI_API_KEY,
        @Body request: GeminiRequest
    ): GeminiResponse
}

data class GeminiRequest(
    val contents: List<Content>
)

data class Content(
    val parts: List<Part>
)

data class Part(
    val text: String
)

data class GeminiResponse(
    val candidates: List<Candidate>
)

data class Candidate(
    val content: ContentResponse
)

data class ContentResponse(
    val parts: List<PartResponse>
)

data class PartResponse(
    val text: String
)