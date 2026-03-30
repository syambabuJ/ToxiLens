package com.example.yttoxicitychecker.data.repository

import android.util.Log
import com.example.yttoxicitychecker.data.model.Comment
import com.example.yttoxicitychecker.data.model.ToxicityResult
import com.example.yttoxicitychecker.data.model.VideoData
import com.example.yttoxicitychecker.data.remote.*
import com.example.yttoxicitychecker.firebase.FirebaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.random.Random

class AppRepository(
    private val firebaseManager: FirebaseManager
) {
    private val TAG = "AppRepository"

    suspend fun fetchComments(videoId: String): List<Comment> = withContext(Dispatchers.IO) {
        try {
            val response = RetrofitClient.youtubeApi.getComments(videoId = videoId)
            response.items.map { item ->
                val snippet = item.snippet.topLevelComment.snippet
                Comment(
                    id = System.currentTimeMillis().toString(),
                    text = snippet.textDisplay,
                    author = snippet.authorDisplayName,
                    publishedAt = snippet.publishedAt,
                    likeCount = snippet.likeCount
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchComments error", e)
            emptyList()
        }
    }

    suspend fun fetchVideoDetails(videoId: String): Pair<String, String> = withContext(Dispatchers.IO) {
        try {
            val response = RetrofitClient.youtubeApi.getVideoDetails(id = videoId)
            val video = response.items.firstOrNull()
            if (video != null) {
                Pair(video.snippet.title, video.snippet.channelTitle)
            } else {
                Pair("YouTube Video", "Unknown Channel")
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchVideoDetails error", e)
            Pair("YouTube Video", "Unknown Channel")
        }
    }

    suspend fun analyzeComment(comment: Comment): Comment = withContext(Dispatchers.IO) {
        try {
            val prompt = """
You are a toxicity detection AI. Analyze this comment and return JSON with MULTI-LABEL toxicity detection.

Required JSON fields:
{
  "isToxic": boolean,
  "toxicityScore": float (0-1),
  "toxicityTypes": array of strings from: ["hate_speech", "obscene", "threat", "insult", "identity_attack", "harassment", "toxic", "severe_toxic"],
  "primaryType": string,
  "sentiment": "Positive" or "Neutral" or "Negative",
  "category": "Safe" or "Offensive" or "Harassment" or "Hate Speech",
  "reasoning": string
}

Comment: "${comment.text}"

Return ONLY JSON.
""".trimIndent()

            val request = GeminiRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt))))
            )

            val response = RetrofitClient.geminiApi.analyzeText(request = request)
            val text = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""

            val toxicityResult = parseMultiLabelToxicityResponse(text)
            comment.copy(toxicityResult = toxicityResult)

        } catch (e: Exception) {
            Log.e(TAG, "analyzeComment error", e)
            comment.copy(
                toxicityResult = fallbackMultiLabelDetection(comment.text)
            )
        }
    }

    private fun parseMultiLabelToxicityResponse(response: String): ToxicityResult {
        return try {
            var cleanResponse = response.trim()
            if (cleanResponse.startsWith("```json")) cleanResponse = cleanResponse.substring(7)
            if (cleanResponse.startsWith("```")) cleanResponse = cleanResponse.substring(3)
            if (cleanResponse.endsWith("```")) cleanResponse = cleanResponse.substring(0, cleanResponse.length - 3)
            cleanResponse = cleanResponse.trim()

            val json = JSONObject(cleanResponse)

            val toxicityTypes = mutableListOf<String>()
            val typesArray = json.optJSONArray("toxicityTypes")
            if (typesArray != null) {
                for (i in 0 until typesArray.length()) {
                    toxicityTypes.add(typesArray.getString(i))
                }
            }

            ToxicityResult(
                isToxic = json.optBoolean("isToxic", false),
                toxicityScore = json.optDouble("toxicityScore", 0.0).toFloat(),
                sentiment = json.optString("sentiment", "Neutral"),
                category = json.optString("category", "Safe"),
                reasoning = json.optString("reasoning", ""),
                toxicityTypes = toxicityTypes,
                primaryType = json.optString("primaryType", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseMultiLabelToxicityResponse error", e)
            fallbackMultiLabelDetection(response)
        }
    }

    private fun fallbackMultiLabelDetection(text: String): ToxicityResult {
        val lowerText = text.lowercase()
        val toxicityTypes = mutableListOf<String>()

        val hateKeywords = listOf("brahmin", "dalit", "muslim", "hindu", "jew", "christian")
        val obsceneKeywords = listOf("fuck", "shit", "damn", "bitch", "asshole")
        val threatKeywords = listOf("kill", "die", "hurt", "destroy", "regret")
        val insultKeywords = listOf("stupid", "idiot", "dumb", "moron", "fool")

        if (hateKeywords.any { lowerText.contains(it) }) toxicityTypes.add("hate_speech")
        if (obsceneKeywords.any { lowerText.contains(it) }) toxicityTypes.add("obscene")
        if (threatKeywords.any { lowerText.contains(it) }) toxicityTypes.add("threat")
        if (insultKeywords.any { lowerText.contains(it) }) toxicityTypes.add("insult")

        val isToxic = toxicityTypes.isNotEmpty()

        return ToxicityResult(
            isToxic = isToxic,
            toxicityScore = if (isToxic) 0.7f else 0.1f,
            sentiment = if (isToxic) "Negative" else "Neutral",
            category = when {
                "hate_speech" in toxicityTypes -> "Hate Speech"
                isToxic -> "Offensive"
                else -> "Safe"
            },
            reasoning = "Detected: ${toxicityTypes.joinToString(", ")}",
            toxicityTypes = toxicityTypes,
            primaryType = toxicityTypes.firstOrNull() ?: ""
        )
    }

    suspend fun analyzeAllComments(comments: List<Comment>): List<Comment> = withContext(Dispatchers.IO) {
        val deferredResults = comments.map { comment ->
            async { analyzeComment(comment) }
        }
        deferredResults.awaitAll()
    }

    suspend fun saveVideoAnalysis(videoData: VideoData) {
        try {
            firebaseManager.saveVideoData(videoData)
            Log.d(TAG, "Video saved: ${videoData.videoId}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving video", e)
            throw e
        }
    }

    fun getVideoHistory(): Flow<List<VideoData>> = firebaseManager.getVideoHistory()

    suspend fun getVideoData(videoId: String): VideoData? = firebaseManager.getVideoData(videoId)

    // ==================== RECOMMENDATION METHODS ====================

    suspend fun getToxicityBasedRecommendations(currentVideo: VideoData): List<VideoData> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Getting recommendations for: ${currentVideo.title}")

                val allVideos = mutableListOf<VideoData>()
                firebaseManager.getAllVideos().collect { videos ->
                    allVideos.addAll(videos)
                    Log.d(TAG, "Collected ${videos.size} videos from Firebase")
                }

                Log.d(TAG, "Total videos in Firebase: ${allVideos.size}")

                if (allVideos.size < 2) {
                    Log.d(TAG, "Not enough videos for recommendations. Need at least 2, have ${allVideos.size}")
                    return@withContext emptyList()
                }

                val candidates = allVideos.filter { it.videoId != currentVideo.videoId }
                Log.d(TAG, "Candidate videos after removing current: ${candidates.size}")

                if (candidates.isEmpty()) {
                    return@withContext emptyList()
                }

                val scored = candidates.map { video ->
                    val score = calculateRecommendationScore(currentVideo, video)
                    Log.d(TAG, "Video: ${video.title}, Score: $score")
                    Pair(video, score)
                }

                val recommendations = scored
                    .filter { it.second > 0.3f }
                    .sortedByDescending { it.second }
                    .take(5)
                    .map {
                        it.first.copy(
                            isRecommended = true,
                            recommendationReason = getRecommendationReason(it.first, currentVideo)
                        )
                    }

                Log.d(TAG, "Returning ${recommendations.size} recommendations")
                recommendations

            } catch (e: Exception) {
                Log.e(TAG, "Error getting recommendations", e)
                emptyList()
            }
        }
    }

    private fun calculateRecommendationScore(current: VideoData, candidate: VideoData): Float {
        var score = 0f

        val toxicityScore = (1f - candidate.toxicityScore) * 0.4f
        score += toxicityScore

        val toxicityDiff = 1f - kotlin.math.abs(current.toxicityScore - candidate.toxicityScore)
        score += toxicityDiff * 0.3f

        val engagementScore = (candidate.totalComments / 10000f).coerceIn(0f, 0.2f)
        score += engagementScore

        score += Random.nextFloat() * 0.1f

        return score.coerceIn(0f, 1f)
    }

    private fun getRecommendationReason(recommended: VideoData, current: VideoData): String {
        return when {
            recommended.toxicityScore < 0.2 -> "✅ Much safer content (${(recommended.toxicityScore * 100).toInt()}% toxicity)"
            recommended.toxicityScore < current.toxicityScore -> "📉 Lower toxicity than current video"
            recommended.totalComments > 5000 -> "🔥 Popular with positive community"
            else -> "🎯 You might enjoy this content"
        }
    }
}