package com.example.stressease.Analytics

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.stressease.R
import kotlin.math.roundToInt

class LeaderboardAdapter(
    private val items: List<LeaderboardEntry>
) : RecyclerView.Adapter<LeaderboardAdapter.LBViewHolder>() {

    class LBViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRank: TextView = view.findViewById(R.id.tvRank)
        val tvUsername: TextView = view.findViewById(R.id.tvUsername)
        val tvMoodEmoji: TextView = view.findViewById(R.id.tvMoodEmoji)
        val tvScore: TextView = view.findViewById(R.id.tvScore)
        val tvMoodCount: TextView = view.findViewById(R.id.tvMoodCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LBViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_leaderboard_enhanced, parent, false)
        return LBViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: LBViewHolder, position: Int) {
        val item = items[position]

        // Rank
        holder.tvRank.text = "#${item.rank}"

        // Username
        holder.tvUsername.text = item.username

        // Dominant emotion emoji
        holder.tvMoodEmoji.text = item.emoji

        // Composite score (main leaderboard score)
        holder.tvScore.text = item.compositeScore.roundToInt().toString()

        // Activity indicator
        holder.tvMoodCount.text = "${item.totalLogs} mood logs"
    }
}
