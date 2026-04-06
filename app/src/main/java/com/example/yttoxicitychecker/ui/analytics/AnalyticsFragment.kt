package com.toxilens.yttoxicitychecker.ui.analytics

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.toxilens.yttoxicitychecker.data.model.VideoData
import com.toxilens.yttoxicitychecker.data.remote.ApiConstants
import com.toxilens.yttoxicitychecker.data.remote.RetrofitClient
import com.toxilens.yttoxicitychecker.databinding.FragmentAnalyticsBinding
import com.toxilens.yttoxicitychecker.ui.adapters.RecommendationAdapter
import com.toxilens.yttoxicitychecker.ui.viewmodel.MainViewModel
import com.toxilens.yttoxicitychecker.ui.webview.WebViewActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class AnalyticsFragment : Fragment() {

    private var _binding: FragmentAnalyticsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var recommendationAdapter: RecommendationAdapter

    private val lastRecommendedIds = ConcurrentHashMap.newKeySet<String>()
    private var lastVideoId: String = ""
    private var recommendationCounter = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecommendationsRecyclerView()
        setupClickListeners()
        observeViewModel()
        restoreExistingData()
        applyEntryAnimations()

        // Load toxicity-aware trending recommendations
        loadToxicityAwareTrendingRecommendations()
    }

    private fun setupRecommendationsRecyclerView() {
        recommendationAdapter = RecommendationAdapter { videoData ->
            val intent = Intent(requireContext(), WebViewActivity::class.java).apply {
                putExtra("video_id", videoData.videoId)
                putExtra("video_url", videoData.videoUrl)
                putExtra("video_title", videoData.title)
            }
            startActivity(intent)
            Toast.makeText(requireContext(), "Opening: ${videoData.title}", Toast.LENGTH_SHORT).show()
        }

        binding.recyclerViewRecommendations.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = recommendationAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupClickListeners() {
        binding.buttonViewVideo.setOnClickListener {
            animateButtonPress(it) {
                viewModel.currentVideoId?.let { videoId ->
                    val videoData = viewModel.currentVideoData.value
                    val intent = Intent(requireContext(), WebViewActivity::class.java).apply {
                        putExtra("video_id", videoId)
                        putExtra("video_url", "https://www.youtube.com/watch?v=$videoId")
                        putExtra("video_title", videoData?.title ?: "YouTube Video")
                    }
                    startActivity(intent)
                } ?: run {
                    Toast.makeText(requireContext(), "No video analyzed yet", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.buttonShare.setOnClickListener {
            animateButtonPress(it) {
                val videoData = viewModel.currentVideoData.value
                if (videoData != null) {
                    val shareText = buildShareText(videoData)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share Results"))
                } else {
                    Toast.makeText(requireContext(), "No data to share", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun restoreExistingData() {
        viewModel.currentVideoData.value?.let { videoData ->
            updateAnalytics(videoData)
        }
        viewModel.comments.value?.let { comments ->
            if (comments.isNotEmpty()) {
                calculateAdvancedMetrics(comments)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.currentVideoData.observe(viewLifecycleOwner) { videoData ->
            videoData?.let { data ->
                updateAnalytics(data)
                animatePulse(binding.canvasChart)

                val currentVideoId = viewModel.currentVideoId ?: return@let
                if (currentVideoId != lastVideoId) {
                    lastVideoId = currentVideoId
                    recommendationCounter++
                    lastRecommendedIds.clear()
                    Log.d("AnalyticsFragment", "New video detected! Loading toxicity-aware trending recommendations")
                    loadToxicityAwareTrendingRecommendations()
                }
            }
        }

        viewModel.comments.observe(viewLifecycleOwner) { comments ->
            if (comments.isNotEmpty()) {
                calculateAdvancedMetrics(comments)
            }
        }
    }

    private fun loadToxicityAwareTrendingRecommendations() {
        if (_binding == null) return

        val currentVideo = viewModel.currentVideoData.value
        val currentToxicity = currentVideo?.toxicityScore ?: 0.5f

        binding.progressRecommendations.visibility = View.VISIBLE
        binding.recommendationsSection.visibility = View.VISIBLE

        // Different loading messages based on toxicity
        val loadingMessage = when {
            currentToxicity > 0.7 -> "🔍 Finding safe trending videos for you..."
            currentToxicity > 0.4 -> "🔍 Finding popular trending videos..."
            else -> "🔍 Finding relaxing trending videos..."
        }
        binding.textRecommendationTitle.text = loadingMessage

        lifecycleScope.launch {
            try {
                val recommendations = withContext(Dispatchers.IO) {
                    fetchToxicityFilteredTrendingVideos(currentToxicity)
                }

                if (_binding == null) return@launch

                binding.progressRecommendations.visibility = View.GONE

                if (recommendations.isNotEmpty()) {
                    recommendationAdapter.submitList(recommendations)
                    binding.recommendationsSection.visibility = View.VISIBLE

                    // Dynamic title based on toxicity
                    val title = when {
                        currentToxicity > 0.7 -> "✅ Safe Trending Videos"
                        currentToxicity > 0.4 -> "📊 Popular Trending Videos"
                        else -> "🎯 Relaxing Trending Videos"
                    }
                    binding.textRecommendationTitle.text = title
                    Log.d("AnalyticsFragment", "Displaying ${recommendations.size} toxicity-filtered recommendations")
                } else {
                    binding.recommendationsSection.visibility = View.GONE
                    Log.d("AnalyticsFragment", "No filtered trending videos available")
                }
            } catch (e: Exception) {
                if (_binding != null) {
                    binding.progressRecommendations.visibility = View.GONE
                    binding.recommendationsSection.visibility = View.GONE
                }
                Log.e("AnalyticsFragment", "Error: ${e.message}", e)
            }
        }
    }

    private suspend fun fetchToxicityFilteredTrendingVideos(currentToxicity: Float): List<VideoData> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("AnalyticsFragment", "Fetching trending videos with toxicity filter: $currentToxicity")

                val response = RetrofitClient.youtubeApi.getTrendingVideos(
                    part = "snippet",
                    chart = "mostPopular",
                    maxResults = 20,  // Get more to filter
                    regionCode = "US",
                    key = ApiConstants.YOUTUBE_API_KEY
                )

                Log.d("AnalyticsFragment", "Trending response items: ${response.items.size}")

                // Filter and score trending videos based on toxicity
                val scoredVideos = response.items
                    .mapNotNull { item ->
                        try {
                            val vidId = item.id
                            if (lastRecommendedIds.contains(vidId)) return@mapNotNull null

                            // Calculate score based on toxicity level and video content
                            val score = calculateVideoScore(item, currentToxicity)

                            Pair(item, score)
                        } catch (e: Exception) { null }
                    }
                    .sortedByDescending { it.second }  // Sort by highest score
                    .take(10)  // Take top 10
                    .map { it.first }
                    .take(5)  // Take top 5 after scoring

                Log.d("AnalyticsFragment", "Filtered to ${scoredVideos.size} videos")

                scoredVideos.mapNotNull { item ->
                    try {
                        val vidId = item.id
                        lastRecommendedIds.add(vidId)

                        // Determine reason based on toxicity
                        val reason = when {
                            currentToxicity > 0.7 -> "✅ Safe trending video"
                            currentToxicity > 0.4 -> "🔥 Popular trending"
                            else -> "🎯 Relaxing trending"
                        }

                        VideoData(
                            videoId = vidId,
                            videoUrl = "https://youtube.com/watch?v=$vidId",
                            title = item.snippet.title,
                            channelTitle = item.snippet.channelTitle,
                            toxicityScore = 0.2f,
                            isRecommended = true,
                            recommendationReason = reason
                        )
                    } catch (e: Exception) { null }
                }
            } catch (e: Exception) {
                Log.e("AnalyticsFragment", "Error fetching trending videos: ${e.message}", e)
                emptyList()
            }
        }
    }

    private fun calculateVideoScore(item: com.toxilens.yttoxicitychecker.data.remote.YouTubeVideoItem, currentToxicity: Float): Float {
        val title = item.snippet.title.lowercase()
        val description = item.snippet.description.lowercase()

        var score = 0f

        when {
            // HIGH TOXICITY CURRENT VIDEO → Prefer safe, educational, family-friendly
            currentToxicity > 0.7 -> {
                if (title.contains("family") || title.contains("kid") || title.contains("educational")) score += 1.0f
                if (title.contains("official") || title.contains("music")) score += 0.8f
                if (title.contains("positive") || title.contains("inspiring")) score += 0.9f
                if (title.contains("news") || title.contains("debate") || title.contains("controversial")) score -= 0.5f
                if (title.contains("funny") || title.contains("comedy")) score += 0.6f
                score += 0.3f // base score
            }
            // MEDIUM TOXICITY CURRENT VIDEO → Prefer popular, engaging content
            currentToxicity > 0.4 -> {
                if (title.contains("music") || title.contains("official")) score += 1.0f
                if (title.contains("funny") || title.contains("comedy")) score += 0.9f
                if (title.contains("travel") || title.contains("vlog")) score += 0.8f
                if (title.contains("review") || title.contains("tech")) score += 0.7f
                score += 0.4f
            }
            // LOW TOXICITY CURRENT VIDEO → Prefer relaxing, peaceful content
            else -> {
                if (title.contains("relaxing") || title.contains("peaceful")) score += 1.0f
                if (title.contains("music") && !title.contains("hard")) score += 0.9f
                if (title.contains("nature") || title.contains("documentary")) score += 0.8f
                if (title.contains("meditation") || title.contains("calm")) score += 1.0f
                score += 0.5f
            }
        }

        return score.coerceIn(0f, 1.5f)
    }

    private fun updateAnalytics(videoData: VideoData) {
        if (_binding == null) return

        val displayTitle = videoData.title.ifEmpty { "YouTube Video" }
        binding.textVideoTitle.text = displayTitle
        binding.textVideoTitle.visibility = View.VISIBLE

        try {
            binding.textChannelName.text = videoData.channelTitle.ifEmpty { "Unknown Channel" }
            binding.textChannelName.visibility = View.VISIBLE
        } catch (e: Exception) { }

        val toxicityLevel = when {
            videoData.toxicityScore > 0.7 -> "⚠️ High Toxicity"
            videoData.toxicityScore > 0.4 -> "📊 Medium Toxicity"
            else -> "✅ Low Toxicity"
        }

        try {
            binding.textToxicityLevel.text = toxicityLevel
            binding.textToxicityLevel.visibility = View.VISIBLE
        } catch (e: Exception) { }

        binding.textTotalComments.text = "📝 Total Comments: ${videoData.totalComments}"
        binding.textToxicCount.text = "🔴 Toxic: ${videoData.toxicCount}"
        binding.textNeutralCount.text = "🟡 Neutral: ${videoData.neutralCount}"
        binding.textSafeCount.text = "🟢 Safe: ${videoData.safeCount}"

        val toxicityPercentage = (videoData.toxicityScore * 100).toInt()
        binding.textToxicityScore.text = "Toxicity Score: $toxicityPercentage%"

        binding.canvasChart.updateData(videoData.toxicCount, videoData.neutralCount, videoData.safeCount)
        binding.progressToxicity.progress = toxicityPercentage
    }

    private fun calculateAdvancedMetrics(comments: List<com.toxilens.yttoxicitychecker.data.model.Comment>) {
        if (_binding == null) return

        val toxicComments = comments.filter { it.toxicityResult?.isToxic == true }

        if (toxicComments.isNotEmpty()) {
            val avgToxicity = toxicComments.map { it.toxicityResult?.toxicityScore ?: 0f }.average()
            binding.textAvgToxicity.text = "Avg Toxicity: ${(avgToxicity * 100).toInt()}%"
        } else {
            binding.textAvgToxicity.text = "Avg Toxicity: 0%"
        }

        val sentimentCounts = comments.groupBy { it.toxicityResult?.sentiment ?: "Neutral" }
        val sentimentText = "Sentiment: ${sentimentCounts["Positive"]?.size ?: 0} 😊 | " +
                "${sentimentCounts["Neutral"]?.size ?: 0} 😐 | " +
                "${sentimentCounts["Negative"]?.size ?: 0} 😞"
        binding.textSentimentDist.text = sentimentText
    }

    private fun buildShareText(videoData: VideoData): String {
        val videoTitle = videoData.title.ifEmpty { "YouTube Video" }
        val toxicityPercentage = (videoData.toxicityScore * 100).toInt()

        return """
            YouTube Video Analysis Results:
            
            Video: $videoTitle
            Channel: ${videoData.channelTitle}
            Total Comments: ${videoData.totalComments}
            🔴 Toxic: ${videoData.toxicCount}
            🟡 Neutral: ${videoData.neutralCount}
            🟢 Safe: ${videoData.safeCount}
            💣 Toxicity Score: $toxicityPercentage%
            
            Analyzed by YouTube Comment Toxicity Checker
        """.trimIndent()
    }

    // ==================== ANIMATION METHODS ====================
    // (Keep all existing animation methods)
    private fun applyEntryAnimations() {
        if (_binding == null) return

        val viewsToAnimate = listOf(
            binding.textVideoTitle.parent?.parent as? View,
            binding.canvasChart,
            binding.textTotalComments.parent?.parent as? View,
            binding.textAvgToxicity.parent?.parent as? View,
            binding.recommendationsSection
        ).filterNotNull()

        viewsToAnimate.forEachIndexed { index, view ->
            animateSlideUp(view, 400, (index * 100).toLong())
        }
        animateScaleIn(binding.buttonViewVideo, 400)
        animateScaleIn(binding.buttonShare, 450)
    }

    private fun animateSlideUp(view: View, duration: Long = 400, delay: Long = 0) {
        view.translationY = 100f
        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(duration)
            .setStartDelay(delay)
            .setInterpolator(OvershootInterpolator(0.5f))
            .start()
    }

    private fun animateSlideLeft(view: View, duration: Long = 400, delay: Long = 0) {
        view.translationX = 100f
        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(duration)
            .setStartDelay(delay)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun animateScaleIn(view: View, duration: Long = 300, delay: Long = 0) {
        view.scaleX = 0f
        view.scaleY = 0f
        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(duration)
            .setStartDelay(delay)
            .setInterpolator(OvershootInterpolator(0.7f))
            .start()
    }

    private fun animateFadeIn(view: View, duration: Long = 300, delay: Long = 0) {
        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .setDuration(duration)
            .setStartDelay(delay)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun animateFadeOut(view: View, duration: Long = 200, onEnd: (() -> Unit)? = null) {
        view.animate()
            .alpha(0f)
            .setDuration(duration)
            .withEndAction {
                view.visibility = View.GONE
                onEnd?.invoke()
            }
            .start()
    }

    private fun animateTextChange(textView: TextView, newText: String) {
        textView.text = newText
    }

    private fun animateProgressBar(progressBar: ProgressBar, targetProgress: Int) {
        ValueAnimator.ofInt(progressBar.progress, targetProgress).apply {
            duration = 800
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                progressBar.progress = it.animatedValue as Int
            }
            start()
        }
    }

    private fun animateButtonPress(button: View, onClick: () -> Unit) {
        button.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                onClick()
                button.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private fun animatePulse(view: View) {
        view.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(200)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .start()
            }
            .start()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
