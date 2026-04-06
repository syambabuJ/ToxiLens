package com.toxilens.yttoxicitychecker.data.repository

import android.util.Log
import com.toxilens.yttoxicitychecker.data.model.ChannelData
import com.toxilens.yttoxicitychecker.data.model.Comment
import com.toxilens.yttoxicitychecker.data.model.ToxicityResult
import com.toxilens.yttoxicitychecker.data.model.VideoData
import com.toxilens.yttoxicitychecker.data.model.VideoSummary
import com.toxilens.yttoxicitychecker.data.remote.*
import com.toxilens.yttoxicitychecker.firebase.FirebaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.HttpException
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
You are an advanced toxicity detection AI that understands FULL CONTEXT and NUANCE of comments.

═══════════════════════════════════════════════════════════════════════════════
⚠️ CRITICAL RULES - YOU MUST FOLLOW:
═══════════════════════════════════════════════════════════════════════════════

1. UNDERSTAND FULL CONTEXT: Read the entire comment, understand sarcasm, jokes, frustration, and genuine hate speech.
2. CLASSIFICATION CATEGORIES: Use ONLY "Safe", "Toxic", or "Neutral" (NO OTHER CATEGORIES)
3. SAFE: Completely positive, supportive, constructive feedback, or neutral questions (toxicityScore < 0.2)
4. TOXIC: Contains hate speech, harassment, threats, insults, profanity, or attacks (toxicityScore > 0.6)
5. NEUTRAL: Mild criticism, disagreement, factual statements, or comments with no strong emotion (toxicityScore 0.2-0.6)

═══════════════════════════════════════════════════════════════════════════════
TOXICITY SCORING GUIDELINES:
═══════════════════════════════════════════════════════════════════════════════
- 0.0-0.2: SAFE (Positive, supportive, constructive, neutral questions)
- 0.3-0.6: NEUTRAL (Mild criticism, disagreement, factual, no strong emotion)
- 0.7-1.0: TOXIC (Hate speech, harassment, threats, insults, profanity)

═══════════════════════════════════════════════════════════════════════════════
TOXICITY TYPES (for toxic comments only):
═══════════════════════════════════════════════════════════════════════════════
- "hate_speech": Attacks based on race, religion, gender, orientation
- "obscene": Profanity, vulgar language
- "threat": Direct or indirect threats of violence
- "insult": Name-calling, personal attacks
- "identity_attack": Attacks on someone's identity characteristics
- "harassment": Repeated targeted attacks, bullying
- "toxic": General harmful content
- "severe_toxic": Extreme toxicity, violence encouragement

═══════════════════════════════════════════════════════════════════════════════
EXAMPLES FOR CALIBRATION:
═══════════════════════════════════════════════════════════════════════════════

SAFE EXAMPLES (Score 0.0-0.2):
- "Great video! Really informative" → Safe, 0.05
- "Thank you for sharing this" → Safe, 0.05
- "Can you explain this in more detail?" → Safe, 0.10
- "I appreciate your hard work" → Safe, 0.05

NEUTRAL EXAMPLES (Score 0.3-0.6):
- "This could have been better" → Neutral, 0.40
- "I disagree with your opinion" → Neutral, 0.45
- "Not what I expected" → Neutral, 0.35
- "Interesting perspective but I see it differently" → Neutral, 0.50
- "The editing needs improvement" → Neutral, 0.45

TOXIC EXAMPLES (Score 0.7-1.0):
- "You're an idiot, delete this garbage" → Toxic, 0.85
- "All [group] people are criminals" → Toxic, 0.95
- "F*** you and your stupid video" → Toxic, 0.88
- "I hope you get hurt" → Toxic, 0.92
- "Go kill yourself" → Toxic, 0.98

═══════════════════════════════════════════════════════════════════════════════
CONTEXT AWARENESS:
═══════════════════════════════════════════════════════════════════════════════
- Sarcasm detection: "Oh great, another ad" → Neutral/Toxic based on context
- Frustration: "This is so frustrating" → Neutral (0.4-0.5)
- Constructive criticism: "The audio quality could be better" → Safe/Neutral
- Cultural context: Consider common phrases and their intent

═══════════════════════════════════════════════════════════════════════════════
NOW ANALYZE THIS COMMENT:
"${comment.text}"

Return ONLY JSON with these exact fields:
{
  "isToxic": boolean,
  "toxicityScore": float (0-1),
  "toxicityTypes": array of strings (empty if not toxic),
  "primaryType": string (empty if not toxic),
  "sentiment": "Positive" or "Neutral" or "Negative",
  "category": "Safe" or "Neutral" or "Toxic",
  "reasoning": "brief explanation"
}

