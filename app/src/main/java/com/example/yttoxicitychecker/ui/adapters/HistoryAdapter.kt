package com.example.yttoxicitychecker.ui.adapters

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.yttoxicitychecker.data.model.VideoData
import com.example.yttoxicitychecker.databinding.ItemHistoryBinding
import java.util.*

class HistoryAdapter(
    private val onItemClick: (VideoData) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private var historyList = listOf<VideoData>()

    fun submitList(list: List<VideoData>) {
        historyList = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoryViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(historyList[position])
    }

    override fun getItemCount() = historyList.size

    class HistoryViewHolder(
        private val binding: ItemHistoryBinding,
        private val onItemClick: (VideoData) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(videoData: VideoData) {
            // Show video title (actual YouTube title)
            binding.textTitle.text = videoData.title.ifEmpty { "YouTube Video" }

            // Show channel name
            binding.textChannel.text = videoData.channelTitle.ifEmpty { "Unknown Channel" }

            // Show stats
            binding.textStats.text = "📊 ${videoData.totalComments} comments | " +
                    "🔴 ${videoData.toxicCount} toxic | " +
                    "🟢 ${videoData.safeCount} safe"

            // Show date
            val date = Date(videoData.timestamp)
            binding.textDate.text = DateFormat.format("MMM dd, yyyy HH:mm", date)

            // Show toxicity score with color
            val toxicityPercent = (videoData.toxicityScore * 100).toInt()
            binding.textToxicityScore.text = "Toxicity: $toxicityPercent%"

            // Color based on toxicity level
            when {
                toxicityPercent > 60 -> binding.textToxicityScore.setTextColor(0xFFEF4444.toInt())
                toxicityPercent > 30 -> binding.textToxicityScore.setTextColor(0xFFF59E0B.toInt())
                else -> binding.textToxicityScore.setTextColor(0xFF10B981.toInt())
            }

            itemView.setOnClickListener { onItemClick(videoData) }
        }
    }
}