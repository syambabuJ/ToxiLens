package com.example.yttoxicitychecker.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ToxicityResult(
    val isToxic: Boolean = false,
    val toxicityScore: Float = 0f,
    val sentiment: String = "Neutral",
    val category: String = "Safe",
    val reasoning: String = "",
    // NEW: Multi-label toxicity fields
    val toxicityTypes: List<String> = emptyList(),
    val primaryType: String = ""
) : Parcelable {

    fun getColor(): Int {
        return when {
            isToxic && category == "Hate Speech" -> 0xFFDC2626.toInt()
            isToxic -> 0xFFEF4444.toInt()
            toxicityScore > 0.5f -> 0xFFF59E0B.toInt()
            else -> 0xFF10B981.toInt()
        }
    }

    fun getIcon(): String {
        return when {
            isToxic && category == "Hate Speech" -> "🔴🔴"
            isToxic -> "🔴"
            toxicityScore > 0.5f -> "🟡"
            else -> "🟢"
        }
    }

    fun getBadgeText(): String {
        return "${getIcon()} $category"
    }

    fun getScorePercentage(): Int {
        return (toxicityScore * 100).toInt()
    }

    // NEW: Get icons for each toxicity type
    fun getToxicityTypeIcons(): String {
        if (toxicityTypes.isEmpty()) return ""
        return toxicityTypes.joinToString(" ") { type ->
            when (type) {
                "hate_speech" -> "💀"
                "obscene" -> "🔞"
                "threat" -> "⚠️"
                "insult" -> "👎"
                "identity_attack" -> "🎯"
                "harassment" -> "🔥"
                "toxic" -> "💢"
                "severe_toxic" -> "☠️"
                else -> "📌"
            }
        }
    }

    // NEW: Get readable names for toxicity types
    fun getToxicityTypeNames(): String {
        if (toxicityTypes.isEmpty()) return ""
        return toxicityTypes.joinToString(", ") { type ->
            when (type) {
                "hate_speech" -> "Hate Speech"
                "obscene" -> "Obscene"
                "threat" -> "Threat"
                "insult" -> "Insult"
                "identity_attack" -> "Identity Attack"
                "harassment" -> "Harassment"
                "toxic" -> "Toxic"
                "severe_toxic" -> "Severe Toxic"
                else -> type.replace("_", " ").capitalize()
            }
        }
    }

    // NEW: Get the most severe toxicity type
    fun getPrimaryTypeName(): String {
        return when (primaryType) {
            "hate_speech" -> "Hate Speech"
            "severe_toxic" -> "Severe Toxicity"
            "threat" -> "Threat"
            "harassment" -> "Harassment"
            "identity_attack" -> "Identity Attack"
            "obscene" -> "Obscene"
            "insult" -> "Insult"
            "toxic" -> "Toxic"
            else -> primaryType.replace("_", " ").capitalize()
        }
    }
}