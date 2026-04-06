package com.toxilens.yttoxicitychecker.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.toxilens.yttoxicitychecker.data.model.VideoData
import com.toxilens.yttoxicitychecker.databinding.ItemRecommendationBinding

class RecommendationAdapter(
    private val onItemClick: (VideoData) -> Unit
) : RecyclerView.Adapter<RecommendationAdapter.RecommendationViewHolder>() {

    private var recommendations = listOf<VideoData>()

    fun submitList(list: List<VideoData>) {
        recommendations = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecommendationViewHolder {
        val binding = ItemRecommendationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RecommendationViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: RecommendationViewHolder, position: Int) {
        holder.bind(recommendations[position])
    }

    override fun getItemCount() = recommendations.size

    class RecommendationViewHolder(
        private val binding: ItemRecommendationBinding,
        private val onItemClick: (VideoData) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(videoData: VideoData) {
            binding.textTitle.text = videoData.title.take(40)
            binding.textChannel.text = videoData.channelTitle.take(25)

            val toxicityPercent = (videoData.toxicityScore * 100).toInt()
            binding.textToxicity.text = "🔴 ${toxicityPercent}% toxic"
            binding.textReason.text = videoData.recommendationReason

            // Set colors based on toxicity
            val toxicityColor = when {
                videoData.toxicityScore > 0.7 -> 0xFFEF4444.toInt()
                videoData.toxicityScore > 0.4 -> 0xFFF59E0B.toInt()
                else -> 0xFF10B981.toInt()
            }
            binding.textToxicity.setTextColor(toxicityColor)

            itemView.setOnClickListener { onItemClick(videoData) }
        }
    }
}
