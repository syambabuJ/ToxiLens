package com.example.yttoxicitychecker.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.yttoxicitychecker.data.model.Comment
import com.example.yttoxicitychecker.databinding.ItemCommentBinding

class CommentAdapter(
    private val onItemClick: (Comment) -> Unit
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    private var comments = listOf<Comment>()

    fun submitList(list: List<Comment>) {
        comments = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CommentViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(comments[position])
    }

    override fun getItemCount() = comments.size

    class CommentViewHolder(
        private val binding: ItemCommentBinding,
        private val onItemClick: (Comment) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: Comment) {
            // Set basic info
            binding.textAuthor.text = comment.author
            binding.textComment.text = comment.text
            binding.textLikes.text = "❤️ ${comment.likeCount}"

            // Set toxicity badge based on result
            comment.toxicityResult?.let { result ->
                // Set badge text with icon
                binding.textToxicityBadge.text = result.getBadgeText()

                // Set badge background color based on toxicity
                val badgeColor = result.getColor()
                binding.textToxicityBadge.setBackgroundColor(badgeColor)

                // Update card background based on toxicity level
                val cardColor = when {
                    result.isToxic && result.category == "Hate Speech" ->
                        android.graphics.Color.parseColor("#30DC2626")
                    result.isToxic ->
                        android.graphics.Color.parseColor("#20EF4444")
                    result.toxicityScore > 0.5f ->
                        android.graphics.Color.parseColor("#20F59E0B")
                    else ->
                        android.graphics.Color.WHITE
                }
                binding.cardView.setCardBackgroundColor(cardColor)

                // Show toxicity score
                val scorePercentage = result.getScorePercentage()
                binding.textToxicityScore.text = "$scorePercentage%"
                binding.textToxicityScore.visibility = android.view.View.VISIBLE

                // Show progress bar
                binding.progressToxicity.progress = scorePercentage
                binding.progressToxicity.visibility = android.view.View.VISIBLE
                binding.progressToxicity.progressTintList = android.content.res.ColorStateList.valueOf(badgeColor)

                // NEW: Show multi-label toxicity types
                val toxicityTypesText = result.getToxicityTypeNames()
                val toxicityIcons = result.getToxicityTypeIcons()

                if (result.toxicityTypes.isNotEmpty()) {
                    binding.textToxicityTypes.text = "$toxicityIcons $toxicityTypesText"
                    binding.textToxicityTypes.visibility = android.view.View.VISIBLE

                    // Show primary type if available
                    if (result.primaryType.isNotEmpty()) {
                        binding.textPrimaryType.text = "⚠️ Primary: ${result.getPrimaryTypeName()}"
                        binding.textPrimaryType.visibility = android.view.View.VISIBLE
                    }
                } else {
                    binding.textToxicityTypes.visibility = android.view.View.GONE
                    binding.textPrimaryType.visibility = android.view.View.GONE
                }

                // Set click listener with reasoning
                itemView.setOnClickListener {
                    onItemClick(comment)
                    android.widget.Toast.makeText(
                        itemView.context,
                        "${result.reasoning}\n\nToxicity Types: ${result.getToxicityTypeNames()}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }

            } ?: run {
                // No analysis yet
                binding.textToxicityBadge.text = "🟡 Analyzing..."
                binding.textToxicityBadge.setBackgroundColor(android.graphics.Color.parseColor("#9CA3AF"))
                binding.textToxicityScore.visibility = android.view.View.GONE
                binding.progressToxicity.visibility = android.view.View.GONE
                binding.textToxicityTypes.visibility = android.view.View.GONE
                binding.textPrimaryType.visibility = android.view.View.GONE
                itemView.setOnClickListener { onItemClick(comment) }
            }
        }
    }
}