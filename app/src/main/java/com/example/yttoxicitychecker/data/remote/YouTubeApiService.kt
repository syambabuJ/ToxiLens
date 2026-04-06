package com.toxilens.yttoxicitychecker.data.remote

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

    @GET("youtube/v3/videos")
    suspend fun getVideoDetails(
        @Query("part") part: String = "snippet",
        @Query("id") id: String,
        @Query("key") key: String = ApiConstants.YOUTUBE_API_KEY
    ): YouTubeVideoResponse

    @GET("youtube/v3/search")
    suspend fun getRelatedVideos(
        @Query("part") part: String = "snippet",
        @Query("relatedToVideoId") relatedToVideoId: String,
        @Query("maxResults") maxResults: Int = 10,
        @Query("type") type: String = "video",
        @Query("key") key: String = ApiConstants.YOUTUBE_API_KEY
    ): YouTubeSearchResponse

    @GET("youtube/v3/search")
    suspend fun searchVideos(
        @Query("part") part: String = "snippet",
        @Query("q") q: String,
        @Query("maxResults") maxResults: Int = 10,
        @Query("type") type: String = "video",
        @Query("videoCategoryId") videoCategoryId: String? = null,
        @Query("safeSearch") safeSearch: String = "strict",
        @Query("key") key: String = ApiConstants.YOUTUBE_API_KEY
    ): YouTubeSearchResponse

    @GET("youtube/v3/search")
    suspend fun searchChannels(
        @Query("part") part: String = "snippet",
        @Query("q") q: String,
        @Query("maxResults") maxResults: Int = 1,
        @Query("type") type: String = "channel",
        @Query("key") key: String = ApiConstants.YOUTUBE_API_KEY
    ): YouTubeSearchResponse

    @GET("youtube/v3/videos")
    suspend fun getTrendingVideos(
        @Query("part") part: String = "snippet,contentDetails,statistics",
        @Query("chart") chart: String = "mostPopular",
        @Query("maxResults") maxResults: Int = 10,
        @Query("regionCode") regionCode: String = "US",
        @Query("key") key: String = ApiConstants.YOUTUBE_API_KEY
    ): YouTubeVideoResponse

    @GET("youtube/v3/channels")
    suspend fun getChannelDetails(
        @Query("part") part: String = "snippet,statistics",
        @Query("id") id: String,
        @Query("key") key: String = ApiConstants.YOUTUBE_API_KEY
    ): YouTubeChannelResponse

    @GET("youtube/v3/search")
    suspend fun getChannelVideos(
        @Query("part") part: String = "snippet",
        @Query("channelId") channelId: String,
        @Query("maxResults") maxResults: Int = 50,
        @Query("order") order: String = "date",
        @Query("type") type: String = "video",
        @Query("key") key: String = ApiConstants.YOUTUBE_API_KEY
    ): YouTubeSearchResponse
}

// ==================== DATA CLASSES ====================

data class YouTubeCommentsResponse(
    val items: List<YouTubeCommentItem> = emptyList()
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

data class YouTubeVideoResponse(
    val items: List<YouTubeVideoItem> = emptyList()
)

data class YouTubeVideoItem(
    val id: String,
    val snippet: YouTubeVideoSnippet,
    val statistics: VideoStatistics? = null
)

data class YouTubeVideoSnippet(
    val title: String,
    val channelTitle: String,
    val publishedAt: String,
    val description: String,
    val thumbnails: Thumbnails?
)

data class VideoStatistics(
    val viewCount: String? = "0",
    val likeCount: String? = "0",
    val commentCount: String? = "0"
)

data class Thumbnails(
    val default: Thumbnail?,
    val medium: Thumbnail?,
    val high: Thumbnail?,
    val standard: Thumbnail?,
    val maxres: Thumbnail?
)

data class Thumbnail(
    val url: String,
    val width: Int,
    val height: Int
)

data class YouTubeSearchResponse(
    val items: List<YouTubeSearchItem>? = emptyList()
)

data class YouTubeSearchItem(
    val id: YouTubeSearchId,
    val snippet: YouTubeSearchSnippet
)

data class YouTubeSearchId(
    val videoId: String? = null,
    val channelId: String? = null,
    val playlistId: String? = null
)

data class YouTubeSearchSnippet(
    val title: String,
    val channelTitle: String,
    val description: String,
    val publishedAt: String,
    val thumbnails: Thumbnails?
)

data class YouTubeChannelResponse(
    val items: List<YouTubeChannelItem>? = emptyList()
)

data class YouTubeChannelItem(
    val id: String,
    val snippet: YouTubeChannelSnippet,
    val statistics: YouTubeChannelStatistics
)

data class YouTubeChannelSnippet(
    val title: String,
    val description: String,
    val customUrl: String?,
    val thumbnails: Thumbnails?
)

data class YouTubeChannelStatistics(
    val viewCount: String? = "0",
    val subscriberCount: String? = "0",
    val videoCount: String? = "0"
)
