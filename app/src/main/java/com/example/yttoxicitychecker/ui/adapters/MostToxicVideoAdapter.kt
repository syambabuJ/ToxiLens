package com.toxilens.yttoxicitychecker.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.toxilens.yttoxicitychecker.data.model.VideoSummary
import com.toxilens.yttoxicitychecker.databinding.ItemMostToxicVideoBinding

class MostToxicVideoAdapter(
    private val onItemClick: (VideoSummary) -> Unit
) : RecyclerView.Adapter<MostToxicVideoAdapter.MostToxicViewHolder>() {

    private var videos = listOf<VideoSummary>()

    fun submitList(list: List<VideoSummary>) {
        videos = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MostToxicViewHolder {
        val binding = ItemMostToxicVideoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MostToxicViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: MostToxicViewHolder, position: Int) {
        holder.bind(videos[position])
    }

    override fun getItemCount() = videos.size

    class MostToxicViewHolder(
        private val binding: ItemMostToxicVideoBinding,
        private val onItemClick: (VideoSummary) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(video: VideoSummary) {
            binding.textTitle.text = video.title
            binding.textToxicity.text = "🔴 ${video.getToxicityPercent()}% toxic"

            val toxicityColor = when {
                video.toxicityScore > 0.7 -> 0xFFEF4444.toInt()
                video.toxicityScore > 0.4 -> 0xFFF59E0B.toInt()
                else -> 0xFF10B981.toInt()
            }
            binding.textToxicity.setTextColor(toxicityColor)

            itemView.setOnClickListener { onItemClick(video) }
        }
    }
}
