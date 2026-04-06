package com.toxilens.yttoxicitychecker.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class VideoData(
    val videoId: String = "",
    val videoUrl: String = "",
    val title: String = "",
    val channelTitle: String = "",
    val thumbnailUrl: String = "",
    val toxicityScore: Float = 0f,
    val totalComments: Int = 0,
    val toxicCount: Int = 0,
    val neutralCount: Int = 0,
    val safeCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val comments: List<Comment> = emptyList(),
    // NEW: Recommendation fields
    val category: String = "", // "educational", "entertainment", "news", "gaming", "music"
    val isRecommended: Boolean = false,
    val recommendationReason: String = ""
) : Parcelable {

    fun getDisplayTitle(): String = if (title.isNotEmpty()) title else "YouTube Video"
    fun getDisplayChannel(): String = if (channelTitle.isNotEmpty()) channelTitle else "Unknown Channel"

    fun getToxicityLevel(): String {
        return when {
            toxicityScore > 0.7 -> "High Toxicity"
            toxicityScore > 0.4 -> "Medium Toxicity"
            toxicityScore > 0.1 -> "Low Toxicity"
            else -> "Safe Content"
        }
    }

    fun getToxicityColor(): Int {
        return when {
            toxicityScore > 0.7 -> 0xFFEF4444.toInt()
            toxicityScore > 0.4 -> 0xFFF59E0B.toInt()
            toxicityScore > 0.1 -> 0xFFFBBF24.toInt()
            else -> 0xFF10B981.toInt()
        }
    }

    fun getRecommendationBadge(): String {
        return when {
            toxicityScore > 0.7 -> "⚠️ High Toxicity - Safe alternatives recommended"
            toxicityScore > 0.4 -> "📊 Medium Toxicity - Consider safer content"
            else -> "✅ Safe content - Great choice!"
        }
    }
}