IMPORTANT: Use ONLY "Safe", "Neutral", or "Toxic" for category. Do not use "Offensive" or "Harassment" or "Hate Speech".
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

            // Map the category from API response to app's categories
            val apiCategory = json.optString("category", "Safe")
            val mappedCategory = when (apiCategory.lowercase()) {
                "toxic" -> "Toxic"
                "offensive" -> "Neutral"  // Map offensive to Neutral
                "harassment" -> "Toxic"
                "hate speech" -> "Toxic"
                "neutral" -> "Neutral"
                else -> "Safe"
            }

            // Calculate isToxic based on toxicityScore
            val toxicityScore = json.optDouble("toxicityScore", 0.0).toFloat()
            val isToxic = toxicityScore > 0.6 || apiCategory.lowercase() in listOf("toxic", "harassment", "hate speech")

            ToxicityResult(
                isToxic = isToxic,
                toxicityScore = toxicityScore,
                sentiment = json.optString("sentiment", "Neutral"),
                category = mappedCategory,
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

        // Check for hate speech keywords
        val hateKeywords = listOf("brahmin", "dalit", "muslim", "hindu", "jew", "christian", "terrorist", "nazi", "racist")
        val obsceneKeywords = listOf("fuck", "shit", "damn", "bitch", "asshole", "cunt", "dick")
        val threatKeywords = listOf("kill", "die", "hurt", "destroy", "regret", "suffer")
        val insultKeywords = listOf("stupid", "idiot", "dumb", "moron", "fool", "loser", "worthless", "useless")

        var isToxic = false
        var toxicityScore = 0.1f
        var category = "Safe"
        var sentiment = "Neutral"

        when {
            hateKeywords.any { lowerText.contains(it) } -> {
                toxicityTypes.add("hate_speech")
                isToxic = true
                toxicityScore = 0.85f
                category = "Toxic"
                sentiment = "Negative"
            }
            threatKeywords.any { lowerText.contains(it) } -> {
                toxicityTypes.add("threat")
                isToxic = true
                toxicityScore = 0.80f
                category = "Toxic"
                sentiment = "Negative"
            }
            obsceneKeywords.any { lowerText.contains(it) } -> {
                toxicityTypes.add("obscene")
                isToxic = true
                toxicityScore = 0.75f
                category = "Toxic"
                sentiment = "Negative"
            }
            insultKeywords.any { lowerText.contains(it) } -> {
                toxicityTypes.add("insult")
                isToxic = true
                toxicityScore = 0.70f
                category = "Toxic"
                sentiment = "Negative"
            }
            lowerText.contains("bad") || lowerText.contains("terrible") || lowerText.contains("awful") -> {
                // Mild criticism - Neutral
                isToxic = false
                toxicityScore = 0.45f
                category = "Neutral"
                sentiment = "Neutral"
            }
            lowerText.contains("good") || lowerText.contains("great") || lowerText.contains("amazing") || lowerText.contains("love") -> {
                // Positive - Safe
                isToxic = false
                toxicityScore = 0.10f
                category = "Safe"
                sentiment = "Positive"
            }
            else -> {
                // Default to Safe for unknown content
                isToxic = false
                toxicityScore = 0.15f
                category = "Safe"
                sentiment = "Neutral"
            }
        }

        return ToxicityResult(
            isToxic = isToxic,
            toxicityScore = toxicityScore,
            sentiment = sentiment,
            category = category,
            reasoning = if (toxicityTypes.isNotEmpty()) "Detected: ${toxicityTypes.joinToString(", ")}" else "No harmful content detected",
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

    // ==================== CHANNEL ANALYSIS METHODS ====================

    suspend fun analyzeChannel(channelInput: String): ChannelData = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== STARTING CHANNEL ANALYSIS ===")
            Log.d(TAG, "Channel Input: $channelInput")

            // Step 1: Get Channel ID
            val channelId = resolveChannelId(channelInput)
            if (channelId == null) {
                throw Exception("Channel not found. Please check the channel name or URL.")
            }

            Log.d(TAG, "Resolved Channel ID: $channelId")

            // Step 2: Get Channel Statistics
            var subscriberCount = 0L
            var videoCount = 0L
            var viewCount = 0L
            var channelName = ""
            var thumbnailUrl = ""
            var customUrl = ""

            try {
                val channelResponse = RetrofitClient.youtubeApi.getChannelDetails(id = channelId)
                val channel = channelResponse.items?.firstOrNull()
                if (channel != null) {
                    channelName = channel.snippet.title
                    thumbnailUrl = channel.snippet.thumbnails?.medium?.url ?: ""
                    customUrl = channel.snippet.customUrl ?: ""
                    subscriberCount = channel.statistics.subscriberCount?.toLongOrNull() ?: 0
                    videoCount = channel.statistics.videoCount?.toLongOrNull() ?: 0
                    viewCount = channel.statistics.viewCount?.toLongOrNull() ?: 0
                    Log.d(TAG, "Channel Stats - Name: $channelName, Subs: $subscriberCount, Videos: $videoCount, Views: $viewCount, CustomUrl: $customUrl")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching channel details: ${e.message}", e)
            }

            // Step 3: Get videos from channel
            val videosResponse = RetrofitClient.youtubeApi.getChannelVideos(
                channelId = channelId,
                maxResults = 20
            )

            val videoItems = videosResponse.items
            if (videoItems == null || videoItems.isEmpty()) {
                throw Exception("No videos found for this channel")
            }

            val videoIds = videoItems.mapNotNull { it.id.videoId }
            Log.d(TAG, "Found ${videoIds.size} videos to analyze")

            // Step 4: Analyze videos
            var totalToxic = 0
            var totalNeutral = 0
            var totalSafe = 0
            var totalComments = 0
            var totalToxicityScore = 0f
            val analyzedVideos = mutableListOf<String>()
            val videoDataList = mutableListOf<VideoData>()

            for (videoId in videoIds.take(3)) {
                try {
                    Log.d(TAG, "Analyzing video: $videoId")
                    val comments = fetchComments(videoId)
                    if (comments.isEmpty()) continue

                    val analyzedComments = analyzeAllComments(comments)

                    val toxicCount = analyzedComments.count { it.toxicityResult?.category == "Toxic" }
                    val neutralCount = analyzedComments.count { it.toxicityResult?.category == "Neutral" }
                    val safeCount = analyzedComments.count { it.toxicityResult?.category == "Safe" }

                    totalToxic += toxicCount
                    totalNeutral += neutralCount
                    totalSafe += safeCount
                    totalComments += analyzedComments.size

                    val toxicityScore = if (analyzedComments.isNotEmpty()) {
                        toxicCount.toFloat() / analyzedComments.size
                    } else 0f
                    totalToxicityScore += toxicityScore

                    analyzedVideos.add(videoId)

                    val videoData = VideoData(
                        videoId = videoId,
                        title = videoItems.find { it.id.videoId == videoId }?.snippet?.title ?: "Video",
                        channelTitle = channelName,
                        toxicityScore = toxicityScore,
                        totalComments = analyzedComments.size,
                        toxicCount = toxicCount,
                        neutralCount = neutralCount,
                        safeCount = safeCount,
                        timestamp = System.currentTimeMillis()
                    )
                    videoDataList.add(videoData)

                } catch (e: Exception) {
                    Log.e(TAG, "Error analyzing video $videoId", e)
                }
            }

            if (analyzedVideos.isEmpty()) {
                throw Exception("Could not analyze any videos. The channel might have comments disabled.")
            }

            val avgToxicityScore = totalToxicityScore / analyzedVideos.size
            val toxicityTrend = calculateTrend(videoDataList.map { it.toxicityScore })

            // Build proper channel URL
            val channelUrl = if (customUrl.isNotEmpty()) {
                "https://youtube.com/@$customUrl"
            } else {
                "https://youtube.com/channel/$channelId"
            }

            ChannelData(
                channelId = channelId,
                channelName = channelName.ifEmpty { channelInput.replace("@", "") },
                channelUrl = channelUrl,
                thumbnailUrl = thumbnailUrl,
                subscriberCount = subscriberCount,
                videoCount = videoCount,
                viewCount = viewCount,
                totalCommentsAnalyzed = totalComments,
                averageToxicityScore = avgToxicityScore,
                totalToxicCount = totalToxic,
                totalNeutralCount = totalNeutral,
                totalSafeCount = totalSafe,
                analyzedVideos = analyzedVideos,
                toxicityTrend = toxicityTrend,
                mostToxicVideos = videoDataList
                    .sortedByDescending { it.toxicityScore }
                    .take(5)
                    .map { VideoSummary(it.videoId, it.title, it.toxicityScore) }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Channel analysis failed: ${e.message}", e)
            throw e
        }
    }

    private suspend fun resolveChannelId(input: String): String? {
        return withContext(Dispatchers.IO) {
            if (input.startsWith("UC") && input.length > 10) {
                return@withContext input
            }

            var handle = input
            val handlePattern = Regex("youtube\\.com/@([a-zA-Z0-9_-]+)")
            var match = handlePattern.find(input)
            if (match != null) {
                handle = match.groupValues[1]
            } else if (input.startsWith("@")) {
                handle = input.removePrefix("@")
            }

            val searchResponse = try {
                RetrofitClient.youtubeApi.searchChannels(q = handle, maxResults = 1)
            } catch (e: Exception) {
                return@withContext null
            }

            val items = searchResponse.items
            if (items == null || items.isEmpty()) return@withContext null

            items.firstOrNull()?.id?.channelId
        }
    }

    private fun calculateTrend(recentScores: List<Float>): String {
        if (recentScores.size < 3) return "stable"
        val firstHalf = recentScores.take(recentScores.size / 2).average()
        val secondHalf = recentScores.takeLast(recentScores.size / 2).average()
        return when {
            secondHalf > firstHalf + 0.05 -> "increasing"
            secondHalf < firstHalf - 0.05 -> "decreasing"
            else -> "stable"
        }
    }

    suspend fun extractChannelIdFromUrl(url: String): String? {
        return resolveChannelId(url)
    }

    // ==================== RECOMMENDATION METHODS ====================

    suspend fun getToxicityBasedRecommendations(currentVideo: VideoData): List<VideoData> {
        return withContext(Dispatchers.IO) {
            try {
                val allVideos = mutableListOf<VideoData>()
                firebaseManager.getAllVideos().collect { videos ->
                    allVideos.addAll(videos)
                }
                if (allVideos.size < 2) return@withContext emptyList()

                val candidates = allVideos.filter { it.videoId != currentVideo.videoId }
                if (candidates.isEmpty()) return@withContext emptyList()

                val scored = candidates.map { video ->
                    val score = calculateEnhancedRecommendationScore(currentVideo, video)
                    Pair(video, score)
                }

                getDiverseRecommendations(scored, currentVideo)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun calculateEnhancedRecommendationScore(current: VideoData, candidate: VideoData): Float {
        var score = 0f
        score += (1f - abs(current.toxicityScore - candidate.toxicityScore)) * 0.35f
        score += when {
            current.toxicityScore > 0.6 && candidate.toxicityScore < 0.2 -> 0.2f
            current.toxicityScore < 0.2 && candidate.toxicityScore > 0.4 -> 0.15f
            else -> 0f
        }
        score += (candidate.totalComments / 5000f).coerceIn(0f, 0.15f)
        score += (1f - candidate.toxicityScore) * 0.2f
        score += Random.nextFloat() * 0.1f
        return score.coerceIn(0f, 1f)
    }

    private fun getDiverseRecommendations(scored: List<Pair<VideoData, Float>>, currentVideo: VideoData): List<VideoData> {
        val threshold = when {
            currentVideo.toxicityScore > 0.6 -> 0.3f
            currentVideo.toxicityScore > 0.3 -> 0.4f
            else -> 0.5f
        }
        val filtered = scored.filter { it.second > threshold }

        val lowToxicity = filtered.filter { it.first.toxicityScore < 0.2 }
        val mediumToxicity = filtered.filter { it.first.toxicityScore in 0.2..0.5 }
        val highToxicity = filtered.filter { it.first.toxicityScore > 0.5 }

        val recommendations = mutableListOf<VideoData>()
        when {
            currentVideo.toxicityScore > 0.6 -> {
                recommendations.addAll(lowToxicity.take(3).map { it.first })
                recommendations.addAll(mediumToxicity.take(1).map { it.first })
                recommendations.addAll(highToxicity.take(1).map { it.first })
            }
            currentVideo.toxicityScore > 0.3 -> {
                recommendations.addAll(lowToxicity.take(2).map { it.first })
                recommendations.addAll(mediumToxicity.take(2).map { it.first })
                recommendations.addAll(highToxicity.take(1).map { it.first })
            }
            else -> {
                recommendations.addAll(lowToxicity.take(4).map { it.first })
                recommendations.addAll(mediumToxicity.take(1).map { it.first })
            }
        }

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
                recommendationReason = when {
                    currentVideo.toxicityScore > 0.6 && video.toxicityScore < 0.2 -> "✅ Safer alternative"
                    video.toxicityScore < currentVideo.toxicityScore -> "📉 Less toxic content"
                    else -> "🎯 Recommended for you"
                }
            )
        }
    }
}
