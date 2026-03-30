package com.example.yttoxicitychecker.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.yttoxicitychecker.databinding.FragmentHomeBinding
import com.example.yttoxicitychecker.ui.adapters.CommentAdapter
import com.example.yttoxicitychecker.ui.viewmodel.MainViewModel
import com.example.yttoxicitychecker.utils.KeyboardUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Shared ViewModel - survives navigation, NO DATA LOSS
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var commentAdapter: CommentAdapter
    private var currentVideoId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        // Restore existing data if any - PREVENTS DISAPPEARING COMMENTS
        viewModel.comments.value?.let { comments ->
            if (comments.isNotEmpty()) {
                commentAdapter.submitList(comments)
                binding.recyclerViewComments.scrollToPosition(0)
            }
        }
    }

    private fun setupRecyclerView() {
        commentAdapter = CommentAdapter { comment ->
            Toast.makeText(requireContext(), "Comment by: ${comment.author}", Toast.LENGTH_SHORT).show()
        }
        binding.recyclerViewComments.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = commentAdapter
            setHasFixedSize(true)  // Performance optimization
        }
    }

    private fun setupListeners() {
        // Handle keyboard - dismiss on action
        binding.editTextUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                analyzeUrl()
                true
            } else false
        }

        binding.buttonAnalyze.setOnClickListener {
            analyzeUrl()
        }

        binding.swipeRefresh.setOnRefreshListener {
            if (currentVideoId.isNotEmpty()) {
                // Refresh WITHOUT clearing existing UI
                viewModel.refreshCurrentVideo()
            } else {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun analyzeUrl() {
        val url = binding.editTextUrl.text.toString().trim()
        if (url.isNotEmpty()) {
            // Dismiss keyboard
            KeyboardUtils.hideKeyboard(requireActivity(), binding.editTextUrl)

            val videoId = extractVideoId(url)
            if (videoId != null) {
                currentVideoId = videoId
                // Show loading but DON'T clear existing comments
                binding.progressBar.visibility = View.VISIBLE
                viewModel.fetchAndAnalyzeComments(videoId)
            } else {
                Toast.makeText(requireContext(), "Invalid YouTube URL", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "Please enter a YouTube URL", Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractVideoId(url: String): String? {
        val patterns = listOf(
            "v=([a-zA-Z0-9_-]{11})",
            "youtu.be/([a-zA-Z0-9_-]{11})",
            "embed/([a-zA-Z0-9_-]{11})",
            "shorts/([a-zA-Z0-9_-]{11})"
        )

        for (pattern in patterns) {
            val regex = Regex(pattern)
            val matchResult = regex.find(url)
            if (matchResult != null) {
                return matchResult.groupValues[1]
            }
        }
        return null
    }

    private fun observeViewModel() {
        // Comments observer - NEVER clears unless new data arrives
        viewModel.comments.observe(viewLifecycleOwner) { comments ->
            commentAdapter.submitList(comments)
            binding.progressBar.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false

            // Handle empty state
            if (comments.isEmpty() && !viewModel.isLoading.value!!) {
                Toast.makeText(requireContext(), "No comments found", Toast.LENGTH_SHORT).show()
            } else if (comments.isNotEmpty()) {
                val toxicCount = comments.count { it.toxicityResult?.isToxic == true }
                Toast.makeText(
                    requireContext(),
                    "Analyzed ${comments.size} comments | 🔴 Toxic: $toxicCount",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Loading observer
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (!isLoading) {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        }

        // Error observer
        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        // Video title display
        viewModel.currentVideoData.observe(viewLifecycleOwner) { videoData ->
            videoData?.let {
                if (it.title.isNotEmpty()) {
                    binding.textVideoTitle.text = "🎬 ${it.title}"
                    binding.textVideoTitle.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}