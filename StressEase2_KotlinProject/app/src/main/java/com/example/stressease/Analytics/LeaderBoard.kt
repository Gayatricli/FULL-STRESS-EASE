package com.example.stressease.Analytics

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.stressease.R
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LeaderBoard : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var adapter: EnhancedLeaderboardAdapter

    // Stats
    private lateinit var tvTotalUsers: TextView
    private lateinit var tvYourRank: TextView
    private lateinit var tvYourLogs: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.leaderboard)

        // Stats views
        tvTotalUsers = findViewById(R.id.tvTotalUsers)
        tvYourRank = findViewById(R.id.tvYourRank)
        tvYourLogs = findViewById(R.id.tvYourLogs)

        val recycler = findViewById<RecyclerView>(R.id.recyclerLeaderboard)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.setHasFixedSize(true)
        recycler.isNestedScrollingEnabled = false   // ✅ IMPORTANT

        adapter = EnhancedLeaderboardAdapter(
            items = emptyList(),
            currentUserId = auth.currentUser?.uid
        )
        recycler.adapter = adapter

        findViewById<MaterialButton>(R.id.btnNextAnalytics).setOnClickListener {
            startActivity(Intent(this, DataAnalyticsActivity::class.java))
        }

        observeLeaderboard()
    }

    private fun observeLeaderboard() {

        db.collection("leaderboard")
            .addSnapshotListener { snap, _ ->

                if (snap == null) return@addSnapshotListener

                val rawList = snap.documents.mapNotNull { doc ->

                    val id = doc.id
                    val username = doc.getString("username") ?: "User"

                    val avgQuizScore = doc.getDouble("avgQuizScore") ?: 0.0
                    val avgMoodScore = doc.getDouble("avgMoodScore") ?: 0.0
                    val chatCount = doc.getLong("chatCount")?.toInt() ?: 0

                    val composite =
                        0.65 * avgQuizScore +
                                0.35 * (avgMoodScore * 10)

                    LeaderboardEntry(
                        id = id,
                        username = username,
                        quizScore = (avgQuizScore * 10).toInt(),
                        emotionScore = avgMoodScore,
                        compositeScore = composite,
                        totalLogs = chatCount
                    )
                }

                val ranked = rawList
                    .sortedByDescending { it.compositeScore }
                    .mapIndexed { index, entry ->
                        entry.copy(rank = index + 1)
                    }

                adapter.updateList(ranked)
                updateStats(ranked)
            }
    }

    private fun updateStats(list: List<LeaderboardEntry>) {

        val userId = auth.currentUser?.uid ?: return

        tvTotalUsers.text = list.size.toString()

        val myEntry = list.find { it.id == userId }

        if (myEntry != null) {
            tvYourRank.text = "#${myEntry.rank}"
            tvYourLogs.text = myEntry.totalLogs.toString()
        } else {
            tvYourRank.text = "--"
            tvYourLogs.text = "0"
        }
    }
}
