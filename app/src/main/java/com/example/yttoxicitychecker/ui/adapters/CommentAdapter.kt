package com.toxilens.yttoxicitychecker.ui.adapters

import android.widget.Filter
import android.widget.Filterable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.toxilens.yttoxicitychecker.data.model.Comment
import com.toxilens.yttoxicitychecker.databinding.ItemCommentBinding

class CommentAdapter(
    private val onItemClick: (Comment) -> Unit
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>(), Filterable {

    private var allComments = listOf<Comment>()
    private var filteredComments = listOf<Comment>()

    fun submitList(list: List<Comment>) {
        allComments = list
        filteredComments = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CommentViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(filteredComments[position])
    }

    override fun getItemCount() = filteredComments.size

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filterType = constraint?.toString() ?: "all"

                val filtered = when (filterType) {
                    "toxic" -> allComments.filter { it.toxicityResult?.isToxic == true }
                    "neutral" -> allComments.filter {
                        it.toxicityResult?.toxicityScore?.let { score -> score > 0.3f && score <= 0.6f } == true
                    }
                    "safe" -> allComments.filter {
                        it.toxicityResult?.isToxic == false &&
                                (it.toxicityResult?.toxicityScore ?: 0f) <= 0.3f
                    }
                    else -> allComments
                }

                return FilterResults().apply {
                    values = filtered
                    count = filtered.size
                }
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredComments = results?.values as? List<Comment> ?: allComments
                notifyDataSetChanged()
            }
        }
    }

    fun getStats(): Triple<Int, Int, Int> {
        val toxic = allComments.count { it.toxicityResult?.isToxic == true }
        val neutral = allComments.count {
            it.toxicityResult?.toxicityScore?.let { score -> score > 0.3f && score <= 0.6f } == true
        }
        val safe = allComments.count {
            it.toxicityResult?.isToxic == false &&
                    (it.toxicityResult?.toxicityScore ?: 0f) <= 0.3f
        }
        return Triple(toxic, neutral, safe)
    }

    class CommentViewHolder(
        private val binding: ItemCommentBinding,
        private val onItemClick: (Comment) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: Comment) {
            binding.textAuthor.text = comment.author
            binding.textComment.text = comment.text
            binding.textLikes.text = "❤️ ${comment.likeCount}"

            comment.toxicityResult?.let { result ->
                val badgeText = "${result.getIcon()} ${result.category}"
                binding.textToxicityBadge.text = badgeText

                val badgeColor = when {
                    result.isToxic -> android.graphics.Color.parseColor("#EF4444")
                    result.toxicityScore > 0.5f -> android.graphics.Color.parseColor("#F59E0B")
                    else -> android.graphics.Color.parseColor("#10B981")
                }
                binding.textToxicityBadge.setBackgroundColor(badgeColor)

                val cardColor = when {
                    result.isToxic -> android.graphics.Color.parseColor("#20EF4444")
                    result.toxicityScore > 0.5f -> android.graphics.Color.parseColor("#20F59E0B")
                    else -> android.graphics.Color.BLUE
                }
                binding.cardView.setCardBackgroundColor(cardColor)

                val scorePercentage = (result.toxicityScore * 100).toInt()
                binding.textToxicityScore.text = "$scorePercentage%"
                binding.textToxicityScore.visibility = android.view.View.VISIBLE

                binding.progressToxicity.progress = scorePercentage
                binding.progressToxicity.visibility = android.view.View.VISIBLE
                binding.progressToxicity.progressTintList = android.content.res.ColorStateList.valueOf(badgeColor)

                if (result.toxicityTypes.isNotEmpty()) {
                    val typesText = result.getToxicityTypeIcons()
                    binding.textToxicityTypes.text = typesText
                    binding.textToxicityTypes.visibility = android.view.View.VISIBLE
                }

                itemView.setOnClickListener { onItemClick(comment) }
            }
        }
    }
}
