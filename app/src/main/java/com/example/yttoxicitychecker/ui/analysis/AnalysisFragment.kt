package com.toxilens.yttoxicitychecker.ui.analysis

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.toxilens.yttoxicitychecker.databinding.FragmentAnalysisBinding
import com.toxilens.yttoxicitychecker.ui.adapters.CommentAdapter
import com.toxilens.yttoxicitychecker.ui.viewmodel.MainViewModel

class AnalysisFragment : Fragment() {

    private var _binding: FragmentAnalysisBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var commentAdapter: CommentAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalysisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFilterButtons()
        setupListeners()
        observeViewModel()
        restoreExistingData()
    }

    private fun setupRecyclerView() {
        commentAdapter = CommentAdapter { comment ->
            Toast.makeText(requireContext(),
                comment.toxicityResult?.reasoning ?: "No analysis",
                Toast.LENGTH_SHORT).show()
        }
        binding.recyclerViewComments.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = commentAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupFilterButtons() {
        binding.buttonFilterAll.setOnClickListener {
            commentAdapter.filter.filter("all")
            updateFilterInfo("Showing all comments")
            updateButtonStates("all")
        }

        binding.buttonFilterToxic.setOnClickListener {
            commentAdapter.filter.filter("toxic")
            updateFilterInfo("🔴 Showing toxic comments only")
            updateButtonStates("toxic")
        }

        binding.buttonFilterNeutral.setOnClickListener {
            commentAdapter.filter.filter("neutral")
            updateFilterInfo("🟡 Showing neutral comments only")
            updateButtonStates("neutral")
        }

        binding.buttonFilterSafe.setOnClickListener {
            commentAdapter.filter.filter("safe")
            updateFilterInfo("🟢 Showing safe comments only")
            updateButtonStates("safe")
        }
    }

    private fun updateFilterInfo(message: String) {
        binding.textFilterInfo.text = message
    }

    private fun updateButtonStates(activeFilter: String) {
        val defaultAlpha = 0.6f
        val activeAlpha = 1.0f

        binding.buttonFilterAll.alpha = if (activeFilter == "all") activeAlpha else defaultAlpha
        binding.buttonFilterToxic.alpha = if (activeFilter == "toxic") activeAlpha else defaultAlpha
        binding.buttonFilterNeutral.alpha = if (activeFilter == "neutral") activeAlpha else defaultAlpha
        binding.buttonFilterSafe.alpha = if (activeFilter == "safe") activeAlpha else defaultAlpha
    }

    private fun setupListeners() {
        binding.buttonRefresh.setOnClickListener {
            viewModel.currentVideoId?.let { videoId ->
                binding.progressBar.visibility = View.VISIBLE
                viewModel.refreshCurrentVideo()
            } ?: run {
                Toast.makeText(requireContext(), "No video analyzed yet", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun restoreExistingData() {
        viewModel.comments.value?.let { comments ->
            if (comments.isNotEmpty()) {
                commentAdapter.submitList(comments)
                updateStats(comments)
            }
        }

        viewModel.currentVideoData.value?.let { videoData ->
            updateAnalytics(videoData)
        }
    }

    private fun observeViewModel() {
        viewModel.comments.observe(viewLifecycleOwner) { comments ->
            commentAdapter.submitList(comments)
            binding.progressBar.visibility = View.GONE

            if (comments.isNotEmpty()) {
                updateStats(comments)
            }
        }

        viewModel.currentVideoData.observe(viewLifecycleOwner) { videoData ->
            videoData?.let {
                updateAnalytics(it)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun updateStats(comments: List<com.toxilens.yttoxicitychecker.data.model.Comment>) {
        val toxicCount = comments.count { it.toxicityResult?.isToxic == true }
        val neutralCount = comments.count {
            it.toxicityResult?.toxicityScore?.let { score -> score > 0.3f && score <= 0.6f } == true
        }
        val safeCount = comments.count { it.toxicityResult?.isToxic == false &&
                (it.toxicityResult?.toxicityScore ?: 0f) <= 0.3f }

        binding.textToxicCount.text = "🔴 Toxic: $toxicCount"
        binding.textNeutralCount.text = "🟡 Neutral: $neutralCount"
        binding.textSafeCount.text = "🟢 Safe: $safeCount"

        val total = comments.size.toFloat()
        if (total > 0) {
            val toxicPercentage = (toxicCount / total * 100).toInt()
            binding.textToxicityScore.text = "Overall Toxicity: $toxicPercentage%"
        }
    }

    private fun updateAnalytics(videoData: com.toxilens.yttoxicitychecker.data.model.VideoData) {
        binding.canvasChart.updateData(videoData.toxicCount, videoData.neutralCount, videoData.safeCount)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
