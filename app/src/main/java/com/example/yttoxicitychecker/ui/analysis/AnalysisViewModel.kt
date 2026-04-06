package com.toxilens.yttoxicitychecker.ui.analysis

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toxilens.yttoxicitychecker.data.model.Comment
import com.toxilens.yttoxicitychecker.data.model.VideoData
import com.toxilens.yttoxicitychecker.data.repository.AppRepository
import com.toxilens.yttoxicitychecker.firebase.FirebaseManager
import kotlinx.coroutines.launch

class AnalysisViewModel : ViewModel() {

    private val repository = AppRepository(FirebaseManager())

    private val _comments = MutableLiveData<List<Comment>>()
    val comments: LiveData<List<Comment>> = _comments

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _historyList = MutableLiveData<List<VideoData>>()
    val historyList: LiveData<List<VideoData>> = _historyList

    private val _isLoadingHistory = MutableLiveData(false)
    val isLoadingHistory: LiveData<Boolean> = _isLoadingHistory

    private val _currentVideoData = MutableLiveData<VideoData?>()
    val currentVideoData: LiveData<VideoData?> = _currentVideoData

    var currentVideoId: String? = null

    fun fetchAndAnalyzeComments(videoId: String, videoTitle: String = "") {
        viewModelScope.launch {
            _isLoading.value = true
            currentVideoId = videoId

            try {
                val comments = repository.fetchComments(videoId)
                val analyzedComments = if (comments.isNotEmpty()) {
                    repository.analyzeAllComments(comments)
                } else {
                    comments
                }

                _comments.value = analyzedComments

                // Calculate metrics
                val toxicCount = analyzedComments.count { it.toxicityResult?.isToxic == true }
                val neutralCount = analyzedComments.count {
                    it.toxicityResult?.toxicityScore?.let { score -> score > 0.3f && score <= 0.6f } == true
                }
                val safeCount = analyzedComments.count { it.toxicityResult?.isToxic == false &&
                        (it.toxicityResult?.toxicityScore ?: 0f) <= 0.3f }

                val toxicityScore = if (analyzedComments.isNotEmpty()) {
                    toxicCount.toFloat() / analyzedComments.size
                } else 0f

                // Use provided title or default
                val finalTitle = if (videoTitle.isNotEmpty()) {
                    videoTitle
                } else {
                    "YouTube Video"
                }

                val videoData = VideoData(
                    videoId = videoId,
                    videoUrl = "https://youtube.com/watch?v=$videoId",
                    title = finalTitle,
                    toxicityScore = toxicityScore,
                    totalComments = analyzedComments.size,
                    toxicCount = toxicCount,
                    neutralCount = neutralCount,
                    safeCount = safeCount,
                    timestamp = System.currentTimeMillis(),
                    comments = analyzedComments
                )

                _currentVideoData.value = videoData
                repository.saveVideoAnalysis(videoData)

            } catch (e: Exception) {
                e.printStackTrace()
                _comments.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            _isLoadingHistory.value = true
            try {
                repository.getVideoHistory().collect { history ->
                    _historyList.value = history
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingHistory.value = false
            }
        }
    }

    fun loadVideoAnalysis(videoId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val videoData = repository.getVideoData(videoId)
                if (videoData != null) {
                    _currentVideoData.value = videoData
                    _comments.value = videoData.comments
                    currentVideoId = videoId
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
