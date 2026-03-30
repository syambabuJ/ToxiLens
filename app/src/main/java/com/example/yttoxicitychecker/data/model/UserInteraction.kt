package com.example.yttoxicitychecker.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class UserInteraction(
    val userId: String = "",
    val videoId: String = "",
    val videoTitle: String = "",
    val toxicityScore: Float = 0f,
    val interactionType: String = "viewed", // "viewed", "analyzed", "shared"
    val timestamp: Long = System.currentTimeMillis(),
    val rating: Int = 0 // 1-5 stars
) : Parcelable