package com.example.yttoxicitychecker.ui.history

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.yttoxicitychecker.databinding.FragmentHistoryBinding
import com.example.yttoxicitychecker.ui.adapters.HistoryAdapter
import com.example.yttoxicitychecker.ui.viewmodel.MainViewModel
import com.example.yttoxicitychecker.ui.webview.WebViewActivity

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()
        setupListeners()

        // Load history from Firebase
        viewModel.loadHistoryFromFirebase()
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter { videoData ->
            // Open video in WebView
            val intent = Intent(requireContext(), WebViewActivity::class.java).apply {
                putExtra("video_id", videoData.videoId)
                putExtra("video_url", videoData.videoUrl)
                putExtra("video_title", videoData.title)
            }
            startActivity(intent)

            // Load analysis data for this video
            viewModel.loadVideoFromFirebase(videoData.videoId)
            Toast.makeText(
                requireContext(),
                "Loading ${videoData.title.ifEmpty { "Video" }}",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.recyclerViewHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadHistoryFromFirebase()
        }
    }

    private fun observeViewModel() {
        // Real-time history updates from Firebase
        viewModel.historyList.observe(viewLifecycleOwner) { historyList ->
            historyAdapter.submitList(historyList)
            binding.swipeRefresh.isRefreshing = false

            if (historyList.isEmpty()) {
                binding.textEmpty.visibility = View.VISIBLE
                binding.recyclerViewHistory.visibility = View.GONE
            } else {
                binding.textEmpty.visibility = View.GONE
                binding.recyclerViewHistory.visibility = View.VISIBLE
            }
        }

        // Observe history loading state
        viewModel.isLoadingHistory.observe(viewLifecycleOwner) { isLoading ->
            if (!isLoading) {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}