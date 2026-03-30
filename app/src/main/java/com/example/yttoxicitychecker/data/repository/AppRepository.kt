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
import kotlin.math.abs
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

    // ==================== ENHANCED RECOMMENDATION METHODS ====================

    suspend fun getToxicityBasedRecommendations(currentVideo: VideoData): List<VideoData> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Getting recommendations for: ${currentVideo.title} (Toxicity: ${currentVideo.toxicityScore})")

                val allVideos = mutableListOf<VideoData>()
                firebaseManager.getAllVideos().collect { videos ->
                    allVideos.addAll(videos)
                    Log.d(TAG, "Collected ${videos.size} videos from Firebase")
                }

                if (allVideos.size < 2) {
                    Log.d(TAG, "Not enough videos for recommendations")
                    return@withContext emptyList()
                }

                val candidates = allVideos.filter { it.videoId != currentVideo.videoId }
                
                if (candidates.isEmpty()) {
                    return@withContext emptyList()
                }

                // Calculate enhanced scores for each candidate
                val scored = candidates.map { video ->
                    val score = calculateEnhancedRecommendationScore(currentVideo, video)
                    Pair(video, score)
                }

                // Get top 5 recommendations with diversity
                val recommendations = getDiverseRecommendations(scored, currentVideo)
                
                Log.d(TAG, "Returning ${recommendations.size} diverse recommendations")
                recommendations

            } catch (e: Exception) {
                Log.e(TAG, "Error getting recommendations", e)
                emptyList()
            }
        }
    }

    private fun calculateEnhancedRecommendationScore(current: VideoData, candidate: VideoData): Float {
        var score = 0f
        
        // FACTOR 1: Toxicity Difference (35% weight) - More dynamic
        val toxicityDifference = 1f - abs(current.toxicityScore - candidate.toxicityScore)
        score += toxicityDifference * 0.35f
        
        // FACTOR 2: Opposite Toxicity Bonus (20% weight) - Shows variety
        // If current is highly toxic, recommend safe videos; if safe, recommend popular videos
        val oppositeBonus = when {
            current.toxicityScore > 0.6 && candidate.toxicityScore < 0.2 -> 0.2f  // Recommend safe alternatives
            current.toxicityScore < 0.2 && candidate.toxicityScore > 0.4 -> 0.15f // Recommend more engaging content
            else -> 0f
        }
        score += oppositeBonus
        
        // FACTOR 3: Engagement Score (15% weight)
        val engagementScore = (candidate.totalComments / 5000f).coerceIn(0f, 0.15f)
        score += engagementScore
        
        // FACTOR 4: Safety Score (20% weight) - Prefer safer content
        val safetyScore = (1f - candidate.toxicityScore) * 0.2f
        score += safetyScore
        
        // FACTOR 5: Random Diversity (10% weight) - Ensures different recommendations each time
        val randomSeed = Random.nextInt(0, 100) / 100f
        score += randomSeed * 0.1f
        
        return score.coerceIn(0f, 1f)
    }

    private fun getDiverseRecommendations(
        scored: List<Pair<VideoData, Float>>, 
        currentVideo: VideoData
    ): List<VideoData> {
        // Filter by minimum score threshold (dynamic based on current video)
        val threshold = when {
            currentVideo.toxicityScore > 0.6 -> 0.3f  // Lower threshold for toxic videos (show more options)
            currentVideo.toxicityScore > 0.3 -> 0.4f  // Medium threshold
            else -> 0.5f  // Higher threshold for safe videos (show only relevant)
        }
        
        val filtered = scored.filter { it.second > threshold }
        
        // Group recommendations by toxicity level for diversity
        val lowToxicity = filtered.filter { it.first.toxicityScore < 0.2 }
        val mediumToxicity = filtered.filter { it.first.toxicityScore in 0.2..0.5 }
        val highToxicity = filtered.filter { it.first.toxicityScore > 0.5 }
        
        val recommendations = mutableListOf<VideoData>()
        
        // Add diverse mix: 3 low, 1 medium, 1 high (for toxic videos show more safe)
        when {
            currentVideo.toxicityScore > 0.6 -> {
                // High toxicity video - show mostly safe alternatives
                recommendations.addAll(lowToxicity.take(3).map { it.first })
                recommendations.addAll(mediumToxicity.take(1).map { it.first })
                recommendations.addAll(highToxicity.take(1).map { it.first })
            }
            currentVideo.toxicityScore > 0.3 -> {
                // Medium toxicity - balanced mix
                recommendations.addAll(lowToxicity.take(2).map { it.first })
                recommendations.addAll(mediumToxicity.take(2).map { it.first })
                recommendations.addAll(highToxicity.take(1).map { it.first })
            }
            else -> {
                // Low toxicity - show similar safe content
                recommendations.addAll(lowToxicity.take(4).map { it.first })
                recommendations.addAll(mediumToxicity.take(1).map { it.first })
            }
        }
        
        // If not enough recommendations, fill with any available
        if (recommendations.size < 5) {
            val remaining = filtered.filter { !recommendations.contains(it.first) }
                .sortedByDescending { it.second }
                .take(5 - recommendations.size)
                .map { it.first }
            recommendations.addAll(remaining)
        }
        
        return recommendations.take(5).map { video ->
            video.copy(
                isRecommended = true,
                recommendationReason = getEnhancedRecommendationReason(video, currentVideo)
            )
        }
    }

    private fun getEnhancedRecommendationReason(recommended: VideoData, current: VideoData): String {
        return when {
            // Opposite recommendation strategies
            current.toxicityScore > 0.6 && recommended.toxicityScore < 0.2 -> 
                "✅ Safer alternative (${(recommended.toxicityScore * 100).toInt()}% toxicity)"
            
            current.toxicityScore < 0.2 && recommended.toxicityScore > 0.4 -> 
                "📊 More engaging content with ${(recommended.toxicityScore * 100).toInt()}% toxicity"
            
            // Toxicity based
            recommended.toxicityScore < current.toxicityScore -> 
                "📉 ${(current.toxicityScore * 100 - recommended.toxicityScore * 100).toInt()}% less toxic"
            
            recommended.toxicityScore < 0.2 -> 
                "✅ Very safe content (${(recommended.toxicityScore * 100).toInt()}% toxicity)"
            
            // Engagement based
            recommended.totalComments > 10000 -> 
                "🔥 Highly popular (${recommended.totalComments} comments)"
            
            recommended.totalComments > 5000 -> 
                "⭐ Popular with positive community"
            
            // Default
            else -> "🎯 Recommended for you"
        }
    }
}
