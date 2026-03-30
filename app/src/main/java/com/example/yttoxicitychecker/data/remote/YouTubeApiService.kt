package com.example.yttoxicitychecker.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeApiService {
    @GET("youtube/v3/commentThreads")
    suspend fun getComments(
        @Query("part") part: String = "snippet",
        @Query("videoId") videoId: String,
        @Query("maxResults") maxResults: Int = 100,
        @Query("key") key: String = ApiConstants.YOUTUBE_API_KEY
    ): YouTubeCommentsResponse

    // Add this endpoint to fetch video details
    @GET("youtube/v3/videos")
    suspend fun getVideoDetails(
        @Query("part") part: String = "snippet",
        @Query("id") id: String,
        @Query("key") key: String = ApiConstants.YOUTUBE_API_KEY
    ): YouTubeVideoResponse
}

// ==================== COMMENT THREADS DATA CLASSES ====================

data class YouTubeCommentsResponse(
    val items: List<YouTubeCommentItem>
)

data class YouTubeCommentItem(
    val snippet: YouTubeCommentSnippet
)

data class YouTubeCommentSnippet(
    val topLevelComment: YouTubeTopLevelComment
)

data class YouTubeTopLevelComment(
    val snippet: YouTubeCommentDetails
)

data class YouTubeCommentDetails(
    val textDisplay: String,
    val authorDisplayName: String,
    val publishedAt: String,
    val likeCount: Int
)

// ==================== VIDEO DETAILS DATA CLASSES ====================

data class YouTubeVideoResponse(
    val items: List<YouTubeVideoItem>
)

data class YouTubeVideoItem(
    val id: String,
    val snippet: YouTubeVideoSnippet
)

data class YouTubeVideoSnippet(
    val title: String,
    val channelTitle: String,
    val publishedAt: String,
    val description: String,
    val thumbnails: Thumbnails?
)

data class Thumbnails(
    val default: Thumbnail?,
    val medium: Thumbnail?,
    val high: Thumbnail?
)

data class Thumbnail(
    val url: String,
    val width: Int,
    val height: Int
)