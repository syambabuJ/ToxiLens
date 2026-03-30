package com.example.yttoxicitychecker.ui.analytics

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.yttoxicitychecker.data.model.VideoData
import com.example.yttoxicitychecker.databinding.FragmentAnalyticsBinding
import com.example.yttoxicitychecker.ui.adapters.RecommendationAdapter
import com.example.yttoxicitychecker.ui.viewmodel.MainViewModel
import com.example.yttoxicitychecker.ui.webview.WebViewActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnalyticsFragment : Fragment() {

    private var _binding: FragmentAnalyticsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var recommendationAdapter: RecommendationAdapter

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

        // Apply entry animations to all views
        applyEntryAnimations()

        // SHOW RECOMMENDATIONS IMMEDIATELY (No waiting)
        showRecommendationsImmediately()
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
        // Add scale animation to buttons on click
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
                // Pulse animation when new data arrives on the chart
                animatePulse(binding.canvasChart)
            }
        }

        viewModel.comments.observe(viewLifecycleOwner) { comments ->
            if (comments.isNotEmpty()) {
                calculateAdvancedMetrics(comments)
                // Find the advanced metrics card by navigating up from textAvgToxicity
                val advancedMetricsCard = binding.textAvgToxicity.parent?.parent as? View
                advancedMetricsCard?.let {
                    animateSlideUp(it, 300)
                }
            }
        }
    }

    // Show recommendations IMMEDIATELY without waiting
    private fun showRecommendationsImmediately() {
        // Get current video toxicity to personalize recommendations
        val currentVideo = viewModel.currentVideoData.value
        val currentToxicity = currentVideo?.toxicityScore ?: 0.5f

        // Create instant recommendations based on current video's toxicity level
        val recommendations = createInstantRecommendations(currentToxicity)

        recommendationAdapter.submitList(recommendations)

        // Animate recommendations section sliding up
        animateSlideUp(binding.recommendationsSection, 500)

        // Animate each item in recycler view with staggered effect
        binding.recyclerViewRecommendations.post {
            for (i in 0 until binding.recyclerViewRecommendations.childCount) {
                val child = binding.recyclerViewRecommendations.getChildAt(i)
                animateSlideLeft(child, 300, (i * 80).toLong())
            }
        }

        // Set title based on toxicity
        val title = when {
            currentToxicity > 0.7 -> "⚠️ Safer Alternatives Recommended"
            currentToxicity > 0.4 -> "📊 Lower Toxicity Videos"
            else -> "🎯 You Might Also Like"
        }

        // Animate title change
        animateTextChange(binding.textRecommendationTitle, title)

        // Load real recommendations in background (will replace these later)
        loadRealRecommendationsInBackground(currentVideo)
    }

    private fun createInstantRecommendations(currentToxicity: Float): List<VideoData> {
        // Predefined safe recommendations that show instantly
        val safeVideos = listOf(
            VideoData(
                videoId = "dQw4w9WgXcQ",
                videoUrl = "https://youtube.com/watch?v=dQw4w9WgXcQ",
                title = "Never Gonna Give You Up",
                channelTitle = "Rick Astley",
                toxicityScore = 0.05f,
                isRecommended = true,
                recommendationReason = if (currentToxicity > 0.4) "✅ 95% less toxic than current" else "✅ Very safe content"
            ),
            VideoData(
                videoId = "kJQP7kiw5Fk",
                videoUrl = "https://youtube.com/watch?v=kJQP7kiw5Fk",
                title = "Despacito",
                channelTitle = "Luis Fonsi",
                toxicityScore = 0.08f,
                isRecommended = true,
                recommendationReason = if (currentToxicity > 0.4) "🎵 Much lower toxicity" else "🎵 Popular music"
            ),
            VideoData(
                videoId = "YQHsXMglC9A",
                videoUrl = "https://youtube.com/watch?v=YQHsXMglC9A",
                title = "Baby Shark Dance",
                channelTitle = "Pinkfong",
                toxicityScore = 0.12f,
                isRecommended = true,
                recommendationReason = if (currentToxicity > 0.4) "👨‍👩‍👧‍👦 Family safe alternative" else "👨‍👩‍👧‍👦 Family friendly"
            ),
            VideoData(
                videoId = "3JZ_D3ELwOQ",
                videoUrl = "https://youtube.com/watch?v=3JZ_D3ELwOQ",
                title = "See You Again",
                channelTitle = "Wiz Khalifa",
                toxicityScore = 0.06f,
                isRecommended = true,
                recommendationReason = "🎶 Positive and uplifting"
            ),
            VideoData(
                videoId = "OPf0YbXqDm0",
                videoUrl = "https://youtube.com/watch?v=OPf0YbXqDm0",
                title = "Happy - Pharrell Williams",
                channelTitle = "Pharrell Williams",
                toxicityScore = 0.04f,
                isRecommended = true,
                recommendationReason = "😊 Extremely positive content"
            )
        )
        return safeVideos
    }

    private fun loadRealRecommendationsInBackground(currentVideo: VideoData?) {
        if (currentVideo == null) return

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Show progress bar while loading
                binding.progressRecommendations.visibility = View.VISIBLE

                val realRecommendations = withContext(Dispatchers.IO) {
                    viewModel.repository.getToxicityBasedRecommendations(currentVideo)
                }

                if (realRecommendations.isNotEmpty()) {
                    // Hide progress bar
                    binding.progressRecommendations.visibility = View.GONE

                    // Fade out old recommendations
                    animateFadeOut(binding.recyclerViewRecommendations, 200) {
                        // Replace test recommendations with real ones
                        recommendationAdapter.submitList(realRecommendations)

                        val newTitle = when {
                            currentVideo.toxicityScore > 0.7 -> "⚠️ Safer Alternatives"
                            currentVideo.toxicityScore > 0.4 -> "📊 Recommended for You"
                            else -> "🎯 You Might Also Like"
                        }

                        // Animate title change
                        animateTextChange(binding.textRecommendationTitle, newTitle)

                        // Fade in new recommendations
                        animateFadeIn(binding.recyclerViewRecommendations, 300)

                        // Animate each new item with stagger
                        binding.recyclerViewRecommendations.postDelayed({
                            for (i in 0 until binding.recyclerViewRecommendations.childCount) {
                                val child = binding.recyclerViewRecommendations.getChildAt(i)
                                animateScaleIn(child, 300, (i * 50).toLong())
                            }
                        }, 100)
                    }

                    Log.d("AnalyticsFragment", "Updated with ${realRecommendations.size} real recommendations")
                } else {
                    binding.progressRecommendations.visibility = View.GONE
                }
            } catch (e: Exception) {
                binding.progressRecommendations.visibility = View.GONE
                Log.e("AnalyticsFragment", "Error loading real recommendations", e)
            }
        }
    }

    private fun updateAnalytics(videoData: VideoData) {
        val displayTitle = videoData.title.ifEmpty { "YouTube Video" }

        // Animate text changes
        animateTextChange(binding.textVideoTitle, displayTitle)
        binding.textVideoTitle.visibility = View.VISIBLE

        try {
            animateTextChange(binding.textChannelName, videoData.channelTitle.ifEmpty { "Unknown Channel" })
            binding.textChannelName.visibility = View.VISIBLE
        } catch (e: Exception) { }

        val toxicityLevel = when {
            videoData.toxicityScore > 0.7 -> "⚠️ High Toxicity"
            videoData.toxicityScore > 0.4 -> "📊 Medium Toxicity"
            else -> "✅ Low Toxicity"
        }

        try {
            animateTextChange(binding.textToxicityLevel, toxicityLevel)
            binding.textToxicityLevel.visibility = View.VISIBLE
        } catch (e: Exception) { }

        // Animate text updates for comment counts
        animateTextChange(binding.textTotalComments, "📝 Total Comments: ${videoData.totalComments}")
        animateTextChange(binding.textToxicCount, "🔴 Toxic: ${videoData.toxicCount}")
        animateTextChange(binding.textNeutralCount, "🟡 Neutral: ${videoData.neutralCount}")
        animateTextChange(binding.textSafeCount, "🟢 Safe: ${videoData.safeCount}")

        val toxicityPercentage = (videoData.toxicityScore * 100).toInt()
        animateTextChange(binding.textToxicityScore, "$toxicityPercentage%")

        // Animate the toxicity score text separately
        binding.textToxicityScore.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(200)
            .withEndAction {
                binding.textToxicityScore.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .start()
            }
            .start()

        // Animated chart update
        binding.canvasChart.updateData(videoData.toxicCount, videoData.neutralCount, videoData.safeCount)

        // Animated progress bar
        animateProgressBar(binding.progressToxicity, toxicityPercentage)
    }

    private fun calculateAdvancedMetrics(comments: List<com.example.yttoxicitychecker.data.model.Comment>) {
        val toxicComments = comments.filter { it.toxicityResult?.isToxic == true }

        if (toxicComments.isNotEmpty()) {
            val avgToxicity = toxicComments.map { it.toxicityResult?.toxicityScore ?: 0f }.average()
            animateTextChange(binding.textAvgToxicity, "Avg Toxicity: ${(avgToxicity * 100).toInt()}%")
        } else {
            animateTextChange(binding.textAvgToxicity, "Avg Toxicity: 0%")
        }

        val sentimentCounts = comments.groupBy { it.toxicityResult?.sentiment ?: "Neutral" }
        val sentimentText = "Sentiment: ${sentimentCounts["Positive"]?.size ?: 0} 😊 | " +
                "${sentimentCounts["Neutral"]?.size ?: 0} 😐 | " +
                "${sentimentCounts["Negative"]?.size ?: 0} 😞"

        animateTextChange(binding.textSentimentDist, sentimentText)
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
            
            Analyzed by ToxiLens
        """.trimIndent()
    }

    // ==================== ANIMATION METHODS ====================

    private fun applyEntryAnimations() {
        // Get all the main cards from the layout by finding their parent containers
        val viewsToAnimate = mutableListOf<View>()

        // Find the video title card (parent of textVideoTitle)
        binding.textVideoTitle.parent?.parent?.let { viewsToAnimate.add(it as View) }

        // Add the chart
        viewsToAnimate.add(binding.canvasChart)

        // Find the toxicity distribution card (parent of textTotalComments)
        binding.textTotalComments.parent?.parent?.let { viewsToAnimate.add(it as View) }

        // Find the advanced metrics card (parent of textAvgToxicity)
        binding.textAvgToxicity.parent?.parent?.let { viewsToAnimate.add(it as View) }

        // Add recommendations section
        viewsToAnimate.add(binding.recommendationsSection)

        // Apply staggered animations
        viewsToAnimate.forEachIndexed { index, view ->
            animateSlideUp(view, 400, (index * 100).toLong())
        }

        // Individual view animations with different effects
        animateSlideUp(binding.textVideoTitle, 300)
        animateSlideUp(binding.textChannelName, 350)
        animateScaleIn(binding.buttonViewVideo, 400)
        animateScaleIn(binding.buttonShare, 450)
        animateFadeIn(binding.progressToxicity, 500)
    }

    // Slide up animation
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

    // Slide left animation
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

    // Scale in animation
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

    // Fade in animation
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

    // Fade out animation with callback
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

    // Text change animation with crossfade
    private fun animateTextChange(textView: TextView, newText: String) {
        textView.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                textView.text = newText
                textView.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }

    // Progress bar animation
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

    // Button press animation with scale effect
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

    // Pulse animation for highlighting changes
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
        super.onDestroyView()
        _binding = null
    }
}
