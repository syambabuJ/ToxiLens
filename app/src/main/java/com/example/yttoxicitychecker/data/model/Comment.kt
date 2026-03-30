package com.example.yttoxicitychecker.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize



@Parcelize
data class Comment(
    val id: String = "",
    val text: String = "",
    val author: String = "",
    val publishedAt: String = "",
    val likeCount: Int = 0,
    var toxicityResult: ToxicityResult? = null
) : Parcelable