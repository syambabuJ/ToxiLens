package com.toxilens.yttoxicitychecker.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ChannelData(
    val channelId: String = "",
    val channelName: String = "",
    val channelUrl: String = "",
    val thumbnailUrl: String = "",
    val subscriberCount: Long = 0,
    val videoCount: Long = 0,
    val viewCount: Long = 0,
    val totalCommentsAnalyzed: Int = 0,
    val averageToxicityScore: Float = 0f,
    val totalToxicCount: Int = 0,
    val totalNeutralCount: Int = 0,
    val totalSafeCount: Int = 0,
    val lastAnalyzed: Long = System.currentTimeMillis(),
    val analyzedVideos: List<String> = emptyList(),
    val toxicityTrend: String = "stable",
    val mostToxicVideos: List<VideoSummary> = emptyList()
) : Parcelable {

    fun getToxicityLevel(): String {
        return when {
            averageToxicityScore > 0.7 -> "⚠️ High Toxicity Channel"
            averageToxicityScore > 0.4 -> "📊 Medium Toxicity Channel"
            else -> "✅ Low Toxicity Channel"
        }
    }

    fun getToxicityColor(): Int {
        return when {
            averageToxicityScore > 0.7 -> 0xFFEF4444.toInt()
            averageToxicityScore > 0.4 -> 0xFFF59E0B.toInt()
            else -> 0xFF10B981.toInt()
        }
    }

    fun getSafetyScore(): Int {
        return ((1 - averageToxicityScore) * 100).toInt()
    }

    fun getSafetyLevel(): String {
        val score = getSafetyScore()
        return when {
            score >= 80 -> "VERY SAFE"
            score >= 60 -> "SAFE"
            score >= 40 -> "MODERATE"
            score >= 20 -> "CAUTION"
            else -> "UNSAFE"
        }
    }

    fun getSafetyColor(): Int {
        val score = getSafetyScore()
        return when {
            score >= 80 -> 0xFF10B981.toInt()
            score >= 60 -> 0xFF34D399.toInt()
            score >= 40 -> 0xFFFBBF24.toInt()
            score >= 20 -> 0xFFF97316.toInt()
            else -> 0xFFEF4444.toInt()
        }
    }

    fun getSafetyBgColor(): String {
        val score = getSafetyScore()
        return when {
            score >= 80 -> "#E8F5E9"
            score >= 60 -> "#F0FDF4"
            score >= 40 -> "#FEFCE8"
            score >= 20 -> "#FFF7ED"
            else -> "#FEF2F2"
        }
    }

    fun getCommunityHealth(): String {
        return when {
            averageToxicityScore < 0.2 -> "🌟 Excellent community! Very few toxic comments."
            averageToxicityScore < 0.4 -> "👍 Generally positive community with occasional issues."
            averageToxicityScore < 0.6 -> "⚠️ Mixed community. Some toxic content present."
            averageToxicityScore < 0.8 -> "🔴 Toxic community. Many negative comments."
            else -> "💀 Extremely toxic. Strongly advise avoiding comment sections."
        }
    }

    fun getRecommendation(): String {
        return when {
            averageToxicityScore > 0.6 -> "🚫 Recommended: Watch videos only, avoid reading comments"
            averageToxicityScore > 0.4 -> "⚠️ Recommended: Be cautious in comment sections"
            else -> "✅ Recommended: Generally safe to engage"
        }
    }

    fun getSubscriberDisplay(): String {
        return when {
            subscriberCount >= 1_000_000 -> String.format("%.1fM", subscriberCount / 1_000_000.0)
            subscriberCount >= 1_000 -> String.format("%.1fK", subscriberCount / 1_000.0)
            else -> subscriberCount.toString()
        }
    }

    fun getViewDisplay(): String {
        return when {
            viewCount >= 1_000_000_000 -> String.format("%.1fB", viewCount / 1_000_000_000.0)
            viewCount >= 1_000_000 -> String.format("%.1fM", viewCount / 1_000_000.0)
            viewCount >= 1_000 -> String.format("%.1fK", viewCount / 1_000.0)
            else -> viewCount.toString()
        }
    }
}

@Parcelize
data class VideoSummary(
    val videoId: String,
    val title: String,
    val toxicityScore: Float,
    val thumbnailUrl: String = ""
) : Parcelable {
    fun getToxicityPercent(): Int = (toxicityScore * 100).toInt()
}
