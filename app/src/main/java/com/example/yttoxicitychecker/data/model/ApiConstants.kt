package com.example.yttoxicitychecker.data.remote

import com.example.yttoxicitychecker.BuildConfig

object ApiConstants {
    const val YOUTUBE_BASE_URL = "https://www.googleapis.com/"
    const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/"
    val YOUTUBE_API_KEY: String = BuildConfig.YOUTUBE_API_KEY
    val GEMINI_API_KEY: String = BuildConfig.GEMINI_API_KEY
}