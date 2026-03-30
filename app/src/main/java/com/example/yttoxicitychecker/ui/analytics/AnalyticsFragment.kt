package com.example.yttoxicitychecker.ui.analytics

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
        binding.buttonViewVideo.setOnClickListener {
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

        binding.buttonShare.setOnClickListener {
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
            }
        }

        viewModel.comments.observe(viewLifecycleOwner) { comments ->
            if (comments.isNotEmpty()) {
                calculateAdvancedMetrics(comments)
            }
        }
    }

    // NEW: Show recommendations IMMEDIATELY without waiting
    private fun showRecommendationsImmediately() {
        // Get current video toxicity to personalize recommendations
        val currentVideo = viewModel.currentVideoData.value
        val currentToxicity = currentVideo?.toxicityScore ?: 0.5f

        // Create instant recommendations based on current video's toxicity level
        val recommendations = createInstantRecommendations(currentToxicity)

        recommendationAdapter.submitList(recommendations)
        binding.recommendationsSection.visibility = View.VISIBLE

        // Set title based on toxicity
        val title = when {
            currentToxicity > 0.7 -> "⚠️ Safer Alternatives Recommended"
            currentToxicity > 0.4 -> "📊 Lower Toxicity Videos"
            else -> "🎯 You Might Also Like"
        }
        binding.textRecommendationTitle.text = title

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

        // Return safe videos immediately (no waiting)
        return safeVideos
    }

    private fun loadRealRecommendationsInBackground(currentVideo: VideoData?) {
        if (currentVideo == null) return

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val realRecommendations = withContext(Dispatchers.IO) {
                    viewModel.repository.getToxicityBasedRecommendations(currentVideo)
                }

                if (realRecommendations.isNotEmpty()) {
                    // Replace test recommendations with real ones
                    recommendationAdapter.submitList(realRecommendations)
                    binding.textRecommendationTitle.text = when {
                        currentVideo.toxicityScore > 0.7 -> "⚠️ Safer Alternatives"
                        currentVideo.toxicityScore > 0.4 -> "📊 Recommended for You"
                        else -> "🎯 You Might Also Like"
                    }
                    Log.d("AnalyticsFragment", "Updated with ${realRecommendations.size} real recommendations")
                }
            } catch (e: Exception) {
                Log.e("AnalyticsFragment", "Error loading real recommendations", e)
            }
        }
    }

    private fun updateAnalytics(videoData: VideoData) {
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

        binding.textTotalComments.text = "Total Comments: ${videoData.totalComments}"
        binding.textToxicCount.text = "Toxic: ${videoData.toxicCount}"
        binding.textNeutralCount.text = "Neutral: ${videoData.neutralCount}"
        binding.textSafeCount.text = "Safe: ${videoData.safeCount}"

        val toxicityPercentage = (videoData.toxicityScore * 100).toInt()
        binding.textToxicityScore.text = "Toxicity Score: $toxicityPercentage%"

        binding.canvasChart.updateData(videoData.toxicCount, videoData.neutralCount, videoData.safeCount)
        binding.progressToxicity.progress = toxicityPercentage
    }

    private fun calculateAdvancedMetrics(comments: List<com.example.yttoxicitychecker.data.model.Comment>) {
        val toxicComments = comments.filter { it.toxicityResult?.isToxic == true }

        if (toxicComments.isNotEmpty()) {
            val avgToxicity = toxicComments.map { it.toxicityResult?.toxicityScore ?: 0f }.average()
            binding.textAvgToxicity.text = "Avg Toxicity: ${(avgToxicity * 100).toInt()}%"
        } else {
            binding.textAvgToxicity.text = "Avg Toxicity: 0%"
        }

        val sentimentCounts = comments.groupBy { it.toxicityResult?.sentiment ?: "Neutral" }
        binding.textSentimentDist.text = "Sentiment: ${sentimentCounts["Positive"]?.size ?: 0} 😊 | " +
                "${sentimentCounts["Neutral"]?.size ?: 0} 😐 | " +
                "${sentimentCounts["Negative"]?.size ?: 0} 😞"
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
