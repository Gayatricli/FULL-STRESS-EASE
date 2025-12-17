package com.example.stressease.Analytics

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.stressease.R
import kotlin.math.roundToInt

class EnhancedLeaderboardAdapter(
    private val items: List<LeaderboardEntry>,
    private val currentUserId: String?,       // auth.currentUser?.uid
    private val wQuiz: Double = 0.65,
    private val wEmotion: Double = 0.35
) : RecyclerView.Adapter<EnhancedLeaderboardAdapter.LBViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LBViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_leaderboard_enhanced, parent, false)
        return LBViewHolder(view)
    }

    override fun onBindViewHolder(holder: LBViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class LBViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        // Rank / medal
        private val tvMedalEmoji: TextView? = itemView.findViewById(R.id.tvMedalEmoji)
        private val rankBadge: CardView? = itemView.findViewById(R.id.rankBadge)
        private val tvRankNumber: TextView? = itemView.findViewById(R.id.tvRankNumber)

        // Avatar
        private val avatarRing: View? = itemView.findViewById(R.id.avatarRing)
        private val ivUserAvatar: ImageView? = itemView.findViewById(R.id.ivUserAvatar)

        // User info
        private val tvUsername: TextView = itemView.findViewById(R.id.tvUsername)
        private val streakBadge: View? = itemView.findViewById(R.id.streakBadge)
        private val tvStreakDays: TextView? = itemView.findViewById(R.id.tvStreakDays)

        // Scores
        private val tvMoodScore: TextView = itemView.findViewById(R.id.tvMoodScore)
        private val tvLogsCount: TextView = itemView.findViewById(R.id.tvLogsCount)
        private val tvScoreValue: TextView = itemView.findViewById(R.id.tvScoreValue)

        // Current user highlight
        private val tvYouBadge: TextView? = itemView.findViewById(R.id.tvYouBadge)
        private val highlightBorder: View? = itemView.findViewById(R.id.highlightBorder)

        fun bind(entry: LeaderboardEntry) {

            // 🏆 Rank logic
            if (entry.rank in 1..3) {
                tvMedalEmoji?.visibility = View.VISIBLE
                rankBadge?.visibility = View.GONE
                avatarRing?.visibility = View.VISIBLE

                tvMedalEmoji?.text = when (entry.rank) {
                    1 -> "🥇"
                    2 -> "🥈"
                    else -> "🥉"
                }
            } else {
                tvMedalEmoji?.visibility = View.GONE
                rankBadge?.visibility = View.VISIBLE
                avatarRing?.visibility = View.GONE
                tvRankNumber?.text = "#${entry.rank}"
            }

            // 👤 Username & streak
            tvUsername.text = entry.username
            if (entry.streakDays > 0) {
                streakBadge?.visibility = View.VISIBLE
                tvStreakDays?.text = entry.streakDays.toString()
            } else {
                streakBadge?.visibility = View.GONE
            }

            // 🎯 Composite score
            val pts = if (entry.compositeScore > 0.0) {
                (entry.compositeScore * 1000).roundToInt()
            } else {
                entry.quizScore * 10
            }

            tvMoodScore.text = "$pts pts"
            tvScoreValue.text = pts.toString()
            tvLogsCount.text = "${entry.totalLogs} logs"

            // ⭐ Current user highlight
            val isMe = currentUserId != null && currentUserId == entry.id
            tvYouBadge?.visibility = if (isMe) View.VISIBLE else View.GONE
            highlightBorder?.visibility = if (isMe) View.VISIBLE else View.GONE

            // 🖼 Avatar (fallback-safe)
            ivUserAvatar?.setImageResource(android.R.drawable.sym_def_app_icon)

            // ℹ Click breakdown
            itemView.setOnClickListener {
                val msg = """
                    ${entry.username}
                    Composite: ${"%.2f".format(entry.compositeScore)}
                    Quiz: ${entry.quizScore}
                    Logs: ${entry.totalLogs}
                    Emotion: ${if (entry.shareWellbeing) "%.2f".format(entry.emotionScore) else "Hidden"}
                """.trimIndent()

                Toast.makeText(itemView.context, msg, Toast.LENGTH_SHORT).show()
            }
        }


    }
    fun updateList(newItems: List<LeaderboardEntry>) {
        (items as? MutableList)?.apply {
            clear()
            addAll(newItems)
            notifyDataSetChanged()
        }
    }
}
