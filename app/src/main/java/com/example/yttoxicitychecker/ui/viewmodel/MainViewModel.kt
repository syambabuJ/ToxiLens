package com.example.yttoxicitychecker.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yttoxicitychecker.data.model.Comment
import com.example.yttoxicitychecker.data.model.VideoData
import com.example.yttoxicitychecker.data.repository.AppRepository
import com.example.yttoxicitychecker.firebase.FirebaseManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    val repository = AppRepository(FirebaseManager())

    private val _comments = MutableLiveData<List<Comment>>()
    val comments: LiveData<List<Comment>> = _comments

    private val _currentVideoData = MutableLiveData<VideoData?>()
    val currentVideoData: LiveData<VideoData?> = _currentVideoData

    private val _historyList = MutableLiveData<List<VideoData>>()
    val historyList: LiveData<List<VideoData>> = _historyList

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isLoadingHistory = MutableLiveData(false)
    val isLoadingHistory: LiveData<Boolean> = _isLoadingHistory

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _isEmpty = MutableLiveData(false)
    val isEmpty: LiveData<Boolean> = _isEmpty

    var currentVideoId: String? = null
    private var isFetching = false

    init {
        loadHistoryFromFirebase()
    }

    fun fetchAndAnalyzeComments(videoId: String, videoTitle: String = "") {
        if (isFetching) return
        isFetching = true

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            currentVideoId = videoId

            try {
                val cachedData = repository.getVideoData(videoId)
                if (cachedData != null) {
                    _currentVideoData.value = cachedData
                    _comments.value = cachedData.comments
                    _isEmpty.value = cachedData.comments.isEmpty()
                    _isLoading.value = false
                    isFetching = false
                    return@launch
                }

                val (videoTitleFromApi, channelTitle) = repository.fetchVideoDetails(videoId)

                val comments = repository.fetchComments(videoId)
                if (comments.isEmpty()) {
                    _isEmpty.value = true
                    _comments.value = emptyList()
                    _isLoading.value = false
                    isFetching = false
                    return@launch
                }

                val analyzedComments = analyzeCommentsParallel(comments)
                _comments.value = analyzedComments

                val toxicCount = analyzedComments.count { it.toxicityResult?.isToxic == true }
                val neutralCount = analyzedComments.count {
                    it.toxicityResult?.toxicityScore?.let { score -> score > 0.3f && score <= 0.6f } == true
                }
                val safeCount = analyzedComments.count {
                    it.toxicityResult?.isToxic == false && (it.toxicityResult?.toxicityScore ?: 0f) <= 0.3f
                }
                val toxicityScore = if (analyzedComments.isNotEmpty()) {
                    toxicCount.toFloat() / analyzedComments.size
                } else 0f

                val videoData = VideoData(
                    videoId = videoId,
                    videoUrl = "https://youtube.com/watch?v=$videoId",
                    title = videoTitleFromApi.ifEmpty { "YouTube Video" },
                    channelTitle = channelTitle.ifEmpty { "Unknown Channel" },
                    toxicityScore = toxicityScore,
                    totalComments = analyzedComments.size,
                    toxicCount = toxicCount,
                    neutralCount = neutralCount,
                    safeCount = safeCount,
                    timestamp = System.currentTimeMillis(),
                    comments = analyzedComments
                )

                _currentVideoData.value = videoData
                _isEmpty.value = false
                repository.saveVideoAnalysis(videoData)
                loadHistoryFromFirebase()

            } catch (e: Exception) {
                Log.e("MainViewModel", "Error", e)
                _errorMessage.value = e.message ?: "Analysis failed"
                _isEmpty.value = true
            } finally {
                _isLoading.value = false
                isFetching = false
            }
        }
    }

    private suspend fun analyzeCommentsParallel(comments: List<Comment>): List<Comment> {
        val deferredResults = comments.map { comment ->
            viewModelScope.async {
                repository.analyzeComment(comment)
            }
        }
        return deferredResults.awaitAll()
    }

    fun loadHistoryFromFirebase() {
        viewModelScope.launch {
            _isLoadingHistory.value = true
            try {
                repository.getVideoHistory().collect { history ->
                    _historyList.value = history
                    Log.d("MainViewModel", "History loaded: ${history.size} videos")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading history", e)
                _errorMessage.value = "Failed to load history"
            } finally {
                _isLoadingHistory.value = false
            }
        }
    }

    fun loadVideoFromFirebase(videoId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val videoData = repository.getVideoData(videoId)
                if (videoData != null) {
                    _currentVideoData.value = videoData
                    _comments.value = videoData.comments
                    currentVideoId = videoId
                    _isEmpty.value = videoData.comments.isEmpty()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading video", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshCurrentVideo() {
        currentVideoId?.let { videoId ->
            viewModelScope.launch {
                try {
                    val comments = repository.fetchComments(videoId)
                    if (comments.isNotEmpty()) {
                        val analyzedComments = analyzeCommentsParallel(comments)
                        _comments.value = analyzedComments

                        val toxicCount = analyzedComments.count { it.toxicityResult?.isToxic == true }
                        val neutralCount = analyzedComments.count {
                            it.toxicityResult?.toxicityScore?.let { score -> score > 0.3f && score <= 0.6f } == true
                        }
                        val safeCount = analyzedComments.count {
                            it.toxicityResult?.isToxic == false && (it.toxicityResult?.toxicityScore ?: 0f) <= 0.3f
                        }
                        val toxicityScore = if (analyzedComments.isNotEmpty()) {
                            toxicCount.toFloat() / analyzedComments.size
                        } else 0f

                        val updatedVideo = _currentVideoData.value?.copy(
                            comments = analyzedComments,
                            totalComments = analyzedComments.size,
                            toxicCount = toxicCount,
                            neutralCount = neutralCount,
                            safeCount = safeCount,
                            toxicityScore = toxicityScore
                        )
                        updatedVideo?.let {
                            _currentVideoData.value = it
                            repository.saveVideoAnalysis(it)
                        }
                        _isEmpty.value = analyzedComments.isEmpty()
                    } else {
                        _isEmpty.value = true
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Refresh failed"
                }
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}